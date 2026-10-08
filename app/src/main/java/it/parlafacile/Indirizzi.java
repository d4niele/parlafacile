package it.parlafacile;

/** Trasforma quello che si scrive nel pannello ("192.168.1.20:8000") in un indirizzo completo. */
public final class Indirizzi {
    private Indirizzi() { }

    public static final String PERCORSO_WHISPER = "/v1/audio/transcriptions";
    public static final String PERCORSO_OLLAMA = "/api/chat";

    /** Server di trascrizione: se manca il percorso si aggiunge quello compatibile con OpenAI. */
    public static String whisper(String testo) {
        return completa(testo, PERCORSO_WHISPER);
    }

    /** Server Ollama: se manca il percorso si aggiunge quello della chat. */
    public static String ollama(String testo) {
        return completa(testo, PERCORSO_OLLAMA);
    }

    /** true se l'indirizzo è quello di Google Cloud Speech-to-Text. */
    public static boolean èGoogle(String url) {
        return url != null && url.contains("speech.googleapis.com");
    }

    static String completa(String testo, String percorso) {
        String t = testo == null ? "" : testo.trim();
        if (t.isEmpty()) return "";
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "http://" + t;
        String senzaProtocollo = t.substring(t.indexOf("//") + 2);
        if (!senzaProtocollo.contains("/")) t += percorso;
        return t;
    }
}
