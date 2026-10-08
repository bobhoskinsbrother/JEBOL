package org.jebol.domain.eval.compression.deflate;

import java.util.Arrays;

final class HashChainMatchFinder extends MatchFinder {

    private static final int HASH3_ORDER = 15;

    private static final int HASH4_ORDER = 16;

    private static final int REQUIRED_BYTES = 5;

    private static final int FOUR_BYTES = 4;

    private final short[] hash3Table = new short[1 << HASH3_ORDER];

    private final short[] hash4Table = new short[1 << HASH4_ORDER];

    private final short[] nextTable = new short[DeflateTables.WINDOW_SIZE];

    private int base;

    private int nextHash3;

    private int nextHash4;

    private int offset;

    HashChainMatchFinder() {
        Arrays.fill(hash3Table, NO_POSITION);
        Arrays.fill(hash4Table, NO_POSITION);
    }

    int offset() {
        return offset;
    }

    private void slideWindow() {
        rebase(hash3Table);
        rebase(hash4Table);
        rebase(nextTable);
        base += DeflateTables.WINDOW_SIZE;
    }

    int longestMatch(byte[] input, int next, int lengthToBeat, int maxLength, int niceLength, int maxSearchDepth) {
        int bestLength = lengthToBeat;
        int bestMatch = next;
        int depthRemaining = maxSearchDepth;
        int position = next - base;
        if (position == DeflateTables.WINDOW_SIZE) {
            slideWindow();
            position = 0;
        }
        int cutoff = cutoffFor(position);
        search:
        {
            if (maxLength < REQUIRED_BYTES) {
                break search;
            }
            int node3 = hash3Table[nextHash3];
            int node4 = hash4Table[nextHash4];
            hash3Table[nextHash3] = (short) position;
            hash4Table[nextHash4] = (short) position;
            nextTable[position] = (short) node4;
            int nextSequence = load32(input, next + 1);
            nextHash3 = hash(lowThreeBytes(nextSequence), HASH3_ORDER);
            nextHash4 = hash(nextSequence, HASH4_ORDER);
            int match;
            if (bestLength < FOUR_BYTES) {
                if (node3 <= cutoff) {
                    break search;
                }
                int sequence4 = load32(input, next);
                if (bestLength < 3) {
                    match = base + node3;
                    if (load24(input, match) == lowThreeBytes(sequence4)) {
                        bestLength = 3;
                        bestMatch = match;
                    }
                }
                if (node4 <= cutoff) {
                    break search;
                }
                while (true) {
                    match = base + node4;
                    if (load32(input, match) == sequence4) {
                        break;
                    }
                    node4 = nextTable[node4 & WINDOW_MASK];
                    if (node4 <= cutoff || --depthRemaining == 0) {
                        break search;
                    }
                }
                bestMatch = match;
                bestLength = extend(input, next, bestMatch, FOUR_BYTES, maxLength);
                if (bestLength >= niceLength) {
                    break search;
                }
                node4 = nextTable[node4 & WINDOW_MASK];
                if (node4 <= cutoff || --depthRemaining == 0) {
                    break search;
                }
            } else if (node4 <= cutoff || bestLength >= niceLength) {
                break search;
            }
            while (true) {
                while (true) {
                    match = base + node4;
                    if (load32(input, match + bestLength - 3) == load32(input, next + bestLength - 3)
                            && load32(input, match) == load32(input, next)) {
                        break;
                    }
                    node4 = nextTable[node4 & WINDOW_MASK];
                    if (node4 <= cutoff || --depthRemaining == 0) {
                        break search;
                    }
                }
                int length = extend(input, next, match, FOUR_BYTES, maxLength);
                if (length > bestLength) {
                    bestLength = length;
                    bestMatch = match;
                    if (bestLength >= niceLength) {
                        break search;
                    }
                }
                node4 = nextTable[node4 & WINDOW_MASK];
                if (node4 <= cutoff || --depthRemaining == 0) {
                    break search;
                }
            }
        }
        offset = next - bestMatch;
        return bestLength;
    }

    void skipBytes(byte[] input, int next, int end, int count) {
        if (count + REQUIRED_BYTES > end - next) {
            return;
        }
        int position = next - base;
        int hash3 = nextHash3;
        int hash4 = nextHash4;
        int at = next;
        int remaining = count;
        do {
            if (position == DeflateTables.WINDOW_SIZE) {
                slideWindow();
                position = 0;
            }
            hash3Table[hash3] = (short) position;
            nextTable[position] = hash4Table[hash4];
            hash4Table[hash4] = (short) position;
            int nextSequence = load32(input, ++at);
            hash3 = hash(lowThreeBytes(nextSequence), HASH3_ORDER);
            hash4 = hash(nextSequence, HASH4_ORDER);
            position++;
        } while (--remaining != 0);
        nextHash3 = hash3;
        nextHash4 = hash4;
    }
}
