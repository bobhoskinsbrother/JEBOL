package org.jebol.domain.eval;

/**
 * RC4, kept as the permutation it is so a caller can encipher a stream.
 *
 * <p>{@code RC4_setup} mixes a key into a 256-byte permutation once, and
 * {@code RC4_crypt} advances that permutation for every byte it enciphers. So
 * this is state rather than a function: the same key applied to two halves of
 * a message gives a different answer from the key applied to the whole, which
 * is exactly what makes it a stream cipher and why the native hands a caller a
 * context to hold.
 *
 * <p>Enciphering and deciphering are one operation, because the cipher works
 * by exclusive-or against a keystream. Running ciphertext back through a
 * <em>fresh</em> context made from the same key gives the plaintext; running
 * it through the same context does not, because the permutation has moved on.
 */
final class StreamCipher {

    private static final int PERMUTATION_SIZE = 256;

    private final int[] permutation = new int[PERMUTATION_SIZE];
    private int takenSoFar;
    private int swappedSoFar;

    private StreamCipher() {
    }

    /**
     * A cipher with a key mixed into it, ready to encipher from its start.
     *
     * <p>An empty key is accepted rather than refused. The mixing loop reads
     * the key modulo its length, and with no bytes to read it leaves the
     * permutation as it found it -- which is a permutation like any other,
     * and a thing a caller can legitimately ask for.
     */
    static StreamCipher keyedWith(byte[] key) {
        StreamCipher cipher = new StreamCipher();
        for (int at = 0; at < PERMUTATION_SIZE; at++) {
            cipher.permutation[at] = at;
        }
        if (key.length == 0) {
            return cipher;
        }
        int mixing = 0;
        for (int at = 0; at < PERMUTATION_SIZE; at++) {
            mixing = (mixing + cipher.permutation[at] + (key[at % key.length] & 0xFF))
                    % PERMUTATION_SIZE;
            cipher.swap(at, mixing);
        }
        return cipher;
    }

    /**
     * The next byte of keystream, advancing the permutation.
     *
     * <p>A byte at a time rather than a buffer at a time, so the caller reads
     * and writes its own storage and the protection a binary carries is
     * checked where every other write checks it. Enciphering is exclusive-or
     * against this, which is why the operation is its own inverse through a
     * context in the same state.
     */
    int nextKeystreamByte() {
        takenSoFar = (takenSoFar + 1) % PERMUTATION_SIZE;
        swappedSoFar = (swappedSoFar + permutation[takenSoFar]) % PERMUTATION_SIZE;
        swap(takenSoFar, swappedSoFar);
        return permutation[
                (permutation[takenSoFar] + permutation[swappedSoFar]) % PERMUTATION_SIZE];
    }

    private void swap(int here, int there) {
        int held = permutation[here];
        permutation[here] = permutation[there];
        permutation[there] = held;
    }
}
