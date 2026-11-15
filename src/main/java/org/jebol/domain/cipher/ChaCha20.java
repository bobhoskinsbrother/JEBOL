package org.jebol.domain.cipher;

public final class ChaCha20 {

    private static final int BLOCK = 64;
    private static final int WORDS = 16;
    private static final int DOUBLE_ROUNDS = 10;
    private static final int[] CONSTANTS = {0x61707865, 0x3320646e, 0x79622d32, 0x6b206574};

    private final int[] input = new int[WORDS];
    private final byte[] keystream = new byte[BLOCK];
    private int used = BLOCK;

    public ChaCha20(byte[] key, byte[] nonce, int counter) {
        System.arraycopy(CONSTANTS, 0, input, 0, CONSTANTS.length);
        for (int word = 0; word < 8; word++) {
            input[4 + word] = littleEndianAt(key, word * 4);
        }
        input[12] = counter;
        for (int word = 0; word < 3; word++) {
            input[13 + word] = littleEndianAt(nonce, word * 4);
        }
    }

    public byte[] mask(byte[] octets) {
        byte[] masked = new byte[octets.length];
        for (int at = 0; at < octets.length; at++) {
            if (used == BLOCK) {
                makeNextBlock();
            }
            masked[at] = (byte) (octets[at] ^ keystream[used++]);
        }
        return masked;
    }

    public byte[] keystream(int howMany) {
        return mask(new byte[howMany]);
    }

    private void makeNextBlock() {
        int[] working = input.clone();
        for (int round = 0; round < DOUBLE_ROUNDS; round++) {
            quarterRound(working, 0, 4, 8, 12);
            quarterRound(working, 1, 5, 9, 13);
            quarterRound(working, 2, 6, 10, 14);
            quarterRound(working, 3, 7, 11, 15);
            quarterRound(working, 0, 5, 10, 15);
            quarterRound(working, 1, 6, 11, 12);
            quarterRound(working, 2, 7, 8, 13);
            quarterRound(working, 3, 4, 9, 14);
        }
        for (int word = 0; word < WORDS; word++) {
            int sum = working[word] + input[word];
            keystream[word * 4] = (byte) sum;
            keystream[word * 4 + 1] = (byte) (sum >>> 8);
            keystream[word * 4 + 2] = (byte) (sum >>> 16);
            keystream[word * 4 + 3] = (byte) (sum >>> 24);
        }
        input[12]++;
        used = 0;
    }

    private static void quarterRound(int[] s, int a, int b, int c, int d) {
        s[a] += s[b];
        s[d] = Integer.rotateLeft(s[d] ^ s[a], 16);
        s[c] += s[d];
        s[b] = Integer.rotateLeft(s[b] ^ s[c], 12);
        s[a] += s[b];
        s[d] = Integer.rotateLeft(s[d] ^ s[a], 8);
        s[c] += s[d];
        s[b] = Integer.rotateLeft(s[b] ^ s[c], 7);
    }

    private static int littleEndianAt(byte[] octets, int at) {
        return (octets[at] & 0xFF) | (octets[at + 1] & 0xFF) << 8
                | (octets[at + 2] & 0xFF) << 16 | (octets[at + 3] & 0xFF) << 24;
    }
}
