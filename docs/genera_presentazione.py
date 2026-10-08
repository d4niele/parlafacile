#!/usr/bin/env python3
"""Genera ParlaFacile_presentazione.pdf (pip install reportlab)."""
import os
import sys

from reportlab.lib.colors import HexColor, white
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Frame, Paragraph, Table, TableStyle

QUI = os.path.dirname(os.path.abspath(__file__))
USCITA = sys.argv[1] if len(sys.argv) > 1 else os.path.join(QUI, "..", "ParlaFacile_presentazione.pdf")
SCHERMATA = os.path.join(QUI, "schermata.png")

F = "/usr/share/fonts/truetype/dejavu/"
pdfmetrics.registerFont(TTFont("D", F + "DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("D-B", F + "DejaVuSans-Bold.ttf"))
pdfmetrics.registerFont(TTFont("D-I", F + "DejaVuSans-Oblique.ttf"))
pdfmetrics.registerFontFamily("D", normal="D", bold="D-B", italic="D-I", boldItalic="D-B")

L, A = 842, 595                      # A4 orizzontale
SFONDO = HexColor("#FFFDF5")
VERDE = HexColor("#1B7F3B")
ROSSO = HexColor("#B3261E")
ARANCIO = HexColor("#C25E00")
BLU = HexColor("#0D47A1")
SCURO = HexColor("#37474F")
GRIGIO = HexColor("#757575")
CHIARO = HexColor("#ECEFF1")
M = 44                                # margine

corpo = ParagraphStyle("corpo", fontName="D", fontSize=13, leading=19, textColor=SCURO)
piccolo = ParagraphStyle("piccolo", parent=corpo, fontSize=10.5, leading=14.5)
cella = ParagraphStyle("cella", parent=corpo, fontSize=10.5, leading=14)
cella_b = ParagraphStyle("cella_b", parent=cella, fontName="D-B", textColor=white)

c = canvas.Canvas(USCITA, pagesize=(L, A))
c.setTitle("Parla Facile - presentazione")
c.setAuthor("Parla Facile")
numero = [0]


def pagina(titolo, sottotitolo=None):
    if numero[0]:
        c.showPage()
    numero[0] += 1
    c.setFillColor(SFONDO)
    c.rect(0, 0, L, A, fill=1, stroke=0)
    c.setFillColor(VERDE)
    c.rect(0, A - 78, L, 78, fill=1, stroke=0)
    c.setFillColor(white)
    c.setFont("D-B", 26)
    c.drawString(M, A - 50, titolo)
    if sottotitolo:
        c.setFont("D", 12)
        c.drawString(M, A - 69, sottotitolo)
    c.setFillColor(GRIGIO)
    c.setFont("D", 9)
    c.drawString(M, 20, "Parla Facile")
    c.drawRightString(L - M, 20, str(numero[0]))


def testo(x, y_alto, larghezza, altezza, html, stile=corpo):
    Frame(x, y_alto - altezza, larghezza, altezza, 0, 0, 0, 0, showBoundary=0).addFromList(
        [Paragraph(html, stile)], c)


def punti(x, y_alto, larghezza, altezza, voci, stile=corpo):
    righe = [Paragraph(f'<font color="#1B7F3B"><b>&#9632;</b></font>&nbsp; {v}', stile) for v in voci]
    f = Frame(x, y_alto - altezza, larghezza, altezza, 0, 0, 0, 0, showBoundary=0)
    for r in righe:
        r.spaceAfter = 7
    f.addFromList(righe, c)


def riquadro(x, y, w, h, titolo, sotto="", colore=SCURO, riempi=white, bordo=None, dim=11):
    c.setFillColor(riempi)
    c.setStrokeColor(bordo or colore)
    c.setLineWidth(1.6)
    c.roundRect(x, y, w, h, 8, fill=1, stroke=1)
    c.setFillColor(colore)
    c.setFont("D-B", dim)
    c.drawCentredString(x + w / 2, y + h - 17, titolo)
    if sotto:
        c.setFont("D", dim - 2)
        c.setFillColor(SCURO)
        for i, riga in enumerate(sotto.split("\n")):
            c.drawCentredString(x + w / 2, y + h - 31 - i * 12, riga)


def freccia(x1, y1, x2, y2, colore=SCURO, tratteggio=False):
    import math
    c.setStrokeColor(colore)
    c.setFillColor(colore)
    c.setLineWidth(1.8)
    if tratteggio:
        c.setDash(4, 3)
    c.line(x1, y1, x2, y2)
    c.setDash()
    a = math.atan2(y2 - y1, x2 - x1)
    p = [(x2, y2), (x2 - 9 * math.cos(a - 0.4), y2 - 9 * math.sin(a - 0.4)),
         (x2 - 9 * math.cos(a + 0.4), y2 - 9 * math.sin(a + 0.4))]
    t = c.beginPath()
    t.moveTo(*p[0]); t.lineTo(*p[1]); t.lineTo(*p[2]); t.close()
    c.drawPath(t, fill=1, stroke=0)


def tabella(x, y_alto, larghezze, righe, intestazione=True):
    dati = []
    for i, r in enumerate(righe):
        dati.append([Paragraph(str(v), cella_b if (i == 0 and intestazione) else cella) for v in r])
    t = Table(dati, colWidths=larghezze)
    stile = [("VALIGN", (0, 0), (-1, -1), "TOP"),
             ("GRID", (0, 0), (-1, -1), 0.5, HexColor("#B0BEC5")),
             ("LEFTPADDING", (0, 0), (-1, -1), 6), ("RIGHTPADDING", (0, 0), (-1, -1), 6),
             ("TOPPADDING", (0, 0), (-1, -1), 5), ("BOTTOMPADDING", (0, 0), (-1, -1), 5)]
    if intestazione:
        stile.append(("BACKGROUND", (0, 0), (-1, 0), SCURO))
    for i in range(1, len(righe)):
        stile.append(("BACKGROUND", (0, i), (-1, i), white if i % 2 else CHIARO))
    t.setStyle(TableStyle(stile))
    w, h = t.wrapOn(c, sum(larghezze), A)
    t.drawOn(c, x, y_alto - h)
    return h


def schermata(x, y, altezza):
    img = ImageReader(SCHERMATA)
    iw, ih = img.getSize()
    w = altezza * iw / ih
    c.setFillColor(SCURO)
    c.roundRect(x - 6, y - 6, w + 12, altezza + 12, 10, fill=1, stroke=0)
    c.drawImage(img, x, y, w, altezza)
    return w


# ------------------------------------------------------------------ 1. copertina
pagina("")
c.setFillColor(VERDE)
c.rect(0, 0, L, A, fill=1, stroke=0)
c.setFillColor(white)
c.setFont("D-B", 54)
c.drawString(M + 10, A - 190, "Parla Facile")
c.setFont("D", 20)
c.drawString(M + 10, A - 228, "Sottotitoli dal vivo per chi ci sente poco")
testo(M + 10, A - 270, 430, 140,
      '<font color="white">Un tablet con una sola funzione: scrivere a caratteri enormi '
      'quello che le dicono.<br/><br/>Senza account, senza menu, sempre pronto.</font>',
      ParagraphStyle("cop", parent=corpo, fontSize=15, leading=22))
c.setFont("D", 11)
c.drawString(M + 10, 60, "Nexus 7 2013  -  LineageOS  -  riconoscimento vocale in casa, offline o online")
schermata(L - 300, 55, 470)

# ------------------------------------------------------------------ 2. il problema
pagina("A chi serve e cosa fa", "Una persona anziana, poco udito, nessuna voglia di imparare un'app")
larg = (L - 2 * M - 40) / 3
for i, (tit, col, t) in enumerate([
    ("Il problema", ROSSO, "Chi ci sente poco perde il filo delle conversazioni in famiglia. "
     "Le app di sottotitoli esistenti hanno menu, account e impostazioni: troppo per un uso quotidiano."),
    ("La soluzione", VERDE, "Un tablet che fa una cosa sola. Si preme un tastone verde e chi parla viene "
     "scritto a caratteri enormi, con le ultime due frasi in nero e le precedenti piccole e grigie."),
    ("Il metodo", BLU, "L'app è la schermata Home e parte da sola. Il riconoscimento vocale può stare "
     "sul tablet, nel PC di casa o su internet, e se uno manca passa da solo all'altro."),
]):
    x = M + i * (larg + 20)
    c.setFillColor(white); c.setStrokeColor(col); c.setLineWidth(2)
    c.roundRect(x, 250, larg, 230, 10, fill=1, stroke=1)
    c.setFillColor(col); c.setFont("D-B", 17)
    c.drawString(x + 16, 450, tit)
    testo(x + 16, 435, larg - 32, 170, t, corpo)
c.setFillColor(SCURO); c.setFont("D-I", 13)
c.drawString(M, 205, "Obiettivo: nessun gesto da imparare. Premere, parlare, leggere.")

# ------------------------------------------------------------------ 3. come si usa
pagina("Come si usa", "Tutto quello che serve è visibile sullo schermo")
w = schermata(M + 6, 40, A - 78 - 70)
punti(M + w + 40, A - 105, L - 2 * M - w - 40, 430, [
    "<b>Tastone</b> in basso: verde = spento, rosso = sta ascoltando. Anche con i tasti del volume "
    "o un pulsante Bluetooth.",
    "<b>Sottotitoli enormi</b>: le ultime due frasi grandi e nere, le altre piccole e grigie. "
    "A&#8722; e A+ cambiano la dimensione.",
    "<b>Comandi vocali</b> (detti da soli): &laquo;cancella&raquo;, &laquo;più grande&raquo;, "
    "&laquo;più piccolo&raquo;.",
    "<b>Batteria</b> sempre in vista: arancio sotto il 35%, rossa con &laquo;mettere in carica&raquo; sotto il 15%.",
    "<b>Sempre pronto</b>: è la schermata Home, parte all'accensione; in carica lo schermo resta acceso.",
    "<b>Modalità chiosco</b>: niente barra di stato, niente tasti di uscita, il tasto Indietro non esce.",
    "<b>Risparmio</b>: dopo 10 minuti di silenzio, a batteria, l'ascolto si spegne da solo.",
], corpo)

# ------------------------------------------------------------------ 4. motori
pagina("I motori di riconoscimento", "L'app ne sa usare più di uno e sceglie da sola")
tabella(M, A - 100, [150, 130, 90, 408], [
    ["Motore", "Dove gira", "Internet", "Note"],
    ["<b>Vosk</b>", "Sul tablet", "No", "Modello italiano piccolo (~50 MB) incluso. Funziona sempre. Meno preciso; dà il testo provvisorio mentre si parla."],
    ["<b>Server Whisper di casa</b>", "PC in casa", "Solo Wi-Fi", "Whisper (faster-whisper). Il più preciso senza uscire di casa, con elenco di parole e correzioni personali."],
    ["<b>Whisper online</b>", "Groq / OpenAI", "Sì", "Stesso protocollo del server di casa: basta indirizzo, chiave e modello. Veloce, ma l'audio esce di casa."],
    ["<b>Google Cloud</b>", "Google", "Sì", "Speech-to-Text con punteggiatura automatica. Richiede chiave e fatturazione."],
    ["<b>Servizio di sistema</b>", "Android", "Sì", "Usa il riconoscimento vocale di Android, se presente. Su questo tablet non c'è."],
], True)
c.setFillColor(SCURO); c.setFont("D-B", 13)
c.drawString(M, 190, "Modo automatico")
punti(M, 178, L - 2 * M, 130, [
    "Prima il server Whisper / servizio online (se impostato e c'è rete), poi il servizio di sistema, poi Vosk.",
    "Se il motore evoluto cade durante l'ascolto, l'app avvisa e continua da sola con Vosk: <b>non resta mai muta</b>.",
    "Modalità <b>ibrida</b>: Vosk scrive subito in blu, Whisper corregge appena risponde.",
], piccolo)

# ------------------------------------------------------------------ 5. architettura
pagina("Architettura", "Il tablet cattura e mostra; il lavoro pesante può stare in casa o su internet")
# tablet
c.setFillColor(HexColor("#E8F5E9")); c.setStrokeColor(VERDE); c.setLineWidth(2.2)
c.roundRect(M, 70, 400, 420, 12, fill=1, stroke=1)
c.setFillColor(VERDE); c.setFont("D-B", 14)
c.drawString(M + 14, 470, "TABLET  -  app Parla Facile")
riquadro(M + 20, 405, 360, 44, "Microfono", "16 kHz, blocchi da 100 ms", GRIGIO)
riquadro(M + 20, 330, 360, 50, "MotoreWhisper", "taglia le frasi alle pause (0,8 s) e le invia\nibrido: stessa cattura anche a Vosk", BLU)
riquadro(M + 20, 262, 170, 46, "MotoreVosk", "offline, sul tablet", VERDE)
riquadro(M + 210, 262, 170, 46, "MotoreRete", "servizio di sistema (opz.)", GRIGIO)
riquadro(M + 20, 170, 360, 60, "MainActivity", "sceglie il motore, mostra i sottotitoli\ncomandi vocali, chiosco, pannello nascosto", SCURO)
riquadro(M + 20, 90, 360, 50, "Correttore (facoltativo)", "punteggiatura e maiuscole via Ollama", ARANCIO)
freccia(M + 200, 405, M + 200, 381, SCURO)
freccia(M + 105, 330, M + 105, 309, SCURO, True)   # whisper -> vosk (ibrido)
freccia(M + 105, 262, M + 105, 231, SCURO)
freccia(M + 295, 262, M + 295, 231, SCURO)
freccia(M + 200, 170, M + 200, 141, SCURO)
c.setStrokeColor(SCURO); c.setLineWidth(1.8)
c.line(M + 20, 355, M + 9, 355); c.line(M + 9, 355, M + 9, 200)
freccia(M + 9, 200, M + 20, 200, SCURO)
# mondo esterno
X = 520
riquadro(X, 372, 278, 100, "PC DI CASA  :8000", "whisper_server.py (faster-whisper)\nparole.txt  -  sostituzioni.txt\nfiltro silenzi, modello small", BLU, HexColor("#E3F2FD"))
riquadro(X, 252, 278, 90, "INTERNET", "Groq / OpenAI (Whisper)\nGoogle Cloud Speech-to-Text", SCURO, HexColor("#ECEFF1"))
riquadro(X, 100, 278, 90, "PC DI CASA  :11435", "Ollama (qwen2.5:3b)\ncorregge punteggiatura e maiuscole", ARANCIO, HexColor("#FFF3E0"))
freccia(M + 380, 355, X, 410, BLU)
freccia(M + 380, 345, X, 300, SCURO)
freccia(M + 380, 125, X, 135, ARANCIO)
c.setFillColor(GRIGIO); c.setFont("D-I", 10)
c.drawString(X, 78, "Tratteggio: Vosk riceve lo stesso audio per il testo provvisorio.")

# ------------------------------------------------------------------ 6. flusso di una frase
pagina("Il percorso di una frase", "Dalla voce al sottotitolo in circa due secondi")
passi = [
    ("1  Voce", "L'app sente iniziare la voce (soglia che si adatta al rumore) e conserva 0,3 s prima.", BLU),
    ("2  Provvisorio", "Vosk scrive subito il testo in blu, mentre si parla (modalità ibrida).", VERDE),
    ("3  Pausa", "Dopo 0,8 s di silenzio la frase si chiude; si toglie il silenzio finale.", SCURO),
    ("4  Invio", "L'audio parte come WAV verso il server di casa o il servizio online.", BLU),
    ("5  Trascrizione", "Whisper trascrive favorendo le parole di parole.txt; poi si applicano le sostituzioni.", BLU),
    ("6  Correzione", "Facoltativa: Ollama sistema punteggiatura e maiuscole. Accettata solo se le parole sono identiche.", ARANCIO),
]
bw = (L - 2 * M - 5 * 14) / 6
for i, (t, d, col) in enumerate(passi):
    x = M + i * (bw + 14)
    c.setFillColor(white); c.setStrokeColor(col); c.setLineWidth(2)
    c.roundRect(x, 285, bw, 155, 8, fill=1, stroke=1)
    c.setFillColor(col); c.setFont("D-B", 11.5)
    c.drawString(x + 8, 418, t)
    testo(x + 8, 405, bw - 16, 150, d, ParagraphStyle("p", parent=piccolo, fontSize=9.6, leading=13))
    if i < 5:
        freccia(x + bw + 1, 362, x + bw + 13, 362, SCURO)
c.setFillColor(SCURO); c.setFont("D-B", 13)
c.drawString(M, 235, "Se qualcosa va storto")
punti(M, 223, L - 2 * M, 130, [
    "Una frase che il server non trascrive: resta il testo di Vosk, invece di perderla.",
    "Due errori di fila: l'ascolto passa a Vosk e un avviso compare a schermo.",
    "Una correzione che cambia le parole viene scartata: resta il testo di Whisper.",
], piccolo)

# ------------------------------------------------------------------ 7. server e parole
pagina("Il server di casa", "Whisper nel PC: parole e nomi personalizzati, tempi misurati")
testo(M, A - 100, 380, 20, "<b>Parole e nomi della famiglia</b>", corpo)
punti(M, A - 125, 380, 150, [
    "<b>parole.txt</b>: nomi e parole che Whisper deve riconoscere (nomi dei familiari, paesi, parole in dialetto).",
    "<b>sostituzioni.txt</b>: correzioni fisse, <i>Rosi = Rossi</i>, su parole intere.",
    "I file si rileggono da soli: niente riavvii.",
], piccolo)
testo(M, 365, 380, 20, "<b>Dialetto</b>", corpo)
testo(M, 345, 380, 120,
      "Whisper non è addestrato sui dialetti italiani: il glossario aiuta sulle parole sparse, non su "
      "frasi tutte in dialetto. Per quello servirebbe addestrare il modello sulla voce della persona.", piccolo)
# grafico a barre
gx, gy = 470, 150
c.setFillColor(SCURO); c.setFont("D-B", 13)
c.drawString(gx, A - 107, "Tempo per frase")
c.setFont("D", 10); c.drawString(gx, A - 121, "12 frasi reali, 2,3 s di audio in media")
dati = [("small\n(attuale)", 1.40, VERDE), ("small\nsenza filtro", 2.09, ARANCIO), ("medium", 3.65, ROSSO)]
massimo = 4.0
for i, (n, v, col) in enumerate(dati):
    x = gx + 10 + i * 118
    h = 270 * v / massimo
    c.setFillColor(col)
    c.rect(x, gy + 38, 80, h, fill=1, stroke=0)
    c.setFillColor(SCURO); c.setFont("D-B", 12)
    c.drawCentredString(x + 40, gy + 44 + h, f"{v:.2f} s".replace(".", ","))
    c.setFont("D", 9.5)
    for k, riga in enumerate(n.split("\n")):
        c.drawCentredString(x + 40, gy + 22 - k * 11, riga)
c.setStrokeColor(GRIGIO); c.setLineWidth(1)
c.line(gx, gy + 38, gx + 370, gy + 38)
testo(gx, gy - 4, 360, 40,
      "PC senza scheda grafica (12 core). 'Senza filtro' fa anche inventare frasi sul rumore. "
      "<b>medium</b> sbaglia meno sui nomi.", ParagraphStyle("n", parent=piccolo, fontSize=9, leading=12))

# ------------------------------------------------------------------ 8. pannello e privacy
pagina("Configurazione e privacy", "Tutto si regola dal tablet, in un pannello nascosto")
c.setFillColor(white); c.setStrokeColor(SCURO); c.setLineWidth(2)
c.roundRect(M, 215, 380, 250, 10, fill=1, stroke=1)
c.setFillColor(SCURO); c.setFont("D-B", 15)
c.drawString(M + 16, 438, "5 tocchi sulla barra della batteria")
punti(M + 16, 425, 348, 300, [
    "<b>Motore in uso ora</b>",
    "Modo: automatico, solo locale, solo Whisper, solo sistema",
    "Indirizzo, chiave e modello Whisper; <i>Compila per Groq / Google Cloud</i>; <i>Prova connessione</i>",
    "Casella ibrido (Vosk subito)",
    "Correzione con AI: indirizzo e modello Ollama",
    "Impostazioni Android, uscita dal chiosco",
], piccolo)
c.setFillColor(ROSSO); c.setFont("D-B", 15)
c.drawString(470, 465, "Da sapere sulla privacy")
punti(470, 452, 330, 340, [
    "Con il <b>server di casa</b> l'audio non esce dalla rete locale.",
    "Con <b>Groq, OpenAI o Google</b> l'audio delle conversazioni va su un server esterno.",
    "Il traffico verso il PC di casa è in chiaro e il server non ha password: è raggiungibile da tutta la Wi-Fi di casa.",
    "La chiave API è salvata in chiaro sul tablet.",
    "Le registrazioni si conservano solo se si attiva <b>--salva</b>, per i test.",
], piccolo)

# ------------------------------------------------------------------ 9. stato e prossimi passi
pagina("Stato e prossimi passi", "Cosa funziona, cosa è ancora da provare")
tabella(M, A - 100, [190, 170, 394], [
    ["Parte", "Stato", "Dettaglio"],
    ["Vosk offline", "<font color='#1B7F3B'><b>In uso</b></font>", "Sempre disponibile sul tablet."],
    ["Server Whisper di casa", "<font color='#1B7F3B'><b>Provato</b></font>", "Frasi reali dal tablet, 1,4-1,8 s ciascuna."],
    ["Ibrido Vosk + Whisper", "<font color='#C25E00'><b>Da riprovare</b></font>", "Testo provvisorio visto nel log; da ricontrollare dopo l'ultimo riavvio."],
    ["Correzione Ollama", "<font color='#C25E00'><b>Parziale</b></font>", "Richieste in 0,6-0,8 s; non misurato quante correzioni vengono accettate."],
    ["Groq", "<font color='#B3261E'><b>Non verificato</b></font>", "Chiave accettata, ma nessuna trascrizione completata."],
    ["Google Cloud", "<font color='#B3261E'><b>Mai provato</b></font>", "Codice scritto, manca la chiave."],
], True)
c.setFillColor(SCURO); c.setFont("D-B", 14)
c.drawString(M, 215, "Prossimi passi")
punti(M, 202, L - 2 * M, 150, [
    "Scheda grafica NVIDIA nel PC: Whisper più grande e frasi in meno di un secondo.",
    "Avvio automatico del server e di Ollama all'accensione del PC.",
    "Soglia di voce e pausa regolabili dal pannello; password e cifratura verso il server.",
    "Salvare le conversazioni (con il consenso della famiglia) per migliorare il riconoscimento del dialetto.",
], piccolo)

c.save()
print("scritto", os.path.abspath(USCITA))
