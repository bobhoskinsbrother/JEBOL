package org.jebol.domain.eval;

/**
 * RIPEMD-160, written out because the JVM has not got it.
 *
 * <p>{@code system/catalog/checksums} lists it, so a script may ask for it and
 * a port may be opened on it, and {@code java.security} offers MD5, the SHA
 * family and nothing else. The shipped jar takes no dependencies, so the
 * alternative to writing it is not offering it.
 *
 * <p>The algorithm is Dobbertin, Bosselaers and Preneel's, and it is two
 * chains of eighty steps run over the same message block and added together at
 * the end. That doubling is the whole design: each chain is weak on its own
 * and they use different constants, a different order of message words and a
 * different order of rotations, so a weakness in one does not line up with the
 * other.
 *
 * <p>Little-endian throughout, unlike SHA. The length is appended as a
 * little-endian count of bits, and each word of the digest comes out low byte
 * first.
 */
final class RipeMd160 {

    private RipeMd160() {
    }

    /** The five words the chains start from, and end up added to. */
    private static final int[] STARTING_WORDS = {
        0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0,
    };

    /** Which message word each of the eighty steps of the left chain takes. */
    private static final int[] LEFT_WORD_ORDER = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
        1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
        4, 0, 5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13,
    };

    private static final int[] RIGHT_WORD_ORDER = {
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
        6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
        8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
        12, 15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11,
    };

    private static final int[] LEFT_ROTATIONS = {
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
        7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
        11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
        9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6,
    };

    private static final int[] RIGHT_ROTATIONS = {
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
        9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
        15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
        8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11,
    };

    /** One constant for each round of sixteen steps, added to every step. */
    private static final int[] LEFT_CONSTANTS = {
        0x00000000, 0x5A827999, 0x6ED9EBA1, 0x8F1BBCDC, 0xA953FD4E,
    };

    private static final int[] RIGHT_CONSTANTS = {
        0x50A28BE6, 0x5C4DD124, 0x6D703EF3, 0x7A6D76E9, 0x00000000,
    };

    /**
     * The digest of some bytes, twenty of them.
     *
     * <p>The message is padded the way MD5 and SHA-1 pad theirs: a set bit,
     * then noughts up to eight short of a block, then the length in bits. The
     * length is little-endian here where SHA's is big.
     */
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

    /**
     * Runs both chains over one block and folds them into the running words.
     *
     * <p>The fold at the end is the part that is easy to get wrong and easy to
     * miss, because a wrong one still produces a plausible-looking digest: the
     * five words are rotated by one as they are added, so word two takes the
     * left chain's third and the right chain's fourth rather than its own.
     */
    private static void compress(int[] words, int[] block) {
        int leftA = words[0];
        int leftB = words[1];
        int leftC = words[2];
        int leftD = words[3];
        int leftE = words[4];
        int rightA = words[0];
        int rightB = words[1];
        int rightC = words[2];
        int rightD = words[3];
        int rightE = words[4];
        for (int step = 0; step < 80; step++) {
            int round = step / 16;
            int turned = Integer.rotateLeft(leftA
                    + mixed(round, leftB, leftC, leftD)
                    + block[LEFT_WORD_ORDER[step]]
                    + LEFT_CONSTANTS[round], LEFT_ROTATIONS[step]) + leftE;
            leftA = leftE;
            leftE = leftD;
            leftD = Integer.rotateLeft(leftC, 10);
            leftC = leftB;
            leftB = turned;
            int turnedBack = Integer.rotateLeft(rightA
                    + mixed(4 - round, rightB, rightC, rightD)
                    + block[RIGHT_WORD_ORDER[step]]
                    + RIGHT_CONSTANTS[round], RIGHT_ROTATIONS[step]) + rightE;
            rightA = rightE;
            rightE = rightD;
            rightD = Integer.rotateLeft(rightC, 10);
            rightC = rightB;
            rightB = turnedBack;
        }
        int carried = words[1] + leftC + rightD;
        words[1] = words[2] + leftD + rightE;
        words[2] = words[3] + leftE + rightA;
        words[3] = words[4] + leftA + rightB;
        words[4] = words[0] + leftB + rightC;
        words[0] = carried;
    }

    /**
     * The round's own way of mixing three words, of which there are five.
     *
     * <p>The right chain runs them in the opposite order, which is why it is
     * asked for {@code 4 - round}. Using the same order in both would make the
     * two chains far more alike than the design intends.
     */
    private static int mixed(int round, int first, int second, int third) {
        return switch (round) {
            case 0 -> first ^ second ^ third;
            case 1 -> first & second | ~first & third;
            case 2 -> (first | ~second) ^ third;
            case 3 -> first & third | second & ~third;
            default -> first ^ (second | ~third);
        };
    }

    private static byte[] digestOf(int[] words) {
        byte[] digest = new byte[20];
        for (int word = 0; word < words.length; word++) {
            for (int at = 0; at < 4; at++) {
                digest[word * 4 + at] = (byte) (words[word] >>> at * 8);
            }
        }
        return digest;
    }
}
