package org.jebol.domain.eval.compression.deflate;

import java.util.Arrays;

final class HashTableMatchFinder extends MatchFinder {

    static final int REQUIRED_BYTES = 5;

    private static final int HASH_ORDER = 15;

    private static final int BUCKET_SIZE = 2;

    private static final int MIN_MATCH_LENGTH = 4;

    private final short[] table = new short[(1 << HASH_ORDER) * BUCKET_SIZE];

    private int base;

    private int nextHash;

    private int offset;

    HashTableMatchFinder() {
        Arrays.fill(table, NO_POSITION);
    }

    int offset() {
        return offset;
    }

    int longestMatch(byte[] input, int next, int maxLength, int niceLength) {
        int bestLength = 0;
        int bestMatch = next;
        int position = next - base;
        if (position == DeflateTables.WINDOW_SIZE) {
            rebase(table);
            base += DeflateTables.WINDOW_SIZE;
            position = 0;
        }
        int cutoff = cutoffFor(position);
        int bucket = nextHash * BUCKET_SIZE;
        nextHash = hash(load32(input, next + 1), HASH_ORDER);
        int sequence = load32(input, next);
        search:
        {
            int node = table[bucket];
            table[bucket] = (short) position;
            if (node <= cutoff) {
                break search;
            }
            int match = base + node;
            int older = table[bucket + 1];
            table[bucket + 1] = (short) node;
            if (load32(input, match) == sequence) {
                bestLength = extend(input, next, match, MIN_MATCH_LENGTH, maxLength);
                bestMatch = match;
                if (older <= cutoff || bestLength >= niceLength) {
                    break search;
                }
                match = base + older;
                if (load32(input, match) == sequence
                        && load32(input, match + bestLength - 3) == load32(input, next + bestLength - 3)) {
                    int length = extend(input, next, match, MIN_MATCH_LENGTH, maxLength);
                    if (length > bestLength) {
                        bestLength = length;
                        bestMatch = match;
                    }
                }
            } else {
                if (older <= cutoff) {
                    break search;
                }
                match = base + older;
                if (load32(input, match) == sequence) {
                    bestLength = extend(input, next, match, MIN_MATCH_LENGTH, maxLength);
                    bestMatch = match;
                }
            }
        }
        offset = next - bestMatch;
        return bestLength;
    }

    void skipBytes(byte[] input, int next, int end, int count) {
        int position = next - base;
        if (count + REQUIRED_BYTES > end - next) {
            return;
        }
        if (position + count - 1 >= DeflateTables.WINDOW_SIZE) {
            rebase(table);
            base += DeflateTables.WINDOW_SIZE;
            position -= DeflateTables.WINDOW_SIZE;
        }
        int hash = nextHash;
        int at = next;
        int remaining = count;
        do {
            table[hash * BUCKET_SIZE + 1] = table[hash * BUCKET_SIZE];
            table[hash * BUCKET_SIZE] = (short) position;
            hash = hash(load32(input, ++at), HASH_ORDER);
            position++;
        } while (--remaining != 0);
        nextHash = hash;
    }
}
