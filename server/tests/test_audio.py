import io
import wave

import numpy as np
import pytest

from audio import campi_multipart, secondi_wav, wav_a_float


def crea_wav(campioni, frequenza=16000, canali=1):
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(canali)
        w.setsampwidth(2)
        w.setframerate(frequenza)
        w.writeframes(np.asarray(campioni, dtype=np.int16).tobytes())
    return buf.getvalue()


def test_wav_16k_mono_diventa_float():
    audio = wav_a_float(crea_wav([0, 16384, -16384, 32767]))
    assert audio.dtype == np.float32
    assert audio == pytest.approx([0, 0.5, -0.5, 32767 / 32768])


def test_altri_formati_restano_ai_byte_originali():
    assert wav_a_float(crea_wav([0] * 100, frequenza=44100)) is None
    assert wav_a_float(crea_wav([0] * 100, canali=2)) is None
    assert wav_a_float(b"non e' un wav") is None
    assert wav_a_float(b"") is None


def test_secondi_wav():
    assert secondi_wav(crea_wav([0] * 16000)) == pytest.approx(1.0)
    assert secondi_wav(b"") == 0


def richiesta(campi, confine="XYZ"):
    corpo = b""
    for nome, valore, nomefile in campi:
        corpo += f'--{confine}\r\nContent-Disposition: form-data; name="{nome}"'.encode()
        if nomefile:
            corpo += f'; filename="{nomefile}"'.encode()
        corpo += b"\r\n\r\n" + valore + b"\r\n"
    corpo += f"--{confine}--\r\n".encode()
    return f"multipart/form-data; boundary={confine}", corpo


def test_multipart_con_audio_binario():
    binario = bytes(range(256)) + b"\r\n--non-e-il-confine\r\n"
    tipo, corpo = richiesta([("language", b"it", None), ("file", binario, "frase.wav")])
    campi = campi_multipart(tipo, corpo)
    assert campi["language"] == b"it"
    assert campi["file"] == binario


def test_multipart_vuoto():
    assert campi_multipart("multipart/form-data; boundary=ZZ", b"--ZZ--\r\n") == {}
