package it.parlafacile;

import static it.parlafacile.FiltroTesto.Comando.CANCELLA;
import static it.parlafacile.FiltroTesto.Comando.NESSUNO;
import static it.parlafacile.FiltroTesto.Comando.PIU_GRANDE;
import static it.parlafacile.FiltroTesto.Comando.PIU_PICCOLO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FiltroTestoTest {

    @Test
    public void leAllucinazioniDiWhisperVengonoRiconosciute() {
        assertTrue(FiltroTesto.allucinazione("Sottotitoli e revisione a cura di QTSS"));
        assertTrue(FiltroTesto.allucinazione("Sottotitoli creati dalla comunità Amara.org"));
        assertTrue(FiltroTesto.allucinazione("GRAZIE PER LA VISIONE"));
        assertFalse(FiltroTesto.allucinazione("Oggi vado al mercato"));
        assertFalse(FiltroTesto.allucinazione(""));
    }

    @Test
    public void ilConfrontoDelleParoleIgnoraPunteggiaturaEMaiuscole() {
        assertTrue(FiltroTesto.stesseParole("oggi vado al mercato poi torno a casa",
                "Oggi vado al mercato, poi torno a casa."));
        assertTrue(FiltroTesto.stesseParole("ciao come stai", "Ciao! Come stai?"));
        assertTrue(FiltroTesto.stesseParole("è già tardi", "È già tardi."));
    }

    @Test
    public void unaParolaCambiataVieneRiconosciuta() {
        assertFalse(FiltroTesto.stesseParole("poi torno a casa", "poi torna a casa"));
        assertFalse(FiltroTesto.stesseParole("uno due", "uno due tre"));
        assertFalse(FiltroTesto.stesseParole("uno due", "due uno"));
    }

    @Test
    public void comandiVocali() {
        assertEquals(CANCELLA, FiltroTesto.comando("Cancella"));
        assertEquals(CANCELLA, FiltroTesto.comando("cancella tutto."));
        assertEquals(CANCELLA, FiltroTesto.comando("Pulisci!"));
        assertEquals(PIU_GRANDE, FiltroTesto.comando("Più grande"));
        assertEquals(PIU_GRANDE, FiltroTesto.comando("ingrandisci"));
        assertEquals(PIU_PICCOLO, FiltroTesto.comando("più piccolo"));
        assertEquals(PIU_PICCOLO, FiltroTesto.comando("Rimpicciolisci."));
    }

    @Test
    public void unComandoDentroUnaFraseNonVieneEseguito() {
        assertEquals(NESSUNO, FiltroTesto.comando("puoi cancellare quella cosa"));
        assertEquals(NESSUNO, FiltroTesto.comando("scrivi più grande per favore"));
        assertEquals(NESSUNO, FiltroTesto.comando(""));
    }
}
