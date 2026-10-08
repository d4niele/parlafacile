"""Parole da favorire e correzioni automatiche (parole.txt, sostituzioni.txt)."""
import os
import re


class Glossario:
    """Legge i due file di testo da una cartella e li rilegge quando cambiano."""

    def __init__(self, cartella):
        self.cartella = cartella
        self._cache = {}

    def _righe(self, nome):
        percorso = os.path.join(self.cartella, nome)
        try:
            mtime = os.stat(percorso).st_mtime_ns
        except OSError:
            return []
        if self._cache.get(nome, (None,))[0] != mtime:
            with open(percorso, encoding="utf-8") as f:
                righe = [r.strip() for r in f if r.strip() and not r.lstrip().startswith("#")]
            self._cache[nome] = (mtime, righe)
        return self._cache[nome][1]

    def parole(self):
        return self._righe("parole.txt")

    def sostituzioni(self):
        coppie = []
        for riga in self._righe("sostituzioni.txt"):
            if "=" in riga:
                sbagliato, giusto = riga.split("=", 1)
                if sbagliato.strip():
                    coppie.append((sbagliato.strip(), giusto.strip()))
        return sorted(coppie, key=lambda c: -len(c[0]))   # prima le più lunghe

    def correggi(self, testo):
        for sbagliato, giusto in self.sostituzioni():
            testo = re.sub(r"(?<!\w)" + re.escape(sbagliato) + r"(?!\w)", lambda _, g=giusto: g,
                           testo, flags=re.IGNORECASE)
        return testo
