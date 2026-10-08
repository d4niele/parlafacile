"""Server HTTP compatibile con POST /v1/audio/transcriptions di OpenAI."""
import hmac
import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from audio import campi_multipart, secondi_wav

MAX_BYTE = 10 * 1024 * 1024   # una frase di 25 s sono circa 0,8 MB: 10 MB bastano e avanzano


def crea_server(trascrivi, porta, token="", salva=None, indirizzo="0.0.0.0", max_byte=MAX_BYTE):
    """Crea il server. `trascrivi(wav: bytes, lingua: str) -> str` fa il lavoro vero.

    Con `token` ogni POST deve avere l'intestazione `Authorization: Bearer <token>`.
    """

    class Gestore(BaseHTTPRequestHandler):
        def _rispondi(self, codice, dati, tipo="application/json"):
            corpo = dati if isinstance(dati, bytes) else json.dumps(dati, ensure_ascii=False).encode()
            self.send_response(codice)
            self.send_header("Content-Type", tipo)
            self.send_header("Content-Length", str(len(corpo)))
            self.end_headers()
            self.wfile.write(corpo)

        def _autorizzato(self):
            if not token:
                return True
            dato = self.headers.get("Authorization", "")
            return hmac.compare_digest(dato.encode(), f"Bearer {token}".encode())

        def do_GET(self):
            if self.path.rstrip("/") in ("", "/salute", "salute"):
                self._rispondi(200, b"ok", "text/plain")
            else:
                self._rispondi(404, {"error": "non trovato"})

        def do_POST(self):
            if self.path.split("?")[0] != "/v1/audio/transcriptions":
                return self._rispondi(404, {"error": "non trovato"})
            if not self._autorizzato():
                return self._rispondi(401, {"error": "non autorizzato"})
            try:
                n = int(self.headers.get("Content-Length", 0))
            except ValueError:
                return self._rispondi(400, {"error": "Content-Length non valido"})
            if n <= 0 or n > max_byte:
                return self._rispondi(413 if n > max_byte else 400, {"error": "dimensione non valida"})
            try:
                campi = campi_multipart(self.headers.get("Content-Type", ""), self.rfile.read(n))
                wav = campi.get("file")
                if not wav:
                    return self._rispondi(400, {"error": "manca il campo 'file'"})
                lingua = (campi.get("language") or b"it").decode("utf-8", "replace")
                if salva:
                    os.makedirs(salva, exist_ok=True)
                    with open(os.path.join(salva, f"{int(time.time() * 1000)}.wav"), "wb") as f:
                        f.write(wav)
                t0 = time.time()
                testo = trascrivi(wav, lingua)
                print(f"→ {testo}   ({time.time() - t0:.1f} s, audio {secondi_wav(wav):.1f} s)", flush=True)
                self._rispondi(200, {"text": testo})
            except Exception as e:   # un errore su una frase non deve fermare il server
                print("Errore:", e, flush=True)
                self._rispondi(500, {"error": "errore interno"})

        def log_message(self, *a):
            pass

    return ThreadingHTTPServer((indirizzo, porta), Gestore)
