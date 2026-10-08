package it.parlafacile;

/**
 * Un motore di riconoscimento vocale (locale o in rete).
 * Tutti i callback arrivano sul thread principale.
 */
public interface MotoreAscolto {

    interface Ascoltatore {
        /** Testo provvisorio mentre la persona sta ancora parlando. */
        void parziale(String testo);

        /** Frase conclusa. */
        void finale(String testo);

        /**
         * Errore. Se recuperabile == false il motore si è fermato
         * e conviene passare all'altro motore.
         */
        void errore(String messaggio, boolean recuperabile);
    }

    void avvia();

    void ferma();

    /** Rilascia le risorse; dopo non si può più usare. */
    void chiudi();

    boolean attivo();

    /** Nome breve da mostrare a video, es. "locale" o "rete". */
    String nome();
}
