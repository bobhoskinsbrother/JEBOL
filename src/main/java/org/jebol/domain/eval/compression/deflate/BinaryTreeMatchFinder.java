package org.jebol.domain.eval.compression.deflate;

import java.util.Arrays;

final class BinaryTreeMatchFinder extends MatchFinder {

    static final int REQUIRED_BYTES = 5;

    private static final int HASH3_ORDER = 16;

    private static final int HASH3_WAYS = 2;

    private static final int HASH4_ORDER = 16;

    private static final int SHORTEST_RECORDED = 3;

    private final short[] hash3Table = new short[(1 << HASH3_ORDER) * HASH3_WAYS];

    private final short[] hash4Table = new short[1 << HASH4_ORDER];

    private final short[] childTable = new short[2 * DeflateTables.WINDOW_SIZE];

    private int nextHash3;

    private int nextHash4;

    BinaryTreeMatchFinder() {
        Arrays.fill(hash3Table, NO_POSITION);
        Arrays.fill(hash4Table, NO_POSITION);
    }

    void slideWindow() {
        rebase(hash3Table);
        rebase(hash4Table);
        rebase(childTable);
    }

    int matchesAt(byte[] input, int base, int position, int maxLength, int niceLength, int maxSearchDepth,
            MatchCache cache, int cacheAt) {
        return advanceOneByte(input, base, position, maxLength, niceLength, maxSearchDepth, cache, cacheAt, true);
    }

    void skipByte(byte[] input, int base, int position, int niceLength, int maxSearchDepth, MatchCache cache) {
        advanceOneByte(input, base, position, niceLength, niceLength, maxSearchDepth, cache, 0, false);
    }

    private int leftChildOf(int node) {
        return 2 * (node & WINDOW_MASK);
    }

    private int rightChildOf(int node) {
        return 2 * (node & WINDOW_MASK) + 1;
    }

    private int advanceOneByte(byte[] input, int base, int position, int maxLength, int niceLength,
            int maxSearchDepth, MatchCache cache, int cacheFrom, boolean recording) {
        int cacheAt = cacheFrom;
        int next = base + position;
        int depthRemaining = maxSearchDepth;
        int cutoff = position - DeflateTables.WINDOW_SIZE;
        int bestLength = SHORTEST_RECORDED;
        int nextSequence = load32(input, next + 1);
        int bucket3 = nextHash3 * HASH3_WAYS;
        int hash4 = nextHash4;
        nextHash3 = hash(lowThreeBytes(nextSequence), HASH3_ORDER);
        nextHash4 = hash(nextSequence, HASH4_ORDER);
        int node = hash3Table[bucket3];
        hash3Table[bucket3] = (short) position;
        int secondNode = hash3Table[bucket3 + 1];
        hash3Table[bucket3 + 1] = (short) node;
        if (recording && node > cutoff) {
            int sequence3 = load24(input, next);
            if (sequence3 == load24(input, base + node)) {
                cache.record(cacheAt++, SHORTEST_RECORDED, next - (base + node));
            } else if (secondNode > cutoff && sequence3 == load24(input, base + secondNode)) {
                cache.record(cacheAt++, SHORTEST_RECORDED, next - (base + secondNode));
            }
        }
        node = hash4Table[hash4];
        hash4Table[hash4] = (short) position;
        int pendingLess = leftChildOf(position);
        int pendingGreater = rightChildOf(position);
        if (node <= cutoff) {
            childTable[pendingLess] = NO_POSITION;
            childTable[pendingGreater] = NO_POSITION;
            return cacheAt;
        }
        int bestLessLength = 0;
        int bestGreaterLength = 0;
        int length = 0;
        while (true) {
            int match = base + node;
            if (input[match + length] == input[next + length]) {
                length = extend(input, next, match, length + 1, maxLength);
                if (!recording || length > bestLength) {
                    if (recording) {
                        bestLength = length;
                        cache.record(cacheAt++, length, next - match);
                    }
                    if (length >= niceLength) {
                        childTable[pendingLess] = childTable[leftChildOf(node)];
                        childTable[pendingGreater] = childTable[rightChildOf(node)];
                        return cacheAt;
                    }
                }
            }
            if ((input[match + length] & 0xFF) < (input[next + length] & 0xFF)) {
                childTable[pendingLess] = (short) node;
                pendingLess = rightChildOf(node);
                node = childTable[pendingLess];
                bestLessLength = length;
                if (bestGreaterLength < length) {
                    length = bestGreaterLength;
                }
            } else {
                childTable[pendingGreater] = (short) node;
                pendingGreater = leftChildOf(node);
                node = childTable[pendingGreater];
                bestGreaterLength = length;
                if (bestLessLength < length) {
                    length = bestLessLength;
                }
            }
            if (node <= cutoff || --depthRemaining == 0) {
                childTable[pendingLess] = NO_POSITION;
                childTable[pendingGreater] = NO_POSITION;
                return cacheAt;
            }
        }
    }
}
