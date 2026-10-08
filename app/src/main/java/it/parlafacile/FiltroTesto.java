package it.parlafacile;

import java.text.Normalizer;
import java.util.Locale;

/** Regole sul testo riconosciuto: allucinazioni di Whisper, confronto delle parole, comandi vocali. */
public final class FiltroTesto {
    private FiltroTesto() { }

    public enum Comando { NESSUNO, CANCELLA, PIU_GRANDE, PIU_PICCOLO }

    /** Frasi che Whisper inventa sui rumori e sui silenzi. */
    private static final String[] ALLUCINAZIONI = {
            "sottotitoli", "amara.org", "grazie per la visione", "grazie a tutti",
            "iscriviti", "alla prossima", "www."
    };

    public static boolean allucinazione(String testo) {
        String t = testo.toLowerCase(Locale.ITALIAN);
        for (String a : ALLUCINAZIONI) if (t.contains(a)) return true;
        return false;
    }

    /** Parole in minuscolo, senza punteggiatura, separate da uno spazio. */
    public static String parole(String t) {
        return t.toLowerCase(Locale.ITALIAN).replaceAll("[^\\p{L}\\p{N} ]", " ").trim().replaceAll("\\s+", " ");
    }

    /** true se i due testi hanno le stesse parole nello stesso ordine (cambia solo la punteggiatura). */
    public static boolean stesseParole(String a, String b) {
        return parole(a).equals(parole(b));
    }

    /** Comandi vocali: devono essere l'unica cosa detta nella frase. */
    public static Comando comando(String testo) {
        String t = Normalizer.normalize(testo.toLowerCase(Locale.ITALIAN), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z ]", "")
                .trim();
        if (t.equals("cancella") || t.equals("cancella tutto") || t.equals("pulisci")) return Comando.CANCELLA;
        if (t.equals("piu grande") || t.equals("ingrandisci") || t.equals("scrivi piu grande")) return Comando.PIU_GRANDE;
        if (t.equals("piu piccolo") || t.equals("rimpicciolisci") || t.equals("scrivi piu piccolo")) return Comando.PIU_PICCOLO;
        return Comando.NESSUNO;
    }
}
