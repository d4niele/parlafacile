import os
import time

from glossario import Glossario


def scrivi(cartella, nome, testo):
    percorso = cartella / nome
    percorso.write_text(testo, encoding="utf-8")
    return percorso


def test_file_assenti_non_fanno_danni(tmp_path):
    g = Glossario(str(tmp_path))
    assert g.parole() == []
    assert g.sostituzioni() == []
    assert g.correggi("ciao a tutti") == "ciao a tutti"


def test_commenti_e_righe_vuote_ignorati(tmp_path):
    scrivi(tmp_path, "parole.txt", "# commento\n\nRossi\n  Salerno  \n   # altro commento\n")
    assert Glossario(str(tmp_path)).parole() == ["Rossi", "Salerno"]


def test_sostituzione_su_parola_intera_senza_distinguere_maiuscole(tmp_path):
    scrivi(tmp_path, "sostituzioni.txt", "Rosi = Rossi\n")
    g = Glossario(str(tmp_path))
    assert g.correggi("Ho visto Rosi e ROSI, poi rosi.") == "Ho visto Rossi e Rossi, poi Rossi."
    assert g.correggi("il rosino") == "il rosino"          # non tocca le parole più lunghe
    assert g.correggi("arosi") == "arosi"                  # né quelle che la contengono


def test_prima_le_voci_piu_lunghe(tmp_path):
    scrivi(tmp_path, "sostituzioni.txt", "mi = io\nmi chiamo = sono\n")
    assert Glossario(str(tmp_path)).correggi("mi chiamo Luca e mi piace") == "sono Luca e io piace"


def test_il_valore_giusto_non_e_interpretato_come_regex(tmp_path):
    scrivi(tmp_path, "sostituzioni.txt", "x = \\1 $ \\\\\n")
    assert Glossario(str(tmp_path)).correggi("una x qui") == "una \\1 $ \\\\ qui"


def test_righe_senza_uguale_o_senza_chiave_ignorate(tmp_path):
    scrivi(tmp_path, "sostituzioni.txt", "senza uguale\n = vuota\na = b\n")
    assert Glossario(str(tmp_path)).sostituzioni() == [("a", "b")]


def test_si_rilegge_quando_il_file_cambia(tmp_path):
    p = scrivi(tmp_path, "parole.txt", "uno\n")
    g = Glossario(str(tmp_path))
    assert g.parole() == ["uno"]
    p.write_text("due\n", encoding="utf-8")
    os.utime(p, ns=(time.time_ns() + 5_000_000_000,) * 2)   # data di modifica certamente diversa
    assert g.parole() == ["due"]
