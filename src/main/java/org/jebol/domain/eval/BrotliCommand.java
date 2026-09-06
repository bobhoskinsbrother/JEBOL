package org.jebol.domain.eval;

/**
 * One insert-and-copy step, as the encoder holds it before writing it.
 *
 * <p>{@code command.h}. A command says how many literals to insert, how long a
 * copy follows, and where the copy comes from. The format has one symbol for
 * the insert length and the copy length together -- seven hundred and four of
 * them -- so both are turned into bracket codes and then combined, and the
 * combining has a shortcut for the common case where the distance is the last
 * one used.
 *
 * <p>Held as parallel arrays rather than objects, because a meta-block has one
 * of these per match and there may be a million of them.
 *
 * <p>The distance is stored as if the meta-block used no postfix bits and no
 * direct codes. Which parameters the meta-block really uses is not decided
 * until after its histograms have been clustered, and the distance is recomputed
 * then.
 */
final class BrotliCommand {

    static final int DISTANCE_SHORT_CODES = 16;

    private static final int[] INSERT_BASE = {
            0, 1, 2, 3, 4, 5, 6, 8, 10, 14, 18, 26,
            34, 50, 66, 98, 130, 194, 322, 578, 1090, 2114, 6210, 22594,
    };
    private static final int[] INSERT_EXTRA = {
            0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3,
            4, 4, 5, 5, 6, 7, 8, 9, 10, 12, 14, 24,
    };
    private static final int[] COPY_BASE = {
            2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 18,
            22, 30, 38, 54, 70, 102, 134, 198, 326, 582, 1094, 2118,
    };
    private static final int[] COPY_EXTRA = {
            0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 2,
            3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 24,
    };

    private int room;
    private int count;

    private int[] insertLength = new int[0];

    /** The copy length, and in its top seven bits how far its code differs. */
    private int[] copyLength = new int[0];
    private int[] distanceExtra = new int[0];
    private int[] commandPrefix = new int[0];

    /** The distance code, and in its top six bits how many extra bits it has. */
    private int[] distancePrefix = new int[0];

    BrotliCommand(int room) {
        grow(room);
    }

    private void grow(int wanted) {
        if (wanted <= room) {
            return;
        }
        room = Math.max(wanted, room * 2);
        insertLength = java.util.Arrays.copyOf(insertLength, room);
        copyLength = java.util.Arrays.copyOf(copyLength, room);
        distanceExtra = java.util.Arrays.copyOf(distanceExtra, room);
        commandPrefix = java.util.Arrays.copyOf(commandPrefix, room);
        distancePrefix = java.util.Arrays.copyOf(distancePrefix, room);
    }

    int count() {
        return count;
    }

    void clear() {
        count = 0;
    }

    /**
     * Throws away everything written after a point, so a parse can be run again
     * from the same place with better costs.
     */
    void keepOnly(int howMany) {
        count = howMany;
    }

    int insertLengthAt(int which) {
        return insertLength[which];
    }

    int distanceExtraAt(int which) {
        return distanceExtra[which];
    }

    int commandPrefixAt(int which) {
        return commandPrefix[which];
    }

    int distancePrefixAt(int which) {
        return distancePrefix[which];
    }

    void commandPrefixIs(int which, int prefix) {
        commandPrefix[which] = prefix;
    }

    int copyLengthAt(int which) {
        return copyLength[which] & 0x1FFFFFF;
    }

    /**
     * The copy length as its code names it, which may differ from the length
     * actually copied.
     *
     * <p>A dictionary word is written with a length that says which word it is
     * rather than how long the transformed word came out, so the two part
     * company by a small signed amount kept in the top seven bits.
     */
    int copyLengthCodeAt(int which) {
        int modifier = copyLength[which] >>> 25;
        int delta = (byte) (modifier | ((modifier & 0x40) << 1));
        return (copyLength[which] & 0x1FFFFFF) + delta;
    }

    /** Lengthens a copy that turned out to carry on into the next block. */
    void copyLengthGrows(int which) {
        copyLength[which]++;
    }

    /**
     * The copy length plus its modifier read as a plain seven bit number.
     *
     * <p>This is not {@link #copyLengthCodeAt}, which reads the same seven bits
     * as a signed amount. The C reads them the plain way in one place only --
     * where it re-codes a command it has just lengthened -- and the two agree
     * for every command whose length was not shifted downward by a dictionary
     * word. Reproduced as the C has it rather than corrected, because the bytes
     * are what is being matched.
     */
    int copyLengthPlusItsPlainModifier(int which) {
        return (copyLength[which] & 0x1FFFFFF) + (copyLength[which] >>> 25);
    }

    /**
     * Which of four distance contexts this command's copy falls in.
     *
     * <p>Short copies after short inserts get a context of their own, which is
     * what lets a meta-block use a different distance code for them. Only the
     * two levels that cluster histograms make use of it.
     */
    int distanceContextAt(int which) {
        int row = commandPrefix[which] >> 6;
        int column = commandPrefix[which] & 7;
        if ((row == 0 || row == 2 || row == 4 || row == 7) && column <= 2) {
            return column;
        }
        return 3;
    }

    void distancePrefixIs(int which, int prefix) {
        distancePrefix[which] = prefix;
    }

    void distanceExtraIs(int which, int extra) {
        distanceExtra[which] = extra;
    }

    void add(int insertLengthGiven, int copyLengthGiven, int copyCodeDelta,
            int distanceCode, int directCodes, int postfixBits) {

        grow(count + 1);
        int delta = copyCodeDelta & 0xFF;
        insertLength[count] = insertLengthGiven;
        copyLength[count] = copyLengthGiven | (delta << 25);
        long encoded = encodedDistance(distanceCode, directCodes, postfixBits);
        distancePrefix[count] = (int) (encoded >>> 32);
        distanceExtra[count] = (int) encoded;
        commandPrefix[count] = lengthCode(insertLengthGiven,
                copyLengthGiven + copyCodeDelta,
                (distancePrefix[count] & 0x3FF) == 0);
        count++;
    }

    /** The last command of a meta-block, which inserts and copies nothing. */
    void addInsertOnly(int insertLengthGiven) {
        grow(count + 1);
        insertLength[count] = insertLengthGiven;
        copyLength[count] = 4 << 25;
        distanceExtra[count] = 0;
        distancePrefix[count] = DISTANCE_SHORT_CODES;
        commandPrefix[count] = lengthCode(insertLengthGiven, 4, false);
        count++;
    }

    /**
     * The distance the command really meant, recovered from the code.
     *
     * <p>Needed after the meta-block has chosen its postfix bits and direct
     * codes, because the code stored at the time assumed neither.
     */
    int restoredDistanceCodeAt(int which, int directCodes, int postfixBits) {
        int code = distancePrefix[which] & 0x3FF;
        if (code < DISTANCE_SHORT_CODES + directCodes) {
            return code;
        }
        int width = distancePrefix[which] >>> 10;
        int postfixMask = (1 << postfixBits) - 1;
        int high = (code - directCodes - DISTANCE_SHORT_CODES) >>> postfixBits;
        int low = (code - directCodes - DISTANCE_SHORT_CODES) & postfixMask;
        int offset = ((2 + (high & 1)) << width) - 4;
        return ((offset + distanceExtra[which]) << postfixBits) + low
                + directCodes + DISTANCE_SHORT_CODES;
    }

    static long encodedDistance(int distanceCode, int directCodes, int postfixBits) {
        if (distanceCode < DISTANCE_SHORT_CODES + directCodes) {
            return (long) distanceCode << 32;
        }
        int distance = (1 << (postfixBits + 2))
                + (distanceCode - DISTANCE_SHORT_CODES - directCodes);
        int bucket = BrotliCodes.log2Floor(distance) - 1;
        int postfixMask = (1 << postfixBits) - 1;
        int postfix = distance & postfixMask;
        int prefix = (distance >>> bucket) & 1;
        int offset = (2 + prefix) << bucket;
        int width = bucket - postfixBits;
        int code = (width << 10) | (DISTANCE_SHORT_CODES + directCodes
                + ((2 * (width - 1) + prefix) << postfixBits) + postfix);
        int extra = (distance - offset) >>> postfixBits;
        return ((long) code << 32) | (extra & 0xFFFFFFFFL);
    }

    static int insertLengthCode(int insertLength) {
        if (insertLength < 6) {
            return insertLength;
        }
        if (insertLength < 130) {
            int width = BrotliCodes.log2Floor(insertLength - 2) - 1;
            return (width << 1) + ((insertLength - 2) >> width) + 2;
        }
        if (insertLength < 2114) {
            return BrotliCodes.log2Floor(insertLength - 66) + 10;
        }
        if (insertLength < 6210) {
            return 21;
        }
        return insertLength < 22594 ? 22 : 23;
    }

    static int copyLengthCode(int copyLength) {
        if (copyLength < 10) {
            return copyLength - 2;
        }
        if (copyLength < 134) {
            int width = BrotliCodes.log2Floor(copyLength - 6) - 1;
            return (width << 1) + ((copyLength - 6) >> width) + 4;
        }
        if (copyLength < 2118) {
            return BrotliCodes.log2Floor(copyLength - 70) + 12;
        }
        return 23;
    }

    /**
     * The one symbol that says both lengths.
     *
     * <p>The magic constant is the C's, and its comment explains it: the nine
     * possible offsets are all a multiple of sixty-four, and the multipliers
     * minus their index need only two bits each, so all nine fit in one number
     * to be shifted out.
     */
    static int lengthCode(int insertLength, int copyLength,
            boolean useLastDistance) {

        return combinedLengthCode(insertLengthCode(insertLength),
                copyLengthCode(copyLength), useLastDistance);
    }

    /** The same, for a caller that already has the two bracket codes. */
    static int combinedLengthCode(int insertCode, int copyCode,
            boolean useLastDistance) {

        int low = (copyCode & 7) | ((insertCode & 7) << 3);
        if (useLastDistance && insertCode < 8 && copyCode < 16) {
            return copyCode < 8 ? low : low | 64;
        }
        int offset = 2 * ((copyCode >> 3) + 3 * (insertCode >> 3));
        offset = (offset << 5) + 0x40 + ((0x520D40 >> offset) & 0xC0);
        return offset | low;
    }

    static int insertBase(int code) {
        return INSERT_BASE[code];
    }

    static int insertExtra(int code) {
        return INSERT_EXTRA[code];
    }

    static int copyBase(int code) {
        return COPY_BASE[code];
    }

    static int copyExtra(int code) {
        return COPY_EXTRA[code];
    }
}
