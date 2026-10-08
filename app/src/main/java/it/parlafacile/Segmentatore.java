package it.parlafacile;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Taglia il flusso del microfono in frasi, ascoltando le pause (rilevamento della voce per energia).
 * Logica pura, senza Android: si prova con i test sulla JVM.
 *
 * Si alimenta a blocchi da {@link #BLOCCO} campioni (100 ms a 16 kHz).
 */
public class Segmentatore {

    public enum Evento { NIENTE, VOCE_INIZIATA, FRASE_CHIUSA, FRASE_SCARTATA }

    public static final int FREQUENZA = 16000;
    public static final int BLOCCO = FREQUENZA / 10;               // 100 ms
    static final int PREROLL_BLOCCHI = 3;                          // 300 ms prima della voce
    static final int SILENZIO_FINE_BLOCCHI = 8;                    // 0,8 s di pausa = frase finita
    static final int SILENZIO_TENUTO_BLOCCHI = 3;                  // del silenzio finale se ne tiene 0,3 s
    static final int MIN_VOCE_BLOCCHI = 4;                         // sotto 0,4 s è un rumore
    static final int MAX_FRASE_BLOCCHI = 250;                      // 25 s: taglio forzato
    static final double SOGLIA_MINIMA = 350;                       // ampiezza RMS (su 32768)
    static final double FATTORE_RUMORE = 2.5;                      // voce = più di 2,5 volte il fruscio

    private final short[][] anello = new short[PREROLL_BLOCCHI][];
    private int posAnello = 0, nAnello = 0;
    private final ByteArrayOutputStream frase = new ByteArrayOutputStream();
    private boolean inVoce = false;
    private int blocchiVoce, blocchiSilenzio, blocchiFrase;
    private double rumore = 200;   // livello del fruscio di fondo, si adatta nei silenzi
    private byte[] ultimaFrase = new byte[0];

    /** true se una frase è in corso (prima di questo blocco o iniziata con esso). */
    public boolean inVoce() { return inVoce; }

    /** PCM 16 bit little endian dell'ultima frase chiusa (con 0,3 s di silenzio finale al massimo). */
    public byte[] frase() { return ultimaFrase; }

    public Evento alimenta(short[] blocco, int n) {
        boolean voce = rms(blocco, n) > Math.max(SOGLIA_MINIMA, rumore * FATTORE_RUMORE);

        if (!inVoce) {
            if (!voce) {
                rumore = rumore * 0.95 + rms(blocco, n) * 0.05;
                anello[posAnello] = Arrays.copyOf(blocco, n);
                posAnello = (posAnello + 1) % PREROLL_BLOCCHI;
                nAnello = Math.min(nAnello + 1, PREROLL_BLOCCHI);
                return Evento.NIENTE;
            }
            inVoce = true;
            frase.reset();
            int primo = (posAnello - nAnello + PREROLL_BLOCCHI) % PREROLL_BLOCCHI;
            for (int i = 0; i < nAnello; i++) {
                short[] b = anello[(primo + i) % PREROLL_BLOCCHI];
                scrivi(b, b.length);
            }
            nAnello = 0;
            blocchiVoce = 1;
            blocchiSilenzio = 0;
            blocchiFrase = 1;
            scrivi(blocco, n);
            return Evento.VOCE_INIZIATA;
        }

        scrivi(blocco, n);
        blocchiFrase++;
        if (voce) { blocchiVoce++; blocchiSilenzio = 0; }
        else blocchiSilenzio++;

        if (blocchiSilenzio < SILENZIO_FINE_BLOCCHI && blocchiFrase < MAX_FRASE_BLOCCHI) return Evento.NIENTE;

        inVoce = false;
        if (blocchiVoce < MIN_VOCE_BLOCCHI) return Evento.FRASE_SCARTATA;
        byte[] pcm = frase.toByteArray();
        int tolti = Math.max(0, blocchiSilenzio - SILENZIO_TENUTO_BLOCCHI) * BLOCCO * 2;
        ultimaFrase = Arrays.copyOf(pcm, Math.max(0, pcm.length - tolti));
        return Evento.FRASE_CHIUSA;
    }

    static double rms(short[] b, int n) {
        if (n <= 0) return 0;
        double somma = 0;
        for (int i = 0; i < n; i++) somma += (double) b[i] * b[i];
        return Math.sqrt(somma / n);
    }

    private void scrivi(short[] b, int n) {
        for (int i = 0; i < n; i++) {
            frase.write(b[i] & 0xFF);
            frase.write((b[i] >> 8) & 0xFF);
        }
    }
}
