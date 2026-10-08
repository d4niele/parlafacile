import json
import threading
import urllib.error
import urllib.request

import pytest
from test_audio import crea_wav, richiesta

from app import crea_server


@pytest.fixture
def server():
    chiamate = []

    def trascrivi(wav, lingua):
        chiamate.append((len(wav), lingua))
        if lingua == "errore":
            raise RuntimeError("segreto interno")
        return "ciao mondo"

    def avvia(**kw):
        s = crea_server(trascrivi, 0, indirizzo="127.0.0.1", **kw)
        threading.Thread(target=s.serve_forever, daemon=True).start()
        avvia.server = s
        return f"http://127.0.0.1:{s.server_address[1]}", chiamate

    yield avvia
    if hasattr(avvia, "server"):
        avvia.server.shutdown()
        avvia.server.server_close()


def post(url, campi=None, intestazioni=None, corpo=None, tipo=None):
    if corpo is None:
        tipo, corpo = richiesta(campi or [("file", crea_wav([0] * 1600), "f.wav")])
    h = {"Content-Type": tipo or "multipart/form-data; boundary=XYZ", **(intestazioni or {})}
    req = urllib.request.Request(url + "/v1/audio/transcriptions", corpo, h)
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)


def test_salute(server):
    url, _ = server()
    with urllib.request.urlopen(url + "/salute", timeout=5) as r:
        assert r.read() == b"ok"


def test_trascrizione(server):
    url, chiamate = server()
    assert post(url) == (200, {"text": "ciao mondo"})
    assert chiamate[0][1] == "it"                         # lingua predefinita


def test_lingua_passata_al_trascrittore(server):
    url, chiamate = server()
    post(url, [("language", b"en", None), ("file", crea_wav([0] * 160), "f.wav")])
    assert chiamate[0][1] == "en"


def test_senza_file_400(server):
    url, _ = server()
    codice, corpo = post(url, [("language", b"it", None)])
    assert codice == 400 and "file" in corpo["error"]


def test_percorso_sconosciuto_404(server):
    url, _ = server()
    req = urllib.request.Request(url + "/altro", b"x", {"Content-Type": "text/plain"})
    with pytest.raises(urllib.error.HTTPError) as e:
        urllib.request.urlopen(req, timeout=5)
    assert e.value.code == 404


def test_password_richiesta(server):
    url, chiamate = server(token="segreta")
    assert post(url)[0] == 401
    assert post(url, intestazioni={"Authorization": "Bearer sbagliata"})[0] == 401
    assert post(url, intestazioni={"Authorization": "segreta"})[0] == 401   # manca "Bearer"
    assert chiamate == []                                                    # mai arrivato al modello
    assert post(url, intestazioni={"Authorization": "Bearer segreta"}) == (200, {"text": "ciao mondo"})


def test_salute_resta_libera_anche_con_password(server):
    url, _ = server(token="segreta")
    with urllib.request.urlopen(url + "/salute", timeout=5) as r:
        assert r.status == 200


def test_richiesta_troppo_grande_413(server):
    url, chiamate = server(max_byte=1000)
    codice, _ = post(url, [("file", b"\0" * 5000, "f.wav")])
    assert codice == 413 and chiamate == []


def test_errore_interno_non_rivela_dettagli_e_il_server_continua(server):
    url, _ = server()
    codice, corpo = post(url, [("language", b"errore", None), ("file", crea_wav([0] * 160), "f.wav")])
    assert codice == 500 and "segreto" not in json.dumps(corpo)
    assert post(url)[0] == 200


def test_salva_i_wav(server, tmp_path):
    url, _ = server(salva=str(tmp_path / "audio"))
    post(url)
    assert len(list((tmp_path / "audio").glob("*.wav"))) == 1
