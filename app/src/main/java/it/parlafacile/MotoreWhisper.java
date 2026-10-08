package it.parlafacile;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Riconoscimento vocale con un server Whisper nella rete di casa
 * (vedi server/whisper_server.py; va bene anche whisper.cpp "server" o
 * qualunque server compatibile con /v1/audio/transcriptions di OpenAI).
 *
 * Il tablet registra dal microfono, taglia l'audio a ogni pausa nel parlato
 * e manda ogni frase al server. Whisper non dà testo provvisorio: mentre la
 * frase viene riconosciuta si vede solo "…".
 */
public class MotoreWhisper implements MotoreAscolto {

    private static final int FREQUENZA = 16000;
    private static final int BLOCCO = FREQUENZA / 10;               // 100 ms
    private static final int PREROLL_BLOCCHI = 3;                   // 300 ms prima della voce
    private static final int SILENZIO_FINE_BLOCCHI = 8;             // 0,8 s di pausa = frase finita
    private static final int MIN_VOCE_BLOCCHI = 4;                  // sotto 0,4 s è un rumore
    private static final int MAX_FRASE_BLOCCHI = 250;               // 25 s: taglio forzato
    private static final double SOGLIA_MINIMA = 350;                // ampiezza RMS (su 32768)
    private static final int ERRORI_MASSIMI = 2;

    /** Frasi che Whisper inventa sui rumori/silenzi: si scartano. */
    private static final String[] ALLUCINAZIONI = {
            "sottotitoli", "amara.org", "grazie per la visione", "grazie a tutti",
            "iscriviti", "alla prossima", "www."
    };

    private final String indirizzo;
    private final String chiave;
    private final String modelloNome;
    private volatile Model vosk;   // se non è null: testo subito con Vosk, poi corretto da Whisper
    private final Ascoltatore ascoltatore;
    private final Handler principale = new Handler(Looper.getMainLooper());
    private final ExecutorService rete = Executors.newSingleThreadExecutor();

    private volatile boolean attivo = false;
    private volatile int sessione = 0;
    private volatile int errori = 0;
    private Thread registrazione;

    public MotoreWhisper(String indirizzo, String chiave, String modello, Model vosk, Ascoltatore ascoltatore) {
        this.vosk = vosk;
        this.indirizzo = indirizzo;
        this.chiave = chiave == null ? "" : chiave.trim();
        this.modelloNome = (modello == null || modello.trim().isEmpty()) ? "whisper-1" : modello.trim();
        this.ascoltatore = ascoltatore;
    }

    /** "192.168.1.20:8000" oppure un indirizzo completo → URL del servizio. */
    public static String urlCompleto(String testo) {
        String t = testo == null ? "" : testo.trim();
        if (t.isEmpty()) return "";
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "http://" + t;
        String senzaProtocollo = t.substring(t.indexOf("//") + 2);
        if (!senzaProtocollo.contains("/")) t += "/v1/audio/transcriptions";
        return t;
    }

    /** Il modello Vosk può finire di caricarsi dopo l'avvio: si aggancia alla frase successiva. */
    public void impostaVosk(Model m) { vosk = m; }

    @Override
    public void avvia() {
        if (attivo) return;
        attivo = true;
        errori = 0;
        final int mia = ++sessione;
        registrazione = new Thread(new Runnable() {
            @Override public void run() { registra(mia); }
        }, "whisper-microfono");
        registrazione.start();
    }

    @Override
    public void ferma() {
        attivo = false;
        sessione++;   // scarta i risultati ancora in volo
        if (registrazione != null) {
            try { registrazione.join(500); } catch (InterruptedException ignored) { }
            registrazione = null;
        }
        principale.removeCallbacksAndMessages(null);
    }

    @Override
    public void chiudi() {
        ferma();
        rete.shutdownNow();
    }

    @Override
    public boolean attivo() { return attivo; }

    @Override
    public String nome() { return "whisper"; }

    // ================================================================== microfono

    private void registra(int mia) {
        if (!serverRaggiungibile()) {
            fallisci(mia, "Server Whisper non raggiungibile");
            return;
        }
        int minimo = AudioRecord.getMinBufferSize(FREQUENZA,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord mic = null;
        Recognizer rapido = null;
        try {
            mic = new AudioRecord(MediaRecorder.AudioSource.MIC, FREQUENZA,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minimo, BLOCCO * 2 * 4));
            if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
                fallisci(mia, "Microfono non disponibile");
                return;
            }
            mic.startRecording();
            if (vosk != null) rapido = new Recognizer(vosk, FREQUENZA);
            android.util.Log.d("PF", "whisper: vosk " + (rapido != null ? "attivo" : "ASSENTE (modello null)"));
            StringBuilder voskTesto = new StringBuilder();

            short[] blocco = new short[BLOCCO];
            short[][] anello = new short[PREROLL_BLOCCHI][];
            int posAnello = 0, nAnello = 0;
            ByteArrayOutputStream frase = new ByteArrayOutputStream();
            boolean inVoce = false;
            int blocchiVoce = 0, blocchiSilenzio = 0, blocchiFrase = 0;
            double rumore = 200;   // livello del fruscio di fondo, si adatta

            while (attivo && mia == sessione) {
                int letti = mic.read(blocco, 0, BLOCCO);
                if (letti <= 0) {
                    if (letti < 0) { fallisci(mia, "Errore del microfono"); return; }
                    continue;
                }
                double rms = rms(blocco, letti);
                double soglia = Math.max(SOGLIA_MINIMA, rumore * 2.5);
                boolean voce = rms > soglia;

                if (!inVoce) {
                    if (!voce) {
                        rumore = rumore * 0.95 + rms * 0.05;
                        anello[posAnello] = java.util.Arrays.copyOf(blocco, letti);
                        posAnello = (posAnello + 1) % PREROLL_BLOCCHI;
                        nAnello = Math.min(nAnello + 1, PREROLL_BLOCCHI);
                    } else {
                        inVoce = true;
                        frase.reset();
                        int primo = (posAnello - nAnello + PREROLL_BLOCCHI) % PREROLL_BLOCCHI;
                        for (int i = 0; i < nAnello; i++) {
                            scrivi(frase, anello[(primo + i) % PREROLL_BLOCCHI], anello[(primo + i) % PREROLL_BLOCCHI].length);
                        }
                        nAnello = 0;
                        blocchiVoce = 1; blocchiSilenzio = 0; blocchiFrase = 1;
                        scrivi(frase, blocco, letti);
                        voskTesto.setLength(0);
                        if (rapido == null && vosk != null) {
                            try { rapido = new Recognizer(vosk, FREQUENZA); } catch (Exception ignored) { }
                        }
                        if (rapido != null) { rapido.reset(); alimenta(mia, rapido, blocco, letti, voskTesto); }
                        else segnala(mia, "…");
                    }
                } else {
                    scrivi(frase, blocco, letti);
                    if (rapido != null) alimenta(mia, rapido, blocco, letti, voskTesto);
                    blocchiFrase++;
                    if (voce) { blocchiVoce++; blocchiSilenzio = 0; }
                    else blocchiSilenzio++;

                    if (blocchiSilenzio >= SILENZIO_FINE_BLOCCHI || blocchiFrase >= MAX_FRASE_BLOCCHI) {
                        if (blocchiVoce >= MIN_VOCE_BLOCCHI) {
                            if (rapido != null) accoda(voskTesto, testoDi(rapido.getFinalResult(), "text"));
                            byte[] pcm = frase.toByteArray();
                            // Il silenzio finale non serve a Whisper: via, tenendo 0,3 s
                            int tolti = Math.max(0, blocchiSilenzio - 3) * BLOCCO * 2;
                            manda(mia, java.util.Arrays.copyOf(pcm, Math.max(0, pcm.length - tolti)),
                                    voskTesto.toString().trim());
                        }
                        else segnala(mia, "");
                        inVoce = false;
                    }
                }
            }
        } catch (Exception e) {
            fallisci(mia, "Microfono: " + e.getMessage());
        } finally {
            if (rapido != null) rapido.close();
            if (mic != null) {
                try { mic.stop(); } catch (Exception ignored) { }
                mic.release();
            }
        }
    }

    /** Dà l'audio a Vosk e mostra subito il testo provvisorio. */
    private void alimenta(int mia, Recognizer r, short[] b, int n, StringBuilder acc) {
        if (r.acceptWaveForm(b, n)) {
            accoda(acc, testoDi(r.getResult(), "text"));
            segnala(mia, acc.toString().trim());
        } else {
            String p = testoDi(r.getPartialResult(), "partial");
            if (!p.isEmpty()) android.util.Log.d("PF", "vosk parziale: " + p);
            segnala(mia, (acc + " " + p).trim());
        }
    }

    private static void accoda(StringBuilder acc, String t) {
        if (!t.isEmpty()) acc.append(acc.length() > 0 ? " " : "").append(t);
    }

    private static String testoDi(String json, String campo) {
        try { return new JSONObject(json).optString(campo, "").trim(); }
        catch (Exception e) { return ""; }
    }

    private static double rms(short[] b, int n) {
        double somma = 0;
        for (int i = 0; i < n; i++) somma += (double) b[i] * b[i];
        return Math.sqrt(somma / n);
    }

    private static void scrivi(ByteArrayOutputStream o, short[] b, int n) {
        for (int i = 0; i < n; i++) {
            o.write(b[i] & 0xFF);
            o.write((b[i] >> 8) & 0xFF);
        }
    }

    // ================================================================== server

    private boolean serverRaggiungibile() { return raggiungibile(indirizzo); }

    /** Prova di connessione (da chiamare fuori dal thread principale). */
    public static boolean raggiungibile(String url) {
        try {
            URL u = new URL(url);
            int porta = u.getPort() != -1 ? u.getPort() : u.getDefaultPort();
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(u.getHost(), porta), 2500);
                return true;
            } finally {
                s.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void manda(final int mia, final byte[] pcm, final String provvisorio) {
        rete.execute(new Runnable() {
            @Override public void run() {
                if (mia != sessione) return;
                try {
                    String testo = trascrivi(pcm);
                    errori = 0;
                    if (mia != sessione) return;
                    segnala(mia, "");
                    if (!testo.isEmpty() && !allucinazione(testo)) finale(mia, testo);
                } catch (Exception e) {
                    android.util.Log.d("PF", "errore invio a Whisper: " + e);
                    segnala(mia, "");
                    if (!provvisorio.isEmpty()) finale(mia, provvisorio);   // meglio Vosk che niente
                    if (++errori >= ERRORI_MASSIMI) fallisci(mia, "Server Whisper non risponde");
                }
            }
        });
    }

    private boolean èGoogle() { return indirizzo.contains("speech.googleapis.com"); }

    /** Google Cloud Speech-to-Text (v1): JSON con l'audio in base64, chiave nell'indirizzo. */
    private String trascriviGoogle(byte[] pcm) throws Exception {
        String url = indirizzo + (indirizzo.contains("?") ? "&" : "?") + "key=" + java.net.URLEncoder.encode(chiave, "UTF-8");
        JSONObject config = new JSONObject()
                .put("encoding", "LINEAR16").put("sampleRateHertz", FREQUENZA)
                .put("languageCode", "it-IT").put("enableAutomaticPunctuation", true);
        if (!modelloNome.equals("whisper-1")) config.put("model", modelloNome);
        JSONObject corpo = new JSONObject().put("config", config).put("audio", new JSONObject()
                .put("content", android.util.Base64.encodeToString(pcm, android.util.Base64.NO_WRAP)));

        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.getOutputStream().write(corpo.toString().getBytes("UTF-8"));
        int codice = c.getResponseCode();
        InputStream in = codice == 200 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream risposta = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) risposta.write(buf, 0, n);
            in.close();
        }
        if (codice != 200) throw new java.io.IOException("HTTP " + codice);
        org.json.JSONArray risultati = new JSONObject(risposta.toString("UTF-8")).optJSONArray("results");
        StringBuilder testo = new StringBuilder();
        for (int i = 0; risultati != null && i < risultati.length(); i++) {
            org.json.JSONArray alt = risultati.getJSONObject(i).optJSONArray("alternatives");
            if (alt != null && alt.length() > 0) {
                testo.append(testo.length() > 0 ? " " : "").append(alt.getJSONObject(0).optString("transcript", "").trim());
            }
        }
        return testo.toString();
    }

    private String trascrivi(byte[] pcm) throws Exception {
        if (èGoogle()) return trascriviGoogle(pcm);
        String confine = "----parlafacile" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(indirizzo).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + confine);
        if (!chiave.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + chiave);
        try {
            OutputStream o = c.getOutputStream();
            campo(o, confine, "model", modelloNome);
            campo(o, confine, "language", "it");
            campo(o, confine, "response_format", "json");
            campo(o, confine, "temperature", "0");
            o.write(("--" + confine + "\r\nContent-Disposition: form-data; name=\"file\"; "
                    + "filename=\"frase.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes("UTF-8"));
            o.write(intestazioneWav(pcm.length));
            o.write(pcm);
            o.write(("\r\n--" + confine + "--\r\n").getBytes("UTF-8"));
            o.flush();

            int codice = c.getResponseCode();
            // La risposta va letta tutta: così la connessione resta aperta (keep-alive)
            // e la frase dopo non rifà la trattativa TLS, lenta su questo tablet.
            InputStream in = codice == 200 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream risposta = new ByteArrayOutputStream();
            if (in != null) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) risposta.write(buf, 0, n);
                in.close();
            }
            if (codice != 200) throw new java.io.IOException("HTTP " + codice);
            return new JSONObject(risposta.toString("UTF-8")).optString("text", "").trim();
        } finally {
            // niente disconnect(): chiuderebbe la connessione riutilizzabile
        }
    }

    private static void campo(OutputStream o, String confine, String nome, String valore) throws Exception {
        o.write(("--" + confine + "\r\nContent-Disposition: form-data; name=\"" + nome
                + "\"\r\n\r\n" + valore + "\r\n").getBytes("UTF-8"));
    }

    private static byte[] intestazioneWav(int byteDati) {
        int lunghezza = byteDati + 36;
        int byteSec = FREQUENZA * 2;
        byte[] h = new byte[44];
        System.arraycopy("RIFF".getBytes(), 0, h, 0, 4);
        int4(h, 4, lunghezza);
        System.arraycopy("WAVEfmt ".getBytes(), 0, h, 8, 8);
        int4(h, 16, 16);
        h[20] = 1;                      // PCM
        h[22] = 1;                      // mono
        int4(h, 24, FREQUENZA);
        int4(h, 28, byteSec);
        h[32] = 2;                      // byte per campione
        h[34] = 16;                     // bit
        System.arraycopy("data".getBytes(), 0, h, 36, 4);
        int4(h, 40, byteDati);
        return h;
    }

    private static void int4(byte[] b, int pos, int v) {
        b[pos] = (byte) v;
        b[pos + 1] = (byte) (v >> 8);
        b[pos + 2] = (byte) (v >> 16);
        b[pos + 3] = (byte) (v >> 24);
    }

    private static boolean allucinazione(String testo) {
        String t = testo.toLowerCase(java.util.Locale.ITALIAN);
        for (String a : ALLUCINAZIONI) if (t.contains(a)) return true;
        return false;
    }

    // ================================================================== verso l'app (thread principale)

    private void segnala(final int mia, final String testo) {
        principale.post(new Runnable() {
            @Override public void run() { if (mia == sessione && attivo) ascoltatore.parziale(testo); }
        });
    }

    private void finale(final int mia, final String testo) {
        principale.post(new Runnable() {
            @Override public void run() { if (mia == sessione && attivo) ascoltatore.finale(testo); }
        });
    }

    private void fallisci(final int mia, final String messaggio) {
        principale.post(new Runnable() {
            @Override public void run() {
                if (mia != sessione) return;
                attivo = false;
                ascoltatore.errore(messaggio, false);
            }
        });
    }
}
