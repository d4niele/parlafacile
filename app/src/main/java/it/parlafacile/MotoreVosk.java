package it.parlafacile;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

/**
 * Riconoscimento vocale completamente offline con Vosk
 * (modello italiano incluso nell'app).
 */
public class MotoreVosk implements MotoreAscolto, RecognitionListener {

    private static final float FREQUENZA = 16000.0f;

    private final Model modello;
    private final Ascoltatore ascoltatore;
    private Recognizer riconoscitore;
    private SpeechService servizio;

    public MotoreVosk(Model modello, Ascoltatore ascoltatore) {
        this.modello = modello;
        this.ascoltatore = ascoltatore;
    }

    @Override
    public void avvia() {
        if (servizio != null) return;
        try {
            if (riconoscitore == null) riconoscitore = new Recognizer(modello, FREQUENZA);
            servizio = new SpeechService(riconoscitore, FREQUENZA);
            servizio.startListening(this);
        } catch (Exception e) {
            servizio = null;
            ascoltatore.errore("Microfono non disponibile: " + e.getMessage(), false);
        }
    }

    @Override
    public void ferma() {
        if (servizio != null) {
            servizio.stop();
            servizio.shutdown();
            servizio = null;
        }
    }

    @Override
    public void chiudi() {
        ferma();
        if (riconoscitore != null) {
            riconoscitore.close();
            riconoscitore = null;
        }
    }

    @Override
    public boolean attivo() { return servizio != null; }

    @Override
    public String nome() { return "locale"; }

    // ---- RecognitionListener (Vosk chiama questi metodi sul thread principale)

    @Override
    public void onPartialResult(String json) {
        String t = leggi(json, "partial");
        if (!t.isEmpty()) ascoltatore.parziale(t);
    }

    @Override
    public void onResult(String json) {
        String t = leggi(json, "text");
        if (!t.isEmpty()) ascoltatore.finale(t);
    }

    @Override
    public void onFinalResult(String json) {
        onResult(json);
    }

    @Override
    public void onError(Exception e) {
        ascoltatore.errore("Errore riconoscimento locale: " + e.getMessage(), false);
    }

    @Override
    public void onTimeout() { }

    private static String leggi(String json, String campo) {
        try {
            return new JSONObject(json).optString(campo, "").trim();
        } catch (Exception e) {
            return "";
        }
    }
}
