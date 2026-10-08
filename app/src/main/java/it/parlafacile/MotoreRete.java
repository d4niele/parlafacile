package it.parlafacile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * Riconoscimento vocale del sistema (di solito Google, online, più preciso).
 * Il servizio di sistema si ferma dopo ogni frase: qui lo si riavvia
 * di continuo finché l'utente non preme STOP.
 */
public class MotoreRete implements MotoreAscolto, RecognitionListener {

    private final Context contesto;
    private final Ascoltatore ascoltatore;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer riconoscitore;
    private boolean attivo = false;
    private int erroriDiRete = 0;

    public MotoreRete(Context contesto, Ascoltatore ascoltatore) {
        this.contesto = contesto;
        this.ascoltatore = ascoltatore;
    }

    /** true se sul tablet c'è un servizio di riconoscimento vocale di sistema. */
    public static boolean disponibile(Context c) {
        return SpeechRecognizer.isRecognitionAvailable(c);
    }

    @Override
    public void avvia() {
        if (attivo) return;
        attivo = true;
        erroriDiRete = 0;
        if (riconoscitore == null) {
            riconoscitore = SpeechRecognizer.createSpeechRecognizer(contesto);
            riconoscitore.setRecognitionListener(this);
        }
        ascolta();
    }

    private void ascolta() {
        if (!attivo || riconoscitore == null) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, contesto.getPackageName());
        // Pause lunghe: le persone anziane parlano con calma
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        try {
            riconoscitore.startListening(i);
        } catch (Exception e) {
            attivo = false;
            ascoltatore.errore("Riconoscimento di rete non avviabile", false);
        }
    }

    private void riavviaTra(long ms) {
        handler.postDelayed(new Runnable() {
            @Override public void run() { ascolta(); }
        }, ms);
    }

    @Override
    public void ferma() {
        attivo = false;
        handler.removeCallbacksAndMessages(null);
        if (riconoscitore != null) {
            try { riconoscitore.cancel(); } catch (Exception ignored) { }
        }
    }

    @Override
    public void chiudi() {
        ferma();
        if (riconoscitore != null) {
            riconoscitore.destroy();
            riconoscitore = null;
        }
    }

    @Override
    public boolean attivo() { return attivo; }

    @Override
    public String nome() { return "rete"; }

    // ---- RecognitionListener di Android

    @Override
    public void onPartialResults(Bundle b) {
        String t = primo(b);
        if (!t.isEmpty()) ascoltatore.parziale(t);
    }

    @Override
    public void onResults(Bundle b) {
        erroriDiRete = 0;
        String t = primo(b);
        if (!t.isEmpty()) ascoltatore.finale(t);
        riavviaTra(100);
    }

    @Override
    public void onError(int codice) {
        if (!attivo) return;
        switch (codice) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                riavviaTra(100);          // silenzio: normale, si riascolta
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
            case SpeechRecognizer.ERROR_CLIENT:
                riavviaTra(500);
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_SERVER:
                erroriDiRete++;
                if (erroriDiRete >= 2) {
                    attivo = false;
                    ascoltatore.errore("Rete non disponibile", false);
                } else {
                    riavviaTra(1000);
                }
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                attivo = false;
                ascoltatore.errore("Manca il permesso del microfono", false);
                break;
            default:
                erroriDiRete++;
                if (erroriDiRete >= 3) {
                    attivo = false;
                    ascoltatore.errore("Riconoscimento di rete in errore (" + codice + ")", false);
                } else {
                    riavviaTra(1000);
                }
        }
    }

    private static String primo(Bundle b) {
        if (b == null) return "";
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (r == null || r.isEmpty() || r.get(0) == null) ? "" : r.get(0).trim();
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onEvent(int eventType, Bundle params) { }
}
