#!/usr/bin/env bash
# =============================================================================
#  PARLA FACILE — installazione automatica sul Nexus 7 2013 (LineageOS)
#
#  Uso (dalla cartella del progetto, tablet collegato via USB con debug attivo):
#      ./setup.sh                  tutto tranne la modalità chiosco
#      ./setup.sh --chiosco        anche modalità chiosco (serve: nessun account sul tablet)
#      ./setup.sh --solo-verifica  mostra lo stato del tablet e non cambia nulla
#
#  Funziona su Linux e macOS. Scarica da solo quello che manca
#  (strumenti Android, modello vocale italiano).
# =============================================================================
set -euo pipefail

CARTELLA="$(cd "$(dirname "$0")" && pwd)"
STRUMENTI="$CARTELLA/.strumenti"
PKG="it.parlafacile"
MODELLO_URL="https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip"
CMDTOOLS_VER="11076708"

CHIOSCO=0; SOLO_VERIFICA=0
for a in "$@"; do
  case "$a" in
    --chiosco) CHIOSCO=1 ;;
    --solo-verifica) SOLO_VERIFICA=1 ;;
    *) echo "Opzione sconosciuta: $a"; exit 1 ;;
  esac
done

passo() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
ok()    { printf '    \033[32m✓\033[0m %s\n' "$*"; }
nota()  { printf '    \033[33m!\033[0m %s\n' "$*"; }
errore(){ printf '\n\033[1;31mERRORE:\033[0m %s\n' "$*"; exit 1; }

case "$(uname -s)" in
  Linux)  SO=linux ;;
  Darwin) SO=mac ;;
  *) errore "Sistema non supportato: usare Linux, macOS o WSL." ;;
esac
mkdir -p "$STRUMENTI"

# -----------------------------------------------------------------------------
passo "1. Strumenti sul computer (adb, Java, SDK Android)"
# -----------------------------------------------------------------------------
if ! command -v adb >/dev/null 2>&1; then
  if [ ! -x "$STRUMENTI/platform-tools/adb" ]; then
    nota "adb non trovato: lo scarico"
    curl -fL -o "$STRUMENTI/pt.zip" \
      "https://dl.google.com/android/repository/platform-tools-latest-$( [ $SO = mac ] && echo darwin || echo linux ).zip"
    unzip -qo "$STRUMENTI/pt.zip" -d "$STRUMENTI" && rm "$STRUMENTI/pt.zip"
  fi
  export PATH="$STRUMENTI/platform-tools:$PATH"
fi
ok "adb: $(adb version | head -1)"

# -----------------------------------------------------------------------------
passo "2. Tablet"
# -----------------------------------------------------------------------------
adb start-server >/dev/null 2>&1 || true
STATO=$(adb get-state 2>/dev/null || true)
if [ "$STATO" != "device" ]; then
  adb devices
  errore "Tablet non visto. Controllare: cavo USB dati, 'Debug USB' attivo, e sul tablet
       premere 'Consenti' alla richiesta di autorizzazione del computer."
fi
tab() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
MODELLO_DEV=$(tab getprop ro.product.device)
ANDROID=$(tab getprop ro.build.version.release)
SDK=$(tab getprop ro.build.version.sdk)
LINEAGE=$(tab getprop ro.lineage.version)
ABI=$(tab getprop ro.product.cpu.abi)
ok "Dispositivo: $MODELLO_DEV  |  Android $ANDROID (API $SDK)  |  LineageOS ${LINEAGE:-?}  |  $ABI"
case "$MODELLO_DEV" in flo|flox|deb) ;; *) nota "Non sembra un Nexus 7 2013: procedo comunque." ;; esac
[ "${SDK:-0}" -ge 21 ] || errore "Serve almeno Android 5 (API 21)."

SPAZIO=$(tab df /data | awk 'NR==2{print $4}')
ok "Spazio libero in /data: ${SPAZIO:-?}"

ACCOUNT=$(tab dumpsys account | grep -c "Account {" || true)
ok "Account configurati sul tablet: $ACCOUNT"

# Root via adb (LineageOS: Opzioni sviluppatore → "Debug con root")
ROOT=0
if [ "$(tab id -u)" = "0" ]; then ROOT=1
else
  adb root >/dev/null 2>&1 || true
  sleep 3; adb wait-for-device
  [ "$(tab id -u)" = "0" ] && ROOT=1
fi
if [ $ROOT = 1 ]; then ok "Root via adb: ATTIVO"
else nota "Root via adb non attivo (Opzioni sviluppatore → 'Debug con root'). Non indispensabile."
fi
if tab "command -v su" | grep -q su; then ok "Root completo (su/Magisk): presente"
else nota "Root completo (Magisk) assente — vedi PROGETTO.md, fase 'Root con Magisk'."
fi

GOOGLE_STT=$(tab pm list packages com.google.android.googlequicksearchbox | grep -c google || true)
ok "Riconoscimento Google (online): $([ "$GOOGLE_STT" -gt 0 ] && echo sì || echo no)"

if [ $SOLO_VERIFICA = 1 ]; then echo; ok "Solo verifica: nessuna modifica fatta."; exit 0; fi

# -----------------------------------------------------------------------------
passo "3. Modello vocale italiano (per funzionare senza internet)"
# -----------------------------------------------------------------------------
ASSET="$CARTELLA/app/src/main/assets/model-it"
if [ ! -f "$ASSET/uuid" ]; then
  curl -fL -o "$STRUMENTI/modello.zip" "$MODELLO_URL"
  rm -rf "$STRUMENTI/modello" && mkdir -p "$STRUMENTI/modello"
  unzip -qo "$STRUMENTI/modello.zip" -d "$STRUMENTI/modello"
  rm -rf "$ASSET" && mkdir -p "$(dirname "$ASSET")"
  mv "$STRUMENTI"/modello/vosk-model-* "$ASSET"
  # Vosk usa questo file per sapere se il modello va ri-estratto
  (command -v uuidgen >/dev/null && uuidgen || python3 -c 'import uuid;print(uuid.uuid4())') > "$ASSET/uuid"
  rm -rf "$STRUMENTI/modello" "$STRUMENTI/modello.zip"
fi
ok "Modello: $(du -sh "$ASSET" | cut -f1)"

# -----------------------------------------------------------------------------
passo "4. Compilazione dell'app"
# -----------------------------------------------------------------------------
command -v java >/dev/null || errore "Serve Java 17 (es. 'sudo apt install openjdk-17-jdk')."
JV=$(java -version 2>&1 | awk -F'"' '/version/{split($2,v,".");print v[1]}')
[ "${JV:-0}" -ge 17 ] || errore "Serve Java 17 o superiore (trovato: $JV)."

SDKDIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -z "$SDKDIR" ] && [ -d "$HOME/Android/Sdk" ] && SDKDIR="$HOME/Android/Sdk"
[ -z "$SDKDIR" ] && [ -d "$HOME/Library/Android/sdk" ] && SDKDIR="$HOME/Library/Android/sdk"
if [ -z "$SDKDIR" ]; then
  SDKDIR="$STRUMENTI/sdk"
  if [ ! -x "$SDKDIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    nota "SDK Android non trovato: scarico la versione minima"
    curl -fL -o "$STRUMENTI/ct.zip" \
      "https://dl.google.com/android/repository/commandlinetools-$SO-${CMDTOOLS_VER}_latest.zip"
    mkdir -p "$SDKDIR/cmdline-tools" && unzip -qo "$STRUMENTI/ct.zip" -d "$SDKDIR/cmdline-tools"
    mv "$SDKDIR/cmdline-tools/cmdline-tools" "$SDKDIR/cmdline-tools/latest" && rm "$STRUMENTI/ct.zip"
  fi
  yes | "$SDKDIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDKDIR" --licenses >/dev/null || true
  "$SDKDIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDKDIR" \
      "platforms;android-34" "build-tools;34.0.0" >/dev/null
fi
echo "sdk.dir=$SDKDIR" > "$CARTELLA/local.properties"
ok "SDK: $SDKDIR"

( cd "$CARTELLA" && ./gradlew --no-daemon -q assembleRelease )
APK="$CARTELLA/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || errore "Compilazione non riuscita."
ok "APK: $(du -h "$APK" | cut -f1)"

# -----------------------------------------------------------------------------
passo "5. Installazione di Parla Facile"
# -----------------------------------------------------------------------------
adb install -r -g "$APK" >/dev/null
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
ok "App installata con permesso del microfono"

ADMIN="$PKG/.AdminReceiver"
HOME_ACT="$PKG/.MainActivity"
if adb shell cmd package set-home-activity "$HOME_ACT" 2>&1 | grep -qi "success"; then
  ok "Impostata come schermata Home: parte da sola all'accensione"
else
  nota "Impostare la Home a mano: premere il tasto Home sul tablet → 'Parla Facile' → 'Sempre'."
fi

# -----------------------------------------------------------------------------
passo "6. Impostazioni del tablet"
# -----------------------------------------------------------------------------
adb shell settings put global stay_on_while_plugged_in 3          # schermo acceso in carica
adb shell settings put system screen_off_timeout 600000           # 10 minuti a batteria
adb shell settings put global heads_up_notifications_enabled 0     # niente notifiche a comparsa
adb shell settings put system accelerometer_rotation 0            # niente rotazioni accidentali
adb shell locksettings set-disabled true >/dev/null 2>&1 || nota "Blocco schermo: se c'è un PIN va tolto a mano"
adb shell cmd notification set_dnd priority >/dev/null 2>&1 || true
if [ $ROOT = 1 ]; then
  # Niente notifiche di aggiornamento di sistema a disturbare
  adb shell pm disable-user --user 0 org.lineageos.updater >/dev/null 2>&1 && ok "Avvisi aggiornamenti LineageOS disattivati" || true
fi
ok "Schermo, notifiche e rotazione configurati"

# -----------------------------------------------------------------------------
passo "7. Modalità chiosco"
# -----------------------------------------------------------------------------
if [ $CHIOSCO = 1 ]; then
  if [ "$ACCOUNT" -gt 0 ]; then
    nota "Ci sono $ACCOUNT account sul tablet: Android non permette la modalità chiosco."
    nota "Rimuoverli (Impostazioni → Account) e rilanciare ./setup.sh --chiosco"
  else
    adb shell dpm set-device-owner "$ADMIN" && ok "Modalità chiosco ATTIVA (uscita: 5 tocchi sulla batteria)"
  fi
else
  nota "Chiosco non attivato (usare --chiosco). L'app è comunque la Home e il tasto Indietro non esce."
fi

# -----------------------------------------------------------------------------
passo "8. Riavvio"
# -----------------------------------------------------------------------------
adb reboot
ok "Fatto. Al riavvio il tablet mostrerà direttamente Parla Facile."
