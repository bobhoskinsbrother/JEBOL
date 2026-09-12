package org.jebol.domain.eval;

final class StreamCipher {

    private static final int PERMUTATION_SIZE = 256;

    private final int[] permutation = new int[PERMUTATION_SIZE];
    private int takenSoFar;
    private int swappedSoFar;

    private StreamCipher() {
    }

    static StreamCipher keyedWithAnEmptyKeyAcceptedAsAny(byte[] key) {
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

    int nextKeystreamByteAdvancingThePermutation() {
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
