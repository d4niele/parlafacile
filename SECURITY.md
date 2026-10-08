# Sicurezza

## Come segnalare una vulnerabilità

Usa la **segnalazione privata** di GitHub: scheda *Security* → *Report a vulnerability*
(https://github.com/d4niele/parlafacile/security/advisories/new). Non aprire una issue pubblica.
Risponderemo appena possibile.

## Cosa è pensato per essere sicuro e cosa no

Parla Facile è pensato per **una rete di casa fidata**. I limiti noti:

| Tema | Stato |
|---|---|
| Traffico tablet → server di casa | **HTTP in chiaro** (serve `usesCleartextTraffic`). Usare solo su una Wi‑Fi fidata |
| Password del server | Facoltativa (`--token`). Senza, chiunque sulla rete locale può usare il server. **Consigliata** |
| Chiave API dei servizi online | Salvata **in chiaro** nelle preferenze dell'app sul tablet |
| Audio verso Groq, OpenAI, Google | Esce dalla rete di casa: valutare la privacy delle conversazioni |
| Ollama sulla porta 11435 | Se aperto alla rete locale, senza autenticazione |
| `--salva` | Conserva sul PC i file audio: solo per test, poi cancellarli |

## Buone pratiche per chi lo usa

- Avviare il server con `--token` e inserire la stessa password nel campo *Chiave API* del pannello.
- Se il server serve solo al PC stesso: `--indirizzo 127.0.0.1`.
- Non pubblicare `server/parole.txt` né `server/sostituzioni.txt` (sono nel `.gitignore`): contengono nomi di persone.
- Se una chiave API finisce in una chat, in un log o in un commit: **rigenerarla subito**.

## Per chi contribuisce

- Niente chiavi, indirizzi di casa, nomi di persone o audio nei commit. Il controllo dei segreti in CI blocca i casi più comuni, ma non tutti.
