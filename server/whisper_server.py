#!/usr/bin/env python3
"""
Server di trascrizione per Parla Facile (Whisper in rete locale).

    pip install faster-whisper
    python3 whisper_server.py                  # modello "small", porta 8000
    python3 whisper_server.py --modello medium --porta 8000

Nel menu tecnico del tablet si imposta  <IP del computer>:8000
Espone POST /v1/audio/transcriptions (come OpenAI): campo "file" con un WAV,
risponde {"text": "..."}.  GET /salute risponde "ok".
"""
import argparse
import io
import json
import os
import re
import wave
import threading
import time
from email.parser import BytesParser
from email.policy import HTTP
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import numpy as np
from faster_whisper import WhisperModel

ap = argparse.ArgumentParser()
ap.add_argument("--modello", default="small", help="tiny, base, small, medium, large-v3")
ap.add_argument("--porta", type=int, default=8000)
ap.add_argument("--dispositivo", default="auto", help="auto, cpu o cuda")
ap.add_argument("--beam", type=int, default=1, help="1 = veloce, 5 = più accurato")
ap.add_argument("--thread", type=int, default=os.cpu_count() or 4, help="thread CPU (default: tutti)")
ap.add_argument("--salva", metavar="CARTELLA", help="salva i WAV ricevuti (per i test di velocità)")
args = ap.parse_args()

print(f"Carico il modello '{args.modello}'…", flush=True)
modello = WhisperModel(args.modello, device=args.dispositivo, compute_type="int8" if args.dispositivo != "cuda" else "float16",
                       cpu_threads=args.thread)
CARTELLA = os.path.dirname(os.path.abspath(__file__))
_cache = {}


def righe_file(nome):
    """Righe utili di un file di testo accanto allo script; si rilegge quando cambia."""
    percorso = os.path.join(CARTELLA, nome)
    try:
        mtime = os.path.getmtime(percorso)
    except OSError:
        return []
    if _cache.get(nome, (None,))[0] != mtime:
        with open(percorso, encoding="utf-8") as f:
            righe = [r.strip() for r in f if r.strip() and not r.lstrip().startswith("#")]
        _cache[nome] = (mtime, righe)
    return _cache[nome][1]


def parole():
    return righe_file("parole.txt")


def sostituzioni():
    coppie = []
    for riga in righe_file("sostituzioni.txt"):
        if "=" in riga:
            a, b = riga.split("=", 1)
            if a.strip():
                coppie.append((a.strip(), b.strip()))
    return sorted(coppie, key=lambda c: -len(c[0]))   # prima le più lunghe


def correggi(testo: str) -> str:
    for sbagliato, giusto in sostituzioni():
        testo = re.sub(r"(?<!\w)" + re.escape(sbagliato) + r"(?!\w)", giusto, testo, flags=re.IGNORECASE)
    return testo


blocco = threading.Lock()   # una trascrizione alla volta


def trascrivi(wav: bytes, lingua: str) -> str:
    audio = io.BytesIO(wav)
    try:   # il tablet manda WAV 16 kHz mono: lo leggo direttamente, senza ffmpeg/PyAV
        with wave.open(io.BytesIO(wav)) as w:
            if w.getframerate() == 16000 and w.getnchannels() == 1 and w.getsampwidth() == 2:
                audio = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768.0
    except wave.Error:
        pass
    elenco = parole()
    segmenti, _ = modello.transcribe(
        audio, language=lingua or "it", beam_size=args.beam,
        temperature=0, vad_filter=True, condition_on_previous_text=False,
        hotwords=" ".join(elenco) or None,
        initial_prompt=("Conversazione in italiano. Nomi e parole: " + ", ".join(elenco) + ".") if elenco else None,
    )
    return correggi(" ".join(s.text.strip() for s in segmenti).strip())


def campi_multipart(tipo: str, corpo: bytes) -> dict:
    msg = BytesParser(policy=HTTP).parsebytes(
        b"Content-Type: " + tipo.encode() + b"\r\n\r\n" + corpo)
    campi = {}
    for parte in msg.iter_parts():
        nome = parte.get_param("name", header="content-disposition")
        campi[nome] = parte.get_payload(decode=True)
    return campi


class Gestore(BaseHTTPRequestHandler):
    def _rispondi(self, codice, dati, tipo="application/json"):
        corpo = dati if isinstance(dati, bytes) else json.dumps(dati, ensure_ascii=False).encode()
        self.send_response(codice)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers()
        self.wfile.write(corpo)

    def do_GET(self):
        self._rispondi(200, b"ok", "text/plain")

    def do_POST(self):
        try:
            n = int(self.headers.get("Content-Length", 0))
            campi = campi_multipart(self.headers["Content-Type"], self.rfile.read(n))
            wav = campi["file"]
            lingua = (campi.get("language") or b"it").decode()
            if args.salva:
                os.makedirs(args.salva, exist_ok=True)
                with open(os.path.join(args.salva, f"{int(time.time() * 1000)}.wav"), "wb") as f:
                    f.write(wav)
            t0 = time.time()
            with blocco:
                testo = trascrivi(wav, lingua)
            print(f"→ {testo}   ({time.time() - t0:.1f} s, audio {(len(wav) - 44) / 32000:.1f} s)", flush=True)
            self._rispondi(200, {"text": testo})
        except Exception as e:
            print("Errore:", e, flush=True)
            self._rispondi(500, {"error": str(e)})

    def log_message(self, *a):
        pass


print("Riscaldo il modello…", flush=True)
modello.transcribe(np.zeros(16000, np.float32), language="it", beam_size=args.beam)
print(f"Pronto: porta {args.porta}  (beam {args.beam}, {args.thread} thread)", flush=True)
ThreadingHTTPServer(("0.0.0.0", args.porta), Gestore).serve_forever()
