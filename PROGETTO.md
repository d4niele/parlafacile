# Parla Facile — sottotitoli dal vivo per chi ci sente poco

Tablet: **Google Nexus 7 2013** (flo / flox) con **LineageOS**.
Scopo: trasformare il tablet in un apparecchio con **una sola funzione** — scrivere a caratteri enormi quello che le dicono — per una persona anziana ipoudente.

---

## 1. Cosa fa

| Funzione | Come si usa |
|---|---|
| **Sottotitoli dal vivo** | Si preme il **tastone verde** (o un tasto del volume): chi parla viene scritto a caratteri enormi. Le ultime due frasi restano grandi e nere, le precedenti diventano piccole e grigie. |
| **Tastone ON/OFF** | Metà bassa dello schermo. Verde = spento, rosso = sta ascoltando. Anche **volume su/giù** e un eventuale **pulsante Bluetooth** (cuffie, telecomandino) fanno da tastone. |
| **Comandi vocali** | Detti da soli, mentre ascolta: **"cancella"**, **"più grande"**, **"più piccolo"**. |
| **Batteria** | Barra in alto sempre visibile: verde, arancio sotto il 35%, rossa con "METTERE IN CARICA!" sotto il 15%. |
| **Sempre pronto** | È la schermata Home: parte da sola all'accensione. In carica lo schermo resta acceso. |
| **Risparmio** | Dopo 10 minuti di silenzio l'ascolto si spegne da solo (solo a batteria). |
| **Menu tecnico** | **5 tocchi rapidi sulla barra della batteria**: modo di ascolto, impostazioni, uscita dal chiosco. |

## 2. Locale e in rete

L'app ha tre motori di riconoscimento e sceglie da sola:

- **Senza internet — Vosk**, modello italiano piccolo (~50 MB) incluso nell'app. Funziona sempre, anche in cantina o senza Wi‑Fi. Meno preciso.
- **Con internet — riconoscimento di sistema** (tipicamente Google), più preciso. Disponibile solo se sul tablet c'è un servizio di riconoscimento vocale (su LineageOS senza Google di solito non c'è).

- **Server Whisper di casa** (`server/whisper_server.py`, `MotoreWhisper.java`): il tablet manda ogni frase a un PC nella stessa Wi‑Fi. Il più preciso, senza Google. Sul PC: `pip install faster-whisper && python3 server/whisper_server.py --modello small`; poi nel pannello di configurazione si scrive `IP-del-PC:8000`. Non dà testo provvisorio: mentre elabora si vede "…".

Modo **Automatico** (predefinito): usa Whisper se configurato e la rete c'è, poi il riconoscimento di sistema, poi Vosk; se la rete cade durante l'ascolto passa al locale senza fermarsi. Dal pannello di configurazione (5 tocchi sulla barra della batteria) si vede il motore in uso, si sceglie il modo, si imposta e si prova l'indirizzo del server Whisper.

## 3. Sistema operativo: scelta

| Sistema | Stato sul Nexus 7 2013 | Giudizio |
|---|---|---|
| **LineageOS** (flox, 18.1 = Android 11) | Hardware tutto funzionante, audio e microfono compresi | **Scelto** |
| Ubuntu Touch | Non supportato dalla versione attuale, solo la vecchia 16.04 senza aggiornamenti | Scartato |
| postmarketOS | Port sperimentale; l'audio è storicamente il punto debole | Scartato |

## 4. Root

- **Root via adb** (Opzioni sviluppatore → *Debug con root*): basta per tutta la configurazione. Lo script lo attiva se disponibile.
- **Root completo con Magisk** (app con permessi di superutente): non serve a Parla Facile, ma richiesto per l'impianto. Si fa **a mano, con backup**, perché modifica l'immagine di avvio — vedi §7.

## 5. Architettura

> Descrizione completa e aggiornata (motori, server, pannello, misure, sicurezza): vedi **ARCHITETTURA.md**. L'albero qui sotto non elenca `MotoreWhisper.java`, `Correttore.java` e la cartella `server/`.

```
app/src/main/java/it/parlafacile/
├── MainActivity.java    schermata unica, tastone, sottotitoli, batteria, chiosco, menu tecnico
├── MotoreAscolto.java   interfaccia comune dei motori di riconoscimento
├── MotoreVosk.java      riconoscimento offline (Vosk)
├── MotoreRete.java      riconoscimento di sistema (online), riavviato di continuo
└── AdminReceiver.java   serve solo alla modalità chiosco (device owner)
    (e le classi pure Segmentatore, Wav, Indirizzi, FiltroTesto: vedi ARCHITETTURA.md)
app/src/main/assets/model-it/   modello Vosk italiano (lo scarica setup.sh)
setup.sh                        installazione automatica via adb
```

- Nessuna libreria AndroidX: solo Android + Vosk. minSdk 21, targetSdk 30.
- L'app è registrata come **HOME**; in modalità chiosco (device owner) usa **Lock Task**: niente barra di stato, niente tasti Home/Recenti, nessuna uscita per sbaglio.
- Il tasto Indietro non esce mai dall'app.

## 6. Installazione

**Serve:** computer Linux o macOS, cavo USB dati, Java 17, connessione internet sul computer.

1. Sul tablet: *Impostazioni → Info tablet →* toccare 7 volte *Numero build*. Poi *Opzioni sviluppatore →* attivare **Debug USB** (e se si vuole **Debug con root**).
2. Collegare il tablet e accettare l'autorizzazione del computer.
3. Se si vuole il chiosco: togliere tutti gli account dal tablet (*Impostazioni → Account*).
4. Dalla cartella del progetto:

```bash
./setup.sh --solo-verifica   # guarda lo stato del tablet, non cambia niente
./setup.sh --chiosco         # installa e configura tutto, poi riavvia
```

Lo script: verifica il tablet → scarica modello vocale e strumenti Android se mancano → compila l'app → installa l'app con il permesso del microfono → la imposta come Home → configura schermo, notifiche, blocco schermo → attiva il chiosco → riavvia.

In alternativa si può aprire la cartella in **Android Studio** e fare *Build → Build APK* (prima scaricare il modello: basta eseguire lo script una volta).

## 7. Root con Magisk (fase guidata, facoltativa)

Da fare con calma e **dopo un backup**. Il bootloader è già sbloccato (c'è LineageOS).

1. Verificare la build esatta: `adb shell getprop ro.lineage.version`.
2. Procurarsi lo **stesso** zip di LineageOS installato ed estrarne `boot.img`.
3. Backup: `adb pull` della cartella foto/documenti se serve; tenere da parte il `boot.img` originale.
4. Installare l'app Magisk (dal GitHub ufficiale di Magisk; verificare che la versione supporti ancora ARM 32 bit e Android 11), copiare `boot.img` sul tablet, *Installa → Seleziona e patcha un file*.
5. `adb pull` del file patchato, poi: `adb reboot bootloader` → `fastboot flash boot magisk_patched.img` → `fastboot reboot`.
6. Se il tablet non si avvia: `fastboot flash boot boot.img` (l'originale) e si torna come prima.

## 8. Uso quotidiano (da lasciare alla famiglia)

- **Per parlarle:** premere il tasto verde, parlare vicino al tablet, con calma. Lei legge.
- **Scritte troppo piccole:** A+ oppure dire "più grande".
- **Batteria rossa:** mettere in carica. In carica il tablet resta sempre acceso e pronto.

## 9. Problemi comuni

| Problema | Soluzione |
|---|---|
| Il tastone resta grigio "PREPARAZIONE…" | Il modello vocale si sta estraendo (solo alla prima accensione, ~1 minuto). |
| "Modello vocale locale mancante" | L'APK è stato compilato senza modello: rieseguire `./setup.sh`. |
| Riconosce male | Parlare più vicino e più lentamente; con internet e servizi Google è molto più preciso. |
| Uscire dall'app | 5 tocchi sulla batteria → menu tecnico. |
| Togliere il chiosco da computer | `adb shell dpm remove-active-admin it.parlafacile/.AdminReceiver` (oppure dal menu tecnico). |

## 10. Possibili sviluppi

- Salvataggio delle conversazioni del giorno.
