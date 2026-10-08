package it.parlafacile;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Passa il testo riconosciuto a un piccolo modello di linguaggio (es. Ollama sul PC di casa,
 * API compatibile con /api/chat) per sistemare punteggiatura, maiuscole e
 * parole palesemente sbagliate. Il testo grezzo si vede subito; la versione corretta lo
 * sostituisce quando arriva. Se qualcosa va storto resta il testo originale.
 */
public class Correttore {

    public interface Esito {
        void corretto(String originale, String corretto);
    }

    private static final String ISTRUZIONI =
            "Sei un correttore di sottotitoli in italiano. Il testo viene da un riconoscimento vocale. "
            + "Correggi solo punteggiatura e maiuscole. Non cambiare, aggiungere o togliere parole. "
            + "Rispondi solo con il testo corretto.";

    /** Parametri leggeri: contesto corto e pochi thread, per non rubare CPU a Whisper. */
    private static final int CONTESTO = 1024;
    private static final int THREAD = 6;

    private final String indirizzo;
    private final String modello;
    private final Handler principale = new Handler(Looper.getMainLooper());
    private final ExecutorService coda = Executors.newSingleThreadExecutor();

    public Correttore(String indirizzo, String modello) {
        this.indirizzo = Indirizzi.ollama(indirizzo);
        this.modello = (modello == null || modello.trim().isEmpty()) ? "qwen2.5:3b" : modello.trim();
    }

    public boolean attivo() { return !indirizzo.isEmpty(); }

    public void chiudi() { coda.shutdownNow(); }

    /** Frasi di meno di tre parole non si correggono: non ne vale la pena. */
    public void correggi(final String testo, final Esito esito) {
        if (!attivo() || testo.trim().split("\\s+").length < 3) return;
        coda.execute(new Runnable() {
            @Override public void run() {
                try {
                    final String r = chiedi(testo);
                    if (r.isEmpty() || r.equals(testo)) return;
                    // Si accetta solo se le parole sono identiche: cambia la punteggiatura, non il senso
                    if (!FiltroTesto.stesseParole(r, testo)) return;
                    principale.post(new Runnable() {
                        @Override public void run() { esito.corretto(testo, r); }
                    });
                } catch (Exception ignored) { }
            }
        });
    }

    private String chiedi(String testo) throws Exception {
        // Risposta al massimo poco più lunga della frase (circa 1 token ogni 2 caratteri)
        int massimo = Math.min(200, testo.length() / 2 + 24);
        JSONObject corpo = new JSONObject()
                .put("model", modello)
                .put("stream", false)
                .put("keep_alive", "30m")
                .put("options", new JSONObject()
                        .put("temperature", 0)
                        .put("num_ctx", CONTESTO)
                        .put("num_predict", massimo)
                        .put("num_thread", THREAD))
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", ISTRUZIONI))
                        .put(new JSONObject().put("role", "user").put("content", testo)));
        HttpURLConnection c = (HttpURLConnection) new URL(indirizzo).openConnection();
        c.setConnectTimeout(2500);
        c.setReadTimeout(10000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.getOutputStream().write(corpo.toString().getBytes("UTF-8"));
        int codice = c.getResponseCode();
        InputStream in = codice == 200 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream r = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) r.write(buf, 0, n);
            in.close();
        }
        if (codice != 200) throw new java.io.IOException("HTTP " + codice);
        return new JSONObject(r.toString("UTF-8")).getJSONObject("message")
                .optString("content", "").trim();
    }
}
