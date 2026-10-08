package it.parlafacile;

/** Intestazione di un file WAV PCM 16 bit mono. */
public final class Wav {
    private Wav() { }

    /** 44 byte di intestazione per `byteDati` byte di campioni a `frequenza` Hz. */
    public static byte[] intestazione(int byteDati, int frequenza) {
        byte[] h = new byte[44];
        copia(h, 0, "RIFF");
        int4(h, 4, byteDati + 36);
        copia(h, 8, "WAVEfmt ");
        int4(h, 16, 16);
        h[20] = 1;                      // PCM
        h[22] = 1;                      // mono
        int4(h, 24, frequenza);
        int4(h, 28, frequenza * 2);     // byte al secondo
        h[32] = 2;                      // byte per campione
        h[34] = 16;                     // bit
        copia(h, 36, "data");
        int4(h, 40, byteDati);
        return h;
    }

    private static void copia(byte[] h, int pos, String testo) {
        for (int i = 0; i < testo.length(); i++) h[pos + i] = (byte) testo.charAt(i);
    }

    private static void int4(byte[] b, int pos, int v) {
        b[pos] = (byte) v;
        b[pos + 1] = (byte) (v >> 8);
        b[pos + 2] = (byte) (v >> 16);
        b[pos + 3] = (byte) (v >> 24);
    }
}
