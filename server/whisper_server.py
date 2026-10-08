#!/usr/bin/env python3
"""
Server di trascrizione per Parla Facile (Whisper in rete locale).

    pip install -r requirements.txt
    python3 whisper_server.py                       # modello "small", porta 8000
    python3 whisper_server.py --modello medium --token UNA-PASSWORD

Nel pannello del tablet si imposta  <IP del computer>:8000  (e la password, se c'è).
Espone POST /v1/audio/transcriptions (come OpenAI): campo "file" con un WAV,
risponde {"text": "..."}.  GET /salute risponde "ok".
"""
import argparse
import io
import os
import threading

QUI = os.path.dirname(os.path.abspath(__file__))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--modello", default="small", help="tiny, base, small, medium, large-v3")
    ap.add_argument("--porta", type=int, default=8000)
    ap.add_argument("--indirizzo", default="0.0.0.0", help="interfaccia su cui ascoltare (127.0.0.1 = solo questo PC)")
    ap.add_argument("--dispositivo", default="auto", help="auto, cpu o cuda")
    ap.add_argument("--beam", type=int, default=1, help="1 = veloce, 5 = più accurato")
    ap.add_argument("--thread", type=int, default=os.cpu_count() or 4, help="thread CPU (default: tutti)")
    ap.add_argument("--token", default=os.environ.get("PARLAFACILE_TOKEN", ""),
                    help="password richiesta al tablet (o variabile PARLAFACILE_TOKEN)")
    ap.add_argument("--salva", metavar="CARTELLA", help="salva i WAV ricevuti (per i test di velocità)")
    args = ap.parse_args()

    import numpy as np
    from faster_whisper import WhisperModel

    from app import crea_server
    from audio import FREQUENZA, wav_a_float
    from glossario import Glossario

    print(f"Carico il modello '{args.modello}'…", flush=True)
    modello = WhisperModel(args.modello, device=args.dispositivo, cpu_threads=args.thread,
                           compute_type="int8" if args.dispositivo != "cuda" else "float16")
    glossario = Glossario(QUI)
    blocco = threading.Lock()   # una trascrizione alla volta

    def trascrivi(wav, lingua):
        audio = wav_a_float(wav)
        elenco = glossario.parole()
        guida = ("Conversazione in italiano. Nomi e parole: " + ", ".join(elenco) + ".") if elenco else ""
        with blocco:
            segmenti, _ = modello.transcribe(
                audio if audio is not None else io.BytesIO(wav),
                language=lingua or "it", beam_size=args.beam,
                temperature=0, vad_filter=True, condition_on_previous_text=False,
                hotwords=" ".join(elenco) or None,
                initial_prompt=guida or None,
            )
            testo = " ".join(s.text.strip() for s in segmenti).strip()
        return glossario.correggi(testo)

    print("Riscaldo il modello…", flush=True)
    modello.transcribe(np.zeros(FREQUENZA, np.float32), language="it", beam_size=args.beam)
    server = crea_server(trascrivi, args.porta, token=args.token, salva=args.salva, indirizzo=args.indirizzo)
    print(f"Pronto: porta {args.porta}  (beam {args.beam}, {args.thread} thread"
          f"{', con password' if args.token else ', SENZA password'})", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
