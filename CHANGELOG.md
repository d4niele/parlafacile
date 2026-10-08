# Changelog

Formato basato su [Keep a Changelog](https://keepachangelog.com/it/1.1.0/); il progetto usa [versioni semantiche](https://semver.org/lang/it/).

## [Non rilasciato]

### Aggiunto
- Test automatici: JUnit per l'app (segmentazione della voce, WAV, indirizzi, filtri sul testo) e pytest per il server (glossario, audio, API HTTP).
- Integrazione continua su GitHub (test, build, lint, controllo segreti) e aggiornamenti automatici delle dipendenze.
- Password facoltativa per il server di casa (`--token` o `PARLAFACILE_TOKEN`), limite di 10 MB per richiesta e `--indirizzo` per ascoltare solo su un'interfaccia.
- Documentazione: `CONTRIBUTING.md`, `SECURITY.md`, modelli per issue e pull request.

### Cambiato
- La logica pura è stata separata dalle classi Android (`Segmentatore`, `Wav`, `Indirizzi`, `FiltroTesto`) per poterla provare sulla JVM.
- Il server è diviso in moduli (`app.py`, `audio.py`, `glossario.py`) e non rivela più i dettagli degli errori interni.

## [0.1.0] - 2026-10-08

### Aggiunto
- App Android con tastone, sottotitoli a caratteri grandi, comandi vocali, indicatore di batteria e modalità chiosco.
- Motori di riconoscimento: Vosk offline, servizio vocale di sistema, server Whisper di casa, Whisper online (Groq, OpenAI) e Google Cloud Speech-to-Text.
- Modalità ibrida (Vosk subito, Whisper corregge) e ripiego automatico su Vosk.
- Correzione facoltativa di punteggiatura e maiuscole con Ollama.
- Pannello di configurazione nascosto (5 tocchi sulla barra della batteria).
- Server Whisper con `parole.txt` e `sostituzioni.txt`.
- Documentazione (`PROGETTO.md`, `ARCHITETTURA.md`) e presentazione in PDF.
