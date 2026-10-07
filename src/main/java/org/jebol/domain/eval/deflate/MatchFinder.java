package org.jebol.domain.eval.deflate;

abstract class MatchFinder {

    static final short NO_POSITION = (short) -DeflateTables.WINDOW_SIZE;

    static final int WINDOW_MASK = DeflateTables.WINDOW_SIZE - 1;

    private static final int HASH_MULTIPLIER = 0x1E35A7BD;

    private static final int LOW_THREE_BYTES = 0xFFFFFF;

    int load32(byte[] input, int at) {
        return (input[at] & 0xFF)
                | (input[at + 1] & 0xFF) << 8
                | (input[at + 2] & 0xFF) << 16
                | (input[at + 3] & 0xFF) << 24;
    }

    int load24(byte[] input, int at) {
        return load32(input, at) & LOW_THREE_BYTES;
    }

    int lowThreeBytes(int sequence) {
        return sequence & LOW_THREE_BYTES;
    }

    int hash(int sequence, int bits) {
        return (sequence * HASH_MULTIPLIER) >>> (Integer.SIZE - bits);
    }

    int extend(byte[] input, int string, int match, int startLength, int maxLength) {
        int length = startLength;
        while (length < maxLength && input[match + length] == input[string + length]) {
            length++;
        }
        return length;
    }

    int cutoffFor(int position) {
        return (short) (position - DeflateTables.WINDOW_SIZE);
    }

    void rebase(short[] table) {
        for (int at = 0; at < table.length; at++) {
            table[at] = (short) (0x8000 | (table[at] & ~(table[at] >> 15)));
        }
    }
}
