package org.jebol.domain.eval;

/**
 * MD4, written out because the JVM has not got it.
 *
 * <p>{@code system/catalog/checksums} lists it, and {@code java.security}
 * dropped it long ago -- it is thoroughly broken as a cryptographic hash and
 * has been since the nineties. It is still here because file formats and
 * protocols written when it was new still carry MD4 sums in them, and reading
 * one of those means computing one.
 *
 * <p>MD5's older and simpler relation: the same little-endian layout and the
 * same padding, three rounds instead of four, and no per-step constant table.
 * Each round has one constant of its own and one way of mixing three words.
 */
final class Md4 {

    private Md4() {
    }

    private static final int[] STARTING_WORDS = {
        0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476,
    };

    /** Which message word each step of each round takes. */
    private static final int[][] WORD_ORDER = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
        {0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15},
        {0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15},
    };

    /** How far each step rotates, four values repeating through the round. */
    private static final int[][] ROTATIONS = {
        {3, 7, 11, 19},
        {3, 5, 9, 13},
        {3, 9, 11, 15},
    };

    /**
     * One constant a round, where MD5 has one a step.
     *
     * <p>The first is nought, which is why the first round looks as though it
     * has no constant at all. It is the sines table MD5 introduced that is
     * missing here, and its absence is part of why MD4 fell.
     */
    private static final int[] ROUND_CONSTANTS = {
        0x00000000, 0x5A827999, 0x6ED9EBA1,
    };

    /** The digest of some bytes, sixteen of them. */
    static byte[] of(byte[] message) {
        int[] words = STARTING_WORDS.clone();
        byte[] padded = padded(message);
        int[] block = new int[16];
        for (int at = 0; at < padded.length; at += 64) {
            for (int word = 0; word < 16; word++) {
                block[word] = littleEndianWordAt(padded, at + word * 4);
            }
            compress(words, block);
        }
        return digestOf(words);
    }

    private static byte[] padded(byte[] message) {
        int roomForTheLength = 9;
        int blocks = (message.length + roomForTheLength + 63) / 64;
        byte[] padded = new byte[blocks * 64];
        System.arraycopy(message, 0, padded, 0, message.length);
        padded[message.length] = (byte) 0x80;
        long bits = (long) message.length * 8;
        for (int at = 0; at < 8; at++) {
            padded[padded.length - 8 + at] = (byte) (bits >>> at * 8);
        }
        return padded;
    }

    private static int littleEndianWordAt(byte[] bytes, int at) {
        return bytes[at] & 0xFF
                | (bytes[at + 1] & 0xFF) << 8
                | (bytes[at + 2] & 0xFF) << 16
                | (bytes[at + 3] & 0xFF) << 24;
    }

    private static void compress(int[] words, int[] block) {
        int first = words[0];
        int second = words[1];
        int third = words[2];
        int fourth = words[3];
        for (int round = 0; round < 3; round++) {
            for (int step = 0; step < 16; step++) {
                int turned = Integer.rotateLeft(first
                        + mixed(round, second, third, fourth)
                        + block[WORD_ORDER[round][step]]
                        + ROUND_CONSTANTS[round], ROTATIONS[round][step % 4]);
                first = fourth;
                fourth = third;
                third = second;
                second = turned;
            }
        }
        words[0] += first;
        words[1] += second;
        words[2] += third;
        words[3] += fourth;
    }

    /**
     * The round's own way of mixing three words: choose, majority, then
     * exclusive-or.
     *
     * <p>MD5 uses four; MD4 has three and reuses none of them. The middle one
     * is the majority function, which is the only one of the three that is not
     * a selection.
     */
    private static int mixed(int round, int second, int third, int fourth) {
        return switch (round) {
            case 0 -> second & third | ~second & fourth;
            case 1 -> second & third | second & fourth | third & fourth;
            default -> second ^ third ^ fourth;
        };
    }

    private static byte[] digestOf(int[] words) {
        byte[] digest = new byte[16];
        for (int word = 0; word < words.length; word++) {
            for (int at = 0; at < 4; at++) {
                digest[word * 4 + at] = (byte) (words[word] >>> at * 8);
            }
        }
        return digest;
    }
}
