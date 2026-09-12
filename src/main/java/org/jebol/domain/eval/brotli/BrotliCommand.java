package org.jebol.domain.eval.brotli;

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

    private int[] copyLengthAndItsCodeDeltaInTheTopSevenBits = new int[0];
    private int[] distanceExtra = new int[0];
    private int[] commandPrefix = new int[0];

    private int[] distancePrefixAndItsExtraBitsInTheTopSixBits = new int[0];

    BrotliCommand(int room) {
        grow(room);
    }

    private void grow(int wanted) {
        if (wanted <= room) {
            return;
        }
        room = Math.max(wanted, room * 2);
        insertLength = java.util.Arrays.copyOf(insertLength, room);
        copyLengthAndItsCodeDeltaInTheTopSevenBits = java.util.Arrays.copyOf(
                copyLengthAndItsCodeDeltaInTheTopSevenBits, room);
        distanceExtra = java.util.Arrays.copyOf(distanceExtra, room);
        commandPrefix = java.util.Arrays.copyOf(commandPrefix, room);
        distancePrefixAndItsExtraBitsInTheTopSixBits = java.util.Arrays.copyOf(
                distancePrefixAndItsExtraBitsInTheTopSixBits, room);
    }

    int count() {
        return count;
    }

    void clear() {
        count = 0;
    }

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
        return distancePrefixAndItsExtraBitsInTheTopSixBits[which];
    }

    void commandPrefixIs(int which, int prefix) {
        commandPrefix[which] = prefix;
    }

    int copyLengthAt(int which) {
        return copyLengthAndItsCodeDeltaInTheTopSevenBits[which] & 0x1FFFFFF;
    }

    int copyLengthCodeAt(int which) {
        int modifier = copyLengthAndItsCodeDeltaInTheTopSevenBits[which] >>> 25;
        int delta = (byte) (modifier | ((modifier & 0x40) << 1));
        return (copyLengthAndItsCodeDeltaInTheTopSevenBits[which] & 0x1FFFFFF) + delta;
    }

    void copyLengthGrows(int which) {
        copyLengthAndItsCodeDeltaInTheTopSevenBits[which]++;
    }

    int copyLengthPlusItsModifierReadPlainlyAsTheCReadsIt(int which) {
        return (copyLengthAndItsCodeDeltaInTheTopSevenBits[which] & 0x1FFFFFF)
                + (copyLengthAndItsCodeDeltaInTheTopSevenBits[which] >>> 25);
    }

    int distanceContextAt(int which) {
        int row = commandPrefix[which] >> 6;
        int column = commandPrefix[which] & 7;
        if ((row == 0 || row == 2 || row == 4 || row == 7) && column <= 2) {
            return column;
        }
        return 3;
    }

    void distancePrefixIs(int which, int prefix) {
        distancePrefixAndItsExtraBitsInTheTopSixBits[which] = prefix;
    }

    void distanceExtraIs(int which, int extra) {
        distanceExtra[which] = extra;
    }

    void add(int insertLengthGiven, int copyLengthGiven, int copyCodeDelta,
            int distanceCode, int directCodes, int postfixBits) {

        grow(count + 1);
        int delta = copyCodeDelta & 0xFF;
        insertLength[count] = insertLengthGiven;
        copyLengthAndItsCodeDeltaInTheTopSevenBits[count] =
                copyLengthGiven | (delta << 25);
        long encoded = encodedDistance(distanceCode, directCodes, postfixBits);
        distancePrefixAndItsExtraBitsInTheTopSixBits[count] = (int) (encoded >>> 32);
        distanceExtra[count] = (int) encoded;
        commandPrefix[count] = lengthCode(insertLengthGiven,
                copyLengthGiven + copyCodeDelta,
                (distancePrefixAndItsExtraBitsInTheTopSixBits[count] & 0x3FF) == 0);
        count++;
    }

    void addInsertOnlyWhichIsHowAMetaBlockEnds(int insertLengthGiven) {
        grow(count + 1);
        insertLength[count] = insertLengthGiven;
        copyLengthAndItsCodeDeltaInTheTopSevenBits[count] = 4 << 25;
        distanceExtra[count] = 0;
        distancePrefixAndItsExtraBitsInTheTopSixBits[count] = DISTANCE_SHORT_CODES;
        commandPrefix[count] = lengthCode(insertLengthGiven, 4, false);
        count++;
    }

    int restoredDistanceCodeAt(int which, int directCodes, int postfixBits) {
        int code = distancePrefixAndItsExtraBitsInTheTopSixBits[which] & 0x3FF;
        if (code < DISTANCE_SHORT_CODES + directCodes) {
            return code;
        }
        int width = distancePrefixAndItsExtraBitsInTheTopSixBits[which] >>> 10;
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

    static int lengthCode(int insertLength, int copyLength,
            boolean useLastDistance) {

        return combinedLengthCode(insertLengthCode(insertLength),
                copyLengthCode(copyLength), useLastDistance);
    }

    static int combinedLengthCode(int insertCode, int copyCode,
            boolean useLastDistance) {

        int low = (copyCode & 7) | ((insertCode & 7) << 3);
        if (useLastDistance && insertCode < 8 && copyCode < 16) {
            return copyCode < 8 ? low : low | 64;
        }
        int offset = 2 * ((copyCode >> 3) + 3 * (insertCode >> 3));
        offset = (offset << 5) + 0x40 + ((NINE_OFFSET_MULTIPLIERS >> offset) & 0xC0);
        return offset | low;
    }

    private static final int NINE_OFFSET_MULTIPLIERS = 0x520D40;

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
