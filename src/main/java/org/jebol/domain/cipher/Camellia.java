package org.jebol.domain.cipher;

/**
 * Camellia, written out because no JVM provider carries it.
 *
 * <p>RFC 3713, and {@code camellia.c}. A Feistel cipher with the same block
 * and the same three key widths as AES, standardised in ISO/IEC 18033-3 and on
 * Japan's CRYPTREC list. Nothing in REBOL's own library asks for it; it is
 * here because REBOL's catalogue names it and a name in a catalogue is a
 * promise.
 *
 * <p>The shape is one round function used twice over. Eighteen rounds for a
 * short key and twenty-four for the others, with a pair of mixing steps every
 * six rounds, and the key schedule folds the key through that same round
 * function to make the subkeys. So there is one piece of arithmetic here and
 * everything else is bookkeeping about where its inputs come from.
 *
 * <p>Deciphering is enciphering with the subkeys reversed, which is what a
 * Feistel design buys: the round function is never undone, only applied in the
 * other order.
 */
public final class Camellia {

    private Camellia() {
    }

    /** How many bytes go in and come out, which is the same for every key. */
    public static final int BLOCK = 16;

    /**
     * A cipher ready to transform blocks under one key, in one direction.
     *
     * <p>The direction is settled here rather than per block, because it is
     * the subkey order that changes and that is worked out once.
     */
    public static OneBlock under(byte[] key, boolean deciphering) {
        int[] subkeys = deciphering
                ? forDeciphering(key)
                : forEnciphering(key);
        int rounds = key.length <= 16 ? 3 : 4;
        return block -> transformed(block, subkeys, rounds);
    }

    /**
     * The round function: substitute every byte, then mix the two halves.
     *
     * <p>{@code camellia_feistel}. Four substitution boxes, which are one
     * table read four ways -- the other three are rotations of the first, so
     * only one is carried.
     */
    private static void round(int[] state, int at, int[] key, int keyAt,
            int[] into, int intoAt) {

        int left = state[at] ^ key[keyAt];
        int right = state[at + 1] ^ key[keyAt + 1];

        left = substituted1(byteAt(left, 3)) << 24
                | substituted2(byteAt(left, 2)) << 16
                | substituted3(byteAt(left, 1)) << 8
                | substituted4(byteAt(left, 0));
        right = substituted2(byteAt(right, 3)) << 24
                | substituted3(byteAt(right, 2)) << 16
                | substituted4(byteAt(right, 1)) << 8
                | substituted1(byteAt(right, 0));

        left ^= right << 8 | right >>> 24;
        right ^= left << 16 | left >>> 16;
        left ^= right >>> 8 | right << 24;
        right ^= left >>> 8 | left << 24;

        into[intoAt] ^= right;
        into[intoAt + 1] ^= left;
    }

    private static int byteAt(int word, int which) {
        return word >>> which * 8 & 0xFF;
    }

    private static int substituted1(int index) {
        return SUBSTITUTION[index] & 0xFF;
    }

    private static int substituted2(int index) {
        int found = SUBSTITUTION[index] & 0xFF;
        return (found >>> 7 ^ found << 1) & 0xFF;
    }

    private static int substituted3(int index) {
        int found = SUBSTITUTION[index] & 0xFF;
        return (found >>> 1 ^ found << 7) & 0xFF;
    }

    private static int substituted4(int index) {
        return SUBSTITUTION[(index << 1 ^ index >>> 7) & 0xFF] & 0xFF;
    }

    /**
     * The two mixing steps that break the Feistel pattern every six rounds,
     * and stop the rounds being a chain of identical steps.
     */
    private static void mixTheFirstHalf(int[] state, int leftKey, int rightKey) {
        int masked = state[0] & leftKey;
        state[1] ^= masked << 1 | masked >>> 31;
        state[0] ^= state[1] | rightKey;
    }

    private static void mixTheSecondHalf(int[] state, int leftKey, int rightKey) {
        state[2] ^= state[3] | rightKey;
        int masked = state[2] & leftKey;
        state[3] ^= masked << 1 | masked >>> 31;
    }

    private static byte[] transformed(byte[] block, int[] subkeys, int rounds) {
        int[] state = {
            wordAt(block, 0), wordAt(block, 4),
            wordAt(block, 8), wordAt(block, 12)
        };
        int key = 0;
        for (int at = 0; at < 4; at++) {
            state[at] ^= subkeys[key++];
        }
        for (int left = rounds; left > 0; left--) {
            for (int pair = 0; pair < 3; pair++) {
                round(state, 0, subkeys, key, state, 2);
                key += 2;
                round(state, 2, subkeys, key, state, 0);
                key += 2;
            }
            if (left > 1) {
                mixTheFirstHalf(state, subkeys[key], subkeys[key + 1]);
                key += 2;
                mixTheSecondHalf(state, subkeys[key], subkeys[key + 1]);
                key += 2;
            }
        }
        return theHalvesSwapped(state, subkeys, key);
    }

    /**
     * The last four subkeys go on, and the two halves come out the other way
     * round -- the swap every Feistel cipher ends with, so that deciphering is
     * the same walk with the keys reversed.
     */
    private static byte[] theHalvesSwapped(int[] state, int[] subkeys, int key) {
        state[2] ^= subkeys[key];
        state[3] ^= subkeys[key + 1];
        state[0] ^= subkeys[key + 2];
        state[1] ^= subkeys[key + 3];
        byte[] out = new byte[BLOCK];
        putWord(out, 0, state[2]);
        putWord(out, 4, state[3]);
        putWord(out, 8, state[0]);
        putWord(out, 12, state[1]);
        return out;
    }

    private static int wordAt(byte[] octets, int at) {
        return (octets[at] & 0xFF) << 24 | (octets[at + 1] & 0xFF) << 16
                | (octets[at + 2] & 0xFF) << 8 | octets[at + 3] & 0xFF;
    }

    private static void putWord(byte[] octets, int at, int word) {
        octets[at] = (byte) (word >>> 24);
        octets[at + 1] = (byte) (word >>> 16);
        octets[at + 2] = (byte) (word >>> 8);
        octets[at + 3] = (byte) word;
    }

    /**
     * The subkeys for enciphering.
     *
     * <p>The key is folded through the round function to make two or four
     * derived keys, and those are rotated by fifteen bits at a time and dealt
     * out to the rounds. Which rotation goes where is a table rather than a
     * pattern, which is why the tables below are carried rather than computed.
     *
     * <p>A 192-bit key is stored as a 256-bit one whose last eight bytes are
     * the complement of the eight before them, so only two widths of schedule
     * exist rather than three.
     */
    private static int[] forEnciphering(byte[] key) {
        int width = key.length <= 16 ? 0 : 1;
        byte[] stored = new byte[32];
        System.arraycopy(key, 0, stored, 0, key.length);
        if (key.length == 24) {
            for (int at = 0; at < 8; at++) {
                stored[24 + at] = (byte) ~stored[16 + at];
            }
        }
        int[] derived = new int[16];
        for (int at = 0; at < 8; at++) {
            derived[at] = wordAt(stored, at * 4);
        }
        foldTheKeyThrough(derived, key.length > 16);

        int[] subkeys = new int[HOW_MANY_SUBKEYS];
        int[] rotating = new int[20];
        placeRotationsOf(derived, 0, width, subkeys, rotating);
        if (key.length > 16) {
            placeRotationsOf(derived, 1, width, subkeys, rotating);
        }
        placeRotationsOf(derived, 2, width, subkeys, rotating);
        if (key.length > 16) {
            placeRotationsOf(derived, 3, width, subkeys, rotating);
        }
        for (int at = 0; at < 20; at++) {
            if (TRANSPOSES[width][at] != -1) {
                subkeys[32 + 12 * width + at] = subkeys[TRANSPOSES[width][at]];
            }
        }
        return subkeys;
    }

    /** Room for the longest schedule, which the shorter one does not fill. */
    private static final int HOW_MANY_SUBKEYS = 68;

    private static void foldTheKeyThrough(int[] derived, boolean longKey) {
        for (int at = 0; at < 4; at++) {
            derived[8 + at] = derived[at] ^ derived[4 + at];
        }
        round(derived, 8, SIGMA, 0, derived, 10);
        round(derived, 10, SIGMA, 2, derived, 8);
        for (int at = 0; at < 4; at++) {
            derived[8 + at] ^= derived[at];
        }
        round(derived, 8, SIGMA, 4, derived, 10);
        round(derived, 10, SIGMA, 6, derived, 8);
        if (!longKey) {
            return;
        }
        for (int at = 0; at < 4; at++) {
            derived[12 + at] = derived[4 + at] ^ derived[8 + at];
        }
        round(derived, 12, SIGMA, 8, derived, 14);
        round(derived, 14, SIGMA, 10, derived, 12);
    }

    /**
     * Rotates one derived key by fifteen bits, four times over, and deals the
     * five results out to whichever rounds want them.
     */
    private static void placeRotationsOf(int[] derived, int which, int width,
            int[] subkeys, int[] rotating) {

        System.arraycopy(derived, which * 4, rotating, 0, 4);
        for (int step = 1; step <= 4; step++) {
            if (SHIFTS[width][which][step - 1] != 0) {
                rotateBy(rotating, step * 4, 15 * step % 32);
            }
        }
        for (int at = 0; at < 20; at++) {
            if (INDEXES[width][which][at] != -1) {
                subkeys[INDEXES[width][which][at]] = rotating[at];
            }
        }
    }

    /** A rotation of the whole hundred and twenty-eight bits at once. */
    private static void rotateBy(int[] words, int into, int places) {
        for (int at = 0; at < 4; at++) {
            words[into + at] = words[at] << places
                    ^ words[(at + 1) % 4] >>> 32 - places;
        }
    }

    /**
     * The subkeys for deciphering: the same ones, read back to front.
     *
     * <p>The four at each end stay where they are and the pairs between them
     * reverse, which is the whole of what a Feistel cipher needs to run
     * backwards.
     */
    private static int[] forDeciphering(byte[] key) {
        int[] forwards = forEnciphering(key);
        int width = key.length <= 16 ? 0 : 1;
        int[] backwards = new int[HOW_MANY_SUBKEYS];
        int from = 48 + 16 * width;
        int to = 0;
        for (int at = 0; at < 4; at++) {
            backwards[to++] = forwards[from++];
        }
        from -= 6;
        for (int pairs = 22 + 8 * width; pairs > 0; pairs--, from -= 4) {
            backwards[to++] = forwards[from++];
            backwards[to++] = forwards[from++];
        }
        from -= 2;
        for (int at = 0; at < 4; at++) {
            backwards[to++] = forwards[from++];
        }
        return backwards;
    }

    /** The constants the key schedule folds against, as pairs of words. */
    private static final int[] SIGMA = {
        0xA09E667F, 0x3BCC908B, 0xB67AE858, 0x4CAA73B2,
        0xC6EF372F, 0xE94F82BE, 0x54FF53A5, 0xF1D36F1C,
        0x10E527FA, 0xDE682D1D, 0xB05688C2, 0xB3E6C1FD
    };

    /** Which of the four rotations each derived key actually needs. */
    private static final int[][][] SHIFTS = {
        {{1, 1, 1, 1}, {0, 0, 0, 0}, {1, 1, 1, 1}, {0, 0, 0, 0}},
        {{1, 0, 1, 1}, {1, 1, 0, 1}, {1, 1, 1, 0}, {1, 1, 0, 1}}
    };

    /** Which round each rotation is dealt to, or nowhere at all. */
    private static final int[][][] INDEXES = {
        {
            {0, 1, 2, 3, 8, 9, 10, 11, 38, 39,
                36, 37, 23, 20, 21, 22, 27, -1, -1, 26},
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},
            {4, 5, 6, 7, 12, 13, 14, 15, 16, 17,
                18, 19, -1, 24, 25, -1, 31, 28, 29, 30},
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}
        },
        {
            {0, 1, 2, 3, 61, 62, 63, 60, -1, -1,
                -1, -1, 27, 24, 25, 26, 35, 32, 33, 34},
            {-1, -1, -1, -1, 8, 9, 10, 11, 16, 17,
                18, 19, -1, -1, -1, -1, 39, 36, 37, 38},
            {-1, -1, -1, -1, 12, 13, 14, 15, 58, 59,
                56, 57, 31, 28, 29, 30, -1, -1, -1, -1},
            {4, 5, 6, 7, 65, 66, 67, 64, 20, 21,
                22, 23, -1, -1, -1, -1, 43, 40, 41, 42}
        }
    };

    /** The last few subkeys, which are copies of earlier ones. */
    private static final int[][] TRANSPOSES = {
        {21, 22, 23, 20, -1, -1, -1, -1, 18, 19, 16, 17,
            11, 8, 9, 10, 15, 12, 13, 14},
        {25, 26, 27, 24, 29, 30, 31, 28, 18, 19, 16, 17,
            -1, -1, -1, -1, -1, -1, -1, -1}
    };

    /**
     * The substitution box, and the only table here that is not bookkeeping.
     *
     * <p>One of four in the specification and the only one carried: the other
     * three are this one rotated by a bit or read at a rotated index, which
     * {@link #substituted2} and its neighbours do rather than storing three
     * more copies.
     */
    private static final byte[] SUBSTITUTION = {
        112, -126, 44, -20, -77, 39, -64, -27, -28, -123, 87, 53, -22, 12, -82, 65,
        35, -17, 107, -109, 69, 25, -91, 33, -19, 14, 79, 78, 29, 101, -110, -67,
        -122, -72, -81, -113, 124, -21, 31, -50, 62, 48, -36, 95, 94, -59, 11, 26,
        -90, -31, 57, -54, -43, 71, 93, 61, -39, 1, 90, -42, 81, 86, 108, 77,
        -117, 13, -102, 102, -5, -52, -80, 45, 116, 18, 43, 32, -16, -79, -124, -103,
        -33, 76, -53, -62, 52, 126, 118, 5, 109, -73, -87, 49, -47, 23, 4, -41,
        20, 88, 58, 97, -34, 27, 17, 28, 50, 15, -100, 22, 83, 24, -14, 34,
        -2, 68, -49, -78, -61, -75, 122, -111, 36, 8, -24, -88, 96, -4, 105, 80,
        -86, -48, -96, 125, -95, -119, 98, -105, 84, 91, 30, -107, -32, -1, 100, -46,
        16, -60, 0, 72, -93, -9, 117, -37, -118, 3, -26, -38, 9, 63, -35, -108,
        -121, 92, -125, 2, -51, 74, -112, 51, 115, 103, -10, -13, -99, 127, -65, -30,
        82, -101, -40, 38, -56, 55, -58, 59, -127, -106, 111, 75, 19, -66, 99, 46,
        -23, 121, -89, -116, -97, 110, -68, -114, 41, -11, -7, -74, 47, -3, -76, 89,
        120, -104, 6, 106, -25, 70, 113, -70, -44, 37, -85, 66, -120, -94, -115, -6,
        114, 7, -71, 85, -8, -18, -84, 10, 54, 73, 42, 104, 60, 56, -15, -92,
        64, 40, -45, 123, -69, -55, 67, -63, 21, -29, -83, -12, 119, -57, -128, -98
    };
}
