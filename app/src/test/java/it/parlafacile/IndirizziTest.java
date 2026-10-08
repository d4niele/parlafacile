package it.parlafacile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IndirizziTest {

    @Test
    public void ipEPortaDiventanoUnUrlCompleto() {
        assertEquals("http://192.168.1.20:8000/v1/audio/transcriptions", Indirizzi.whisper("192.168.1.20:8000"));
        assertEquals("http://192.168.1.20:11434/api/chat", Indirizzi.ollama("192.168.1.20:11434"));
    }

    @Test
    public void gliSpaziVengonoTolti() {
        assertEquals("http://pc:8000/v1/audio/transcriptions", Indirizzi.whisper("  pc:8000 "));
    }

    @Test
    public void unUrlConPercorsoRestaCosiCom() {
        String groq = "https://api.groq.com/openai/v1/audio/transcriptions";
        assertEquals(groq, Indirizzi.whisper(groq));
        assertEquals("http://pc:8000/mio/percorso", Indirizzi.whisper("http://pc:8000/mio/percorso"));
    }

    @Test
    public void unHostSenzaPortaConProtocolloRiceveIlPercorso() {
        assertEquals("https://esempio.it/v1/audio/transcriptions", Indirizzi.whisper("https://esempio.it"));
    }

    @Test
    public void vuotoONullRestanoVuoti() {
        assertEquals("", Indirizzi.whisper(""));
        assertEquals("", Indirizzi.whisper("   "));
        assertEquals("", Indirizzi.whisper(null));
        assertEquals("", Indirizzi.ollama(null));
    }

    @Test
    public void riconoscimentoDiGoogle() {
        assertTrue(Indirizzi.èGoogle("https://speech.googleapis.com/v1/speech:recognize"));
        assertFalse(Indirizzi.èGoogle("https://api.groq.com/openai/v1/audio/transcriptions"));
        assertFalse(Indirizzi.èGoogle(null));
    }
}
