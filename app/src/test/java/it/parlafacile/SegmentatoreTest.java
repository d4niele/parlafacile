package it.parlafacile;

import static it.parlafacile.Segmentatore.Evento.FRASE_CHIUSA;
import static it.parlafacile.Segmentatore.Evento.FRASE_SCARTATA;
import static it.parlafacile.Segmentatore.Evento.NIENTE;
import static it.parlafacile.Segmentatore.Evento.VOCE_INIZIATA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SegmentatoreTest {

    private static short[] blocco(int ampiezza) {
        short[] b = new short[Segmentatore.BLOCCO];
        for (int i = 0; i < b.length; i++) b[i] = (short) (i % 2 == 0 ? ampiezza : -ampiezza);
        return b;
    }

    private static Segmentatore.Evento manda(Segmentatore s, int ampiezza, int volte) {
        Segmentatore.Evento ultimo = NIENTE;
        for (int i = 0; i < volte; i++) {
            Segmentatore.Evento e = s.alimenta(blocco(ampiezza), Segmentatore.BLOCCO);
            if (e != NIENTE) ultimo = e;
        }
        return ultimo;
    }

    @Test
    public void ilSilenzioNonFaNiente() {
        Segmentatore s = new Segmentatore();
        assertEquals(NIENTE, manda(s, 50, 100));
        assertFalse(s.inVoce());
    }

    @Test
    public void unFruscioCostanteSottoSogliaNonEVoce() {
        Segmentatore s = new Segmentatore();
        assertEquals(NIENTE, manda(s, 300, 200));
    }

    @Test
    public void laVoceFaPartireUnaFrase() {
        Segmentatore s = new Segmentatore();
        manda(s, 50, 5);
        assertEquals(VOCE_INIZIATA, s.alimenta(blocco(5000), Segmentatore.BLOCCO));
        assertTrue(s.inVoce());
    }

    @Test
    public void laFraseSiChiudeDopoLaPausaETieneSoloUnPocoDiSilenzio() {
        Segmentatore s = new Segmentatore();
        manda(s, 50, 10);                       // silenzio: il pre-roll tiene gli ultimi 3 blocchi
        manda(s, 5000, 12);                     // 12 blocchi di voce
        for (int i = 0; i < Segmentatore.SILENZIO_FINE_BLOCCHI - 1; i++) {
            assertEquals(NIENTE, s.alimenta(blocco(50), Segmentatore.BLOCCO));
        }
        assertEquals(FRASE_CHIUSA, s.alimenta(blocco(50), Segmentatore.BLOCCO));
        assertFalse(s.inVoce());

        int blocchi = Segmentatore.PREROLL_BLOCCHI + 12 + Segmentatore.SILENZIO_TENUTO_BLOCCHI;
        assertEquals(blocchi * Segmentatore.BLOCCO * 2, s.frase().length);
    }

    @Test
    public void unRumoreBreveVieneScartato() {
        Segmentatore s = new Segmentatore();
        manda(s, 50, 5);
        manda(s, 5000, Segmentatore.MIN_VOCE_BLOCCHI - 1);
        assertEquals(FRASE_SCARTATA, manda(s, 50, Segmentatore.SILENZIO_FINE_BLOCCHI));
        assertEquals(0, s.frase().length);
    }

    @Test
    public void unaPausaCortaNonSpezzaLaFrase() {
        Segmentatore s = new Segmentatore();
        manda(s, 5000, 10);
        assertEquals(NIENTE, manda(s, 50, Segmentatore.SILENZIO_FINE_BLOCCHI - 1));
        assertEquals(NIENTE, manda(s, 5000, 10));
        assertTrue(s.inVoce());
    }

    @Test
    public void unaFraseLunghissimaVieneTagliataA25Secondi() {
        Segmentatore s = new Segmentatore();
        Segmentatore.Evento e = manda(s, 5000, Segmentatore.MAX_FRASE_BLOCCHI);
        assertEquals(FRASE_CHIUSA, e);
        assertEquals(Segmentatore.MAX_FRASE_BLOCCHI * Segmentatore.BLOCCO * 2, s.frase().length);
    }

    @Test
    public void dopoUnaFraseSiPuoRiascoltare() {
        Segmentatore s = new Segmentatore();
        manda(s, 5000, 6);
        assertEquals(FRASE_CHIUSA, manda(s, 50, Segmentatore.SILENZIO_FINE_BLOCCHI));
        assertEquals(VOCE_INIZIATA, s.alimenta(blocco(5000), Segmentatore.BLOCCO));
    }

    @Test
    public void rmsDiUnBloccoCostante() {
        assertEquals(1000.0, Segmentatore.rms(blocco(1000), Segmentatore.BLOCCO), 0.001);
        assertEquals(0.0, Segmentatore.rms(new short[0], 0), 0.0);
    }
}
