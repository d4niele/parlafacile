"""Lettura dei WAV mandati dal tablet e della richiesta multipart."""
import io
import wave
from email.parser import BytesParser
from email.policy import HTTP

FREQUENZA = 16000


def wav_a_float(wav):
    """WAV 16 kHz mono a 16 bit -> array float32 in [-1, 1]; altrimenti None.

    Si legge direttamente, senza ffmpeg/PyAV. Con None il chiamante può passare
    i byte originali a faster-whisper, che li decodifica da solo.
    """
    import numpy as np
    try:
        with wave.open(io.BytesIO(wav)) as w:
            if (w.getframerate() == FREQUENZA and w.getnchannels() == 1
                    and w.getsampwidth() == 2):
                dati = np.frombuffer(w.readframes(w.getnframes()), np.int16)
                return dati.astype(np.float32) / 32768.0
    except (wave.Error, EOFError):
        pass
    return None


def secondi_wav(wav):
    """Durata di un WAV 16 kHz mono 16 bit (44 byte di intestazione)."""
    return max(0, len(wav) - 44) / (FREQUENZA * 2)


def campi_multipart(tipo, corpo):
    """Campi di una richiesta multipart/form-data: {nome: byte}."""
    msg = BytesParser(policy=HTTP).parsebytes(
        b"Content-Type: " + tipo.encode() + b"\r\n\r\n" + corpo)
    campi = {}
    for parte in msg.iter_parts():
        nome = parte.get_param("name", header="content-disposition")
        if nome:
            campi[nome] = parte.get_payload(decode=True)
    return campi
