package it.parlafacile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

public class WavTest {

    @Test
    public void intestazioneDiUnWavPcm16Mono() {
        byte[] h = Wav.intestazione(3200, 16000);
        assertEquals(44, h.length);
        assertArrayEquals("RIFF".getBytes(), java.util.Arrays.copyOfRange(h, 0, 4));
        assertArrayEquals("WAVEfmt ".getBytes(), java.util.Arrays.copyOfRange(h, 8, 16));
        assertArrayEquals("data".getBytes(), java.util.Arrays.copyOfRange(h, 36, 40));

        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(3200 + 36, b.getInt(4));      // dimensione del file - 8
        assertEquals(16, b.getInt(16));            // dimensione del blocco fmt
        assertEquals(1, b.getShort(20));           // PCM
        assertEquals(1, b.getShort(22));           // mono
        assertEquals(16000, b.getInt(24));         // frequenza
        assertEquals(32000, b.getInt(28));         // byte al secondo
        assertEquals(2, b.getShort(32));           // byte per campione
        assertEquals(16, b.getShort(34));          // bit
        assertEquals(3200, b.getInt(40));          // byte di dati
    }
}
