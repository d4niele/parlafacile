# Parla Facile — Architettura

Documento tecnico aggiornato allo stato attuale del progetto. Per l'installazione e l'uso quotidiano vedi `PROGETTO.md`.

---

## 1. Visione d'insieme

Parla Facile trasforma un tablet Android (Nexus 7 2013, LineageOS 18.1 / Android 11) in un apparecchio con una sola funzione: **scrivere a caratteri enormi ciò che si dice**, per una persona anziana ipoudente.

Il tablet registra la voce, la trasforma in testo con uno di più **motori di riconoscimento** (sul tablet, in casa o su internet) e mostra le frasi. Un **server nel PC di casa** può fare il lavoro pesante (Whisper) e, facoltativamente, una **correzione con un piccolo modello di linguaggio** (Ollama).

```
                     ┌──────────────────────────── TABLET (app Parla Facile) ───────────────────────────┐
                     │                                                                                  │
  voce ──► microfono ──► MotoreWhisper (cattura 16 kHz, taglia le frasi, invia)                           │
                     │         │   └─ (ibrido) Vosk dalla stessa cattura: testo provvisorio subito        │
                     │         │                                                                          │
                     │   MotoreVosk (offline)      MotoreRete (servizio vocale di Android, se esiste)    │
                     │         │                              │                                          │
                     │         └────────────► MainActivity ◄──┘   scelta motore, sottotitoli,            │
                     │                          │                 comandi vocali, chiosco, pannello       │
                     │                          └──► Correttore (facoltativo) ──┐                        │
                     └──────────────────────────────┬─────────────────────────────┼────────────────────────┘
                                                     │ Wi-Fi                       │
              ┌──────────────────────────────────────┼─────────────────────────────┼───────────────┐
              ▼                                      ▼                             ▼               │
   PC di casa :8000                         Internet                         PC di casa :11435       │
   server/whisper_server.py                 Groq / OpenAI (Whisper)          Ollama (qwen2.5:3b)     │
   faster-whisper + parole.txt              Google Cloud Speech-to-Text      punteggiatura/maiuscole │
   + sostituzioni.txt                                                                                │
```

## 2. Principi di progetto

1. **Una sola funzione, sempre pronta.** L'app è la schermata Home; in modalità chiosco niente barra di stato né tasti di uscita.
2. **Mai restare muti.** Se un motore evoluto cade (rete, server), si passa da soli a Vosk, che lavora offline.
3. **Nessuna dipendenza da Google.** Il servizio vocale di sistema è opzionale; il percorso principale è il server di casa o un servizio Whisper.
4. **Pochissime dipendenze.** Nessuna libreria AndroidX: solo Android SDK e Vosk. Minimo API 21, target API 30.
5. **Tutto configurabile dal tablet**, in un pannello nascosto, senza ricompilare.

## 3. App Android (`app/src/main/java/it/parlafacile/`)

| File | Ruolo |
|---|---|
| `MainActivity.java` | Interfaccia unica, scelta del motore, sottotitoli, comandi vocali, batteria, chiosco, pannello di configurazione, collegamento al correttore |
| `MotoreAscolto.java` | Interfaccia comune dei motori (`avvia`, `ferma`, `chiudi`, `attivo`, `nome`) e dell'ascoltatore (`parziale`, `finale`, `errore`) |
| `MotoreVosk.java` | Riconoscimento offline con Vosk (modello `vosk-model-small-it-0.22`, ~50 MB, in `assets/model-it/`) |
| `MotoreRete.java` | Riconoscimento di sistema (`SpeechRecognizer`), riavviato di continuo. **Non disponibile su questo tablet** (nessun servizio vocale installato) |
| `MotoreWhisper.java` | Cattura audio, segmentazione in frasi, invio a un servizio di trascrizione, ibrido con Vosk |
| `Correttore.java` | Correzione facoltativa del testo con un modello di linguaggio (API di Ollama) |
| `AdminReceiver.java` | Serve solo alla modalità chiosco (device owner) |

### 3.1 Interfaccia dei motori

Ogni motore implementa `MotoreAscolto` e comunica con `MainActivity` tramite `Ascoltatore`. Tutti i callback arrivano **sul thread principale**.

- `parziale(testo)`: testo provvisorio (blu) mentre si parla.
- `finale(testo)`: frase conclusa, aggiunta ai sottotitoli.
- `errore(messaggio, recuperabile)`: se `recuperabile == false` il motore si è fermato e l'app passa a Vosk (salvo i modi esclusivi).

### 3.2 Scelta del motore

Preferenza `modo`, scelta dal pannello:

| Modo | Comportamento |
|---|---|
| `auto` (predefinito) | Whisper/cloud se l'indirizzo è impostato **e** c'è rete → altrimenti servizio di sistema se c'è rete e esiste → altrimenti Vosk → ultima possibilità: servizio di sistema |
| `locale` | Solo Vosk |
| `whisper` | Solo Whisper (server di casa o servizio online). Se cade, l'ascolto si ferma con un messaggio |
| `rete` | Solo servizio vocale di sistema |

In `auto`, se il motore evoluto fallisce (non recuperabile) l'app avvisa a schermo e continua con Vosk.

### 3.3 MotoreWhisper

Registra con `AudioRecord` a 16 kHz mono, 16 bit, e lavora a blocchi da 100 ms.

**Segmentazione delle frasi (sul tablet)**
- Rilevamento della voce per energia (RMS): soglia = `max(350, rumore × 2,5)`, con il rumore di fondo che si adatta durante i silenzi.
- 300 ms di audio prima dell'inizio della voce vengono conservati (pre-roll).
- La frase finisce dopo **0,8 s di silenzio**; sotto 0,4 s di voce è considerata rumore e scartata; taglio forzato a 25 s.
- Prima dell'invio il silenzio finale è ridotto a 0,3 s (meno dati e meno lavoro per il server).

**Invio** (un solo thread di rete, in ordine; la registrazione non si ferma)
- **Formato OpenAI** (`/v1/audio/transcriptions`, multipart: `file` WAV, `model`, `language=it`, `response_format=json`, `temperature=0`). Vale per il server di casa, Groq, OpenAI e simili. Chiave API come `Authorization: Bearer`.
- **Google Cloud Speech-to-Text v1** (JSON con audio in base64, chiave nell'indirizzo): usato automaticamente se l'indirizzo contiene `speech.googleapis.com`.
- Connessione HTTP riutilizzata (keep-alive); timeout 3 s di connessione, 30 s di lettura.
- All'avvio si verifica che il server sia raggiungibile (connessione TCP, 2,5 s).
- Due errori consecutivi → motore fermato e passaggio a Vosk.
- Filtro delle "allucinazioni" di Whisper sul rumore (frasi tipo "Sottotitoli creati dalla comunità Amara.org").

**Modalità ibrida** (opzione, accesa di default)
- Dalla **stessa cattura audio** si alimenta anche un `Recognizer` di Vosk: il testo provvisorio appare subito, in blu, mentre la persona parla.
- Quando Whisper risponde, il testo corretto sostituisce quello provvisorio.
- Se Whisper non risponde per una frase, resta il testo di Vosk.
- Il modello Vosk può terminare il caricamento dopo l'avvio: viene agganciato alla frase successiva.

### 3.4 Correttore (facoltativo)

Se è impostato un indirizzo Ollama, ogni frase di almeno tre parole viene inviata a `/api/chat` per sistemare punteggiatura e maiuscole. Il testo grezzo si vede subito; la riga viene sostituita quando arriva la versione corretta.

- Parametri leggeri: `num_ctx 1024`, `num_thread 6`, `num_predict` proporzionato alla frase, `keep_alive 30m`, `temperature 0`.
- **Accettata solo se le parole sono identiche** a quelle originali (cambiano punteggiatura e maiuscole, non il senso). Altrimenti resta il testo di Whisper.
- Qualsiasi errore o timeout (10 s) lascia il testo originale.

### 3.5 Interfaccia e comandi

- Barra della batteria sempre visibile: verde, arancio sotto il 35 %, rossa con "METTERE IN CARICA!" sotto il 15 %.
- Riga di stato: "Ascolto con Whisper", "Ascolto con Whisper + Vosk", "Ascolto senza internet", "Ascolto con internet".
- Sottotitoli: le ultime due frasi grandi e nere, le precedenti piccole e grigie; il testo provvisorio in blu. Pulsanti A−, A+, CANCELLA.
- Tastone ON/OFF (verde/rosso), oppure tasti volume o pulsante Bluetooth.
- Comandi vocali (detti da soli): "cancella", "più grande", "più piccolo".
- Risparmio: dopo 10 minuti senza voce l'ascolto si spegne (solo a batteria).
- Il tasto Indietro non esce dall'app.

### 3.6 Chiosco

L'app è registrata come HOME. Con il device owner (`AdminReceiver`) usa Lock Task: niente barra di stato né tasti Home/Recenti. L'uscita è dal pannello nascosto.

### 3.7 Pannello di configurazione nascosto

**5 tocchi rapidi (entro 3 secondi) sulla barra della batteria.** Contiene:

- **Motore in uso ora** (quello in ascolto, o quello che verrebbe usato).
- Modo di ascolto (4 scelte).
- Indirizzo Whisper, chiave API, modello, casella "ibrido"; tasti **Compila per Groq** e **Compila per Google Cloud**; **Prova connessione**.
- Correzione con AI: indirizzo e modello Ollama.
- Impostazioni Android; scelta della Home / disattivazione del chiosco.

### 3.8 Preferenze (`SharedPreferences` "parlafacile")

| Chiave | Significato |
|---|---|
| `modo` | `auto`, `locale`, `whisper`, `rete` |
| `whisper` | Indirizzo (`IP:porta` oppure URL completo) |
| `whisper_chiave` | Chiave API (in chiaro sul tablet) |
| `whisper_modello` | Nome del modello (vuoto = `whisper-1`) |
| `ibrido` | Vosk per il testo provvisorio |
| `llm` | Indirizzo di Ollama |
| `llm_modello` | Modello di Ollama (vuoto = `qwen2.5:3b`) |

## 4. Server nel PC di casa (`server/`)

`whisper_server.py` — trascrizione con **faster-whisper** (ambiente separato `.venv-whisper/`).

- `POST /v1/audio/transcriptions` (campo `file`, WAV; risponde `{"text": "…"}`) e `GET /salute`.
- Ascolta su `0.0.0.0:8000`. Una trascrizione alla volta (blocco), richieste in parallelo accettate.
- Legge direttamente i WAV 16 kHz mono (numpy), senza ffmpeg/PyAV.
- Filtro dei silenzi attivo (`vad_filter`): spegnerlo rallenta e produce frasi inventate.
- Riscaldamento del modello all'avvio.
- Opzioni: `--modello` (small), `--porta`, `--dispositivo` (auto/cpu/cuda), `--beam` (1), `--thread` (tutti i core), `--salva CARTELLA` (conserva i WAV per i test).
- Ogni trascrizione scrive nel log testo, tempo e durata dell'audio.

**Parole e dialetto**
- Nel repository ci sono solo `parole.example.txt` e `sostituzioni.example.txt`: si copiano in `parole.txt` e `sostituzioni.txt` (ignorati da git, così i nomi di famiglia restano privati).
- `parole.txt`: parole e nomi da favorire (una per riga; `#` per i commenti). Usate come `hotwords` e come testo guida (`initial_prompt`).
- `sostituzioni.txt`: correzioni `sbagliato = giusto` applicate dopo la trascrizione, su parole intere, senza distinguere maiuscole/minuscole.
- Entrambi i file si rileggono da soli quando cambiano.

**Ollama (correzione)**: istanza dedicata sulla porta 11435 (`OLLAMA_HOST=0.0.0.0:11435`), modello `qwen2.5:3b`. Non riparte da sola al riavvio del PC.

## 5. Flusso di una frase

1. La persona parla; l'app rileva l'inizio della voce e comincia a raccogliere l'audio.
2. (Ibrido) Vosk mostra subito il testo provvisorio in blu.
3. Dopo 0,8 s di silenzio la frase viene chiusa, ripulita del silenzio finale e inviata come WAV.
4. Il server trascrive (filtro silenzi, parole favorite) e applica `sostituzioni.txt`.
5. L'app riceve il testo, lo mostra al posto del provvisorio e, se impostato, lo manda al correttore.
6. Se arriva la correzione e le parole sono identiche, la riga viene aggiornata.

## 6. Misure (PC senza scheda grafica: 12 core, 14 GB RAM)

Su 12 frasi reali (durata media 2,3 s), `beam 1`, 12 thread:

| Configurazione | Tempo medio per frase | Note |
|---|---|---|
| `small`, filtro silenzi acceso (attuale) | 1,40 s | |
| `small`, filtro silenzi spento | 2,09 s | 3 frasi su 12 diventano "Sottotitoli e revisione a cura di QTSS" |
| `medium`, filtro silenzi acceso | 3,65 s | Più preciso su nomi e parole comuni ("A che ora gioca il Napoli oggi?") |

Il tempo è quasi indipendente dalla lunghezza della frase: ipotesi (non verificata) che dipenda dalla finestra fissa da 30 s di Whisper. Ollama dal tablet: 0,56–0,82 s per frase (`qwen2.5:3b`).

## 7. Sicurezza e privacy

- Il traffico verso il server di casa è **HTTP in chiaro** (`usesCleartextTraffic`); il server **non ha autenticazione** ed è raggiungibile da tutta la rete locale.
- La copia di Ollama sulla porta 11435 è aperta alla rete locale.
- La chiave API è salvata **in chiaro** nelle preferenze del tablet.
- Con Groq, OpenAI o Google l'audio delle conversazioni **esce da casa**; con il server di casa no.
- `--salva` conserva sul PC i file audio: usarlo solo per i test e cancellarli.
- Installata ora la versione **debug** (debuggable); la release si costruisce con `setup.sh`.

## 8. Stato di verifica

| Parte | Stato |
|---|---|
| Vosk offline | In uso |
| Server Whisper di casa | Provato dal tablet con frasi reali |
| Ibrido Vosk + Whisper | Testo provvisorio verificato nel log; da riprovare dopo l'ultimo riavvio |
| Correzione Ollama | Richieste arrivate dal tablet (0,56–0,82 s); non verificato quante correzioni vengono accettate |
| Groq | Chiave accettata dall'elenco modelli; **nessuna trascrizione completata con successo** |
| Google Cloud | Scritto, **mai provato** (manca la chiave) |
| Servizio vocale di sistema | Non disponibile sul tablet |

## 9. Limiti noti

- Whisper `small` sbaglia su nomi propri e parole poco comuni; il dialetto stretto non è supportato da Whisper. `medium` è migliore ma impiega più del doppio.
- Solo CPU: i tempi di 1,4–1,8 s per frase non scendono senza scheda grafica NVIDIA.
- Frasi dette a voce bassa possono essere scartate dal filtro (testo vuoto).
- Whisper non produce testo provvisorio: serve l'ibrido con Vosk.
- Il server di casa e Ollama non partono da soli all'accensione del PC.

## 10. Possibili sviluppi

- Avvio automatico del server e di Ollama (servizio di sistema).
- Autenticazione e cifratura verso il server.
- Scheda NVIDIA e modello `large-v3` / `large-v3-turbo`.
- Regolazione della soglia di voce e della pausa dal pannello.
- Salvataggio delle conversazioni (con il consenso della famiglia) per addestrare il modello sulla voce e sul dialetto della persona.
- Adattatori per altri servizi (Deepgram, Azure).
