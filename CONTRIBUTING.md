# Come contribuire

Grazie dell'interesse! Parla Facile è un progetto piccolo e con uno scopo preciso: **sottotitoli dal vivo, semplici, per chi ci sente poco**. Le modifiche che aggiungono menu o complicazioni all'uso quotidiano vanno discusse prima in una issue.

## Preparare l'ambiente

**App Android** — serve Java 17 e l'SDK Android (lo scarica `./setup.sh`, oppure imposta `ANDROID_HOME`):

```bash
export JAVA_HOME=/percorso/di/jdk-17
./gradlew testDebugUnitTest          # test sulla JVM, senza tablet
./gradlew assembleDebug lintDebug    # APK di debug e controlli di lint
```

**Server** — Python 3.10 o più recente:

```bash
cd server
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/pytest                      # test (non serve faster-whisper)
.venv/bin/ruff check .                # lint
```

## Struttura

Vedi [`ARCHITETTURA.md`](ARCHITETTURA.md). In breve: la **logica pura** (taglio delle frasi, WAV, indirizzi, filtri sul testo) sta in classi senza Android (`Segmentatore`, `Wav`, `Indirizzi`, `FiltroTesto`) con i loro test; le classi `Motore*` e `MainActivity` fanno da collante con Android e la rete.

## Regole

1. **Ogni modifica alla logica ha un test.** Se non si riesce a provare sulla JVM, spostare la parte pura in una classe a parte.
2. **Prima i test, poi la pull request**: devono passare `testDebugUnitTest`, `pytest` e `ruff`.
3. **Niente dati personali**: né nomi di persone, né indirizzi di casa, né chiavi, né audio. Usare i file `*.example.txt` per gli esempi.
4. **Codice in stile con quello che c'è**: nomi in italiano, commenti che spiegano il *perché*, nessuna libreria nuova senza una buona ragione (l'app non usa AndroidX di proposito).
5. Aggiornare `CHANGELOG.md` (sezione *Non rilasciato*) e, se cambia il comportamento, `README.md` e `ARCHITETTURA.md`.
6. Messaggi di commit brevi e chiari, al presente: «Aggiunge il limite di dimensione alle richieste».

## Provare sul tablet

`./setup.sh --solo-verifica` controlla il tablet senza cambiare nulla. Per provare solo l'app: `./gradlew installDebug` con il tablet collegato. Chi cambia l'ascolto o la rete dovrebbe dire nella pull request su che dispositivo e con quale motore ha provato.
