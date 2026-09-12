package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliBinaryTreeHasher {

    private static final int MIXING_MULTIPLIER = 0x1E35A7BD;
    private static final int BUCKET_BITS = 17;
    private static final int HOW_FAR_DOWN_THE_TREE_TO_WALK = 64;

    static final int LONGEST_COMPARED = 128;

    static final int MOST_MATCHES_AT_ONE_POSITION = 128;

    private final int windowMask;

    private final int nowhereWhichReadsAsNegativeSoEveryCompareMustAllowForIt;
    private final int[] buckets = new int[1 << BUCKET_BITS];
    private final int[] forest;

    BrotliBinaryTreeHasher(int windowBits, int howMuchInputThereIs,
            boolean theWholeInputAtOnce) {

        this.windowMask = (1 << windowBits) - 1;
        this.nowhereWhichReadsAsNegativeSoEveryCompareMustAllowForIt = -windowMask;
        int positions = 1 << windowBits;
        if (theWholeInputAtOnce && howMuchInputThereIs < positions) {
            positions = howMuchInputThereIs;
        }
        this.forest = new int[2 * Math.max(positions, 1)];
        Arrays.fill(buckets,
                nowhereWhichReadsAsNegativeSoEveryCompareMustAllowForIt);
    }

    static int howManyBytesItLooksAhead() {
        return LONGEST_COMPARED;
    }

    private int slotFor(byte[] data, int at) {
        int fourBytes = (data[at] & 0xFF)
                | ((data[at + 1] & 0xFF) << 8)
                | ((data[at + 2] & 0xFF) << 16)
                | ((data[at + 3] & 0xFF) << 24);
        return (fourBytes * MIXING_MULTIPLIER) >>> (32 - BUCKET_BITS);
    }

    private int leftChildOf(int position) {
        return 2 * (position & windowMask);
    }

    private int rightChildOf(int position) {
        return 2 * (position & windowMask) + 1;
    }

    private int walkRecordingMatchesAndReRootingOrMerelyStoringWhereThereIsNoRoom(byte[] data, int at, int mask, int maxLength,
            int maxBackward, int[] bestLengthSoFar, BrotliMatches into,
            int writtenSoFar) {

        int here = at & mask;
        int mostToCompare = Math.min(maxLength, LONGEST_COMPARED);
        boolean shouldReRoot = maxLength >= LONGEST_COMPARED;
        int slot = slotFor(data, here);
        int previousAt = buckets[slot];
        int nodeLeft = leftChildOf(at);
        int nodeRight = rightChildOf(at);
        int bestLeft = 0;
        int bestRight = 0;
        int written = writtenSoFar;

        if (shouldReRoot) {
            buckets[slot] = at;
        }
        for (int depthLeft = HOW_FAR_DOWN_THE_TREE_TO_WALK; ; depthLeft--) {
            long backward = (long) at - Integer.toUnsignedLong(previousAt);
            int previous = previousAt & mask;
            if (backward <= 0 || backward > maxBackward || depthLeft == 0) {
                if (shouldReRoot) {
                    forest[nodeLeft] =
                            nowhereWhichReadsAsNegativeSoEveryCompareMustAllowForIt;
                    forest[nodeRight] =
                            nowhereWhichReadsAsNegativeSoEveryCompareMustAllowForIt;
                }
                break;
            }
            int alreadyAgreed = Math.min(bestLeft, bestRight);
            int length = alreadyAgreed + BrotliMatch.matchingBytes(data,
                    previous + alreadyAgreed, here + alreadyAgreed,
                    maxLength - alreadyAgreed);
            if (into != null && length > bestLengthSoFar[0]) {
                bestLengthSoFar[0] = length;
                into.addCopy(written++, backward, length);
            }
            if (length >= mostToCompare) {
                if (shouldReRoot) {
                    forest[nodeLeft] = forest[leftChildOf(previousAt)];
                    forest[nodeRight] = forest[rightChildOf(previousAt)];
                }
                break;
            }
            if ((data[here + length] & 0xFF) > (data[previous + length] & 0xFF)) {
                bestLeft = length;
                if (shouldReRoot) {
                    forest[nodeLeft] = previousAt;
                }
                nodeLeft = rightChildOf(previousAt);
                previousAt = forest[nodeLeft];
            } else {
                bestRight = length;
                if (shouldReRoot) {
                    forest[nodeRight] = previousAt;
                }
                nodeRight = leftChildOf(previousAt);
                previousAt = forest[nodeRight];
            }
        }
        return written;
    }

    int findAll(byte[] data, int mask, int at, int maxLength, int maxBackward,
            int dictionaryDistance, int furthestDistanceAllowed,
            boolean atTheTopLevel, BrotliMatches into) {

        int here = at & mask;
        int[] bestLength = {1};
        int written = 0;
        int howFarBackToScanPlainly = atTheTopLevel ? 64 : 16;
        int stop = at < howFarBackToScanPlainly ? 0 : at - howFarBackToScanPlainly;
        for (int back = at - 1; back > stop && bestLength[0] <= 2; back--) {
            int backward = at - back;
            if (backward > maxBackward) {
                break;
            }
            int previous = back & mask;
            if (data[here] != data[previous] || data[here + 1] != data[previous + 1]) {
                continue;
            }
            int length = BrotliMatch.matchingBytes(data, previous, here, maxLength);
            if (length > bestLength[0]) {
                bestLength[0] = length;
                into.addCopy(written++, backward, length);
            }
        }
        if (bestLength[0] < maxLength) {
            written = walkRecordingMatchesAndReRootingOrMerelyStoringWhereThereIsNoRoom(
                    data, at, mask, maxLength, maxBackward, bestLength,
                    into, written);
        }
        return written + dictionaryMatches(data, here, maxLength, bestLength[0],
                dictionaryDistance, furthestDistanceAllowed, into, written);
    }

    private static int dictionaryMatches(byte[] data, int here, int maxLength,
            int bestSoFar, int dictionaryDistance, int furthestDistanceAllowed,
            BrotliMatches into, int writtenSoFar) {

        int[] found = new int[BrotliDictionaryMatches.LONGEST_MATCH + 1];
        Arrays.fill(found, BrotliDictionaryMatches.NOTHING_FOUND_WHICH_IS_SEVEN_FS_NOT_EIGHT);
        int shortest = Math.max(4, bestSoFar + 1);
        if (!BrotliDictionaryMatches.findAll(data, here, shortest, maxLength, found)) {
            return 0;
        }
        int longest = Math.min(BrotliDictionaryMatches.LONGEST_MATCH, maxLength);
        int written = 0;
        for (int length = shortest; length <= longest; length++) {
            int packed = found[length];
            if (Integer.compareUnsigned(packed,
                    BrotliDictionaryMatches.NOTHING_FOUND_WHICH_IS_SEVEN_FS_NOT_EIGHT) >= 0) {
                continue;
            }
            long distance = dictionaryDistance + (packed >>> 5) + 1;
            if (distance <= furthestDistanceAllowed) {
                into.addWord(writtenSoFar + written, distance, length, packed & 31);
                written++;
            }
        }
        return written;
    }

    void remember(byte[] data, int mask, int at) {
        int furthestBack = windowMask - 16 + 1;
        walkRecordingMatchesAndReRootingOrMerelyStoringWhereThereIsNoRoom(
                data, at, mask, LONGEST_COMPARED, furthestBack, null, null, 0);
    }

    void rememberRange(byte[] data, int mask, int from, int until) {
        int at = from;
        int sampleUntil = from;
        if (from + 63 <= until) {
            at = until - 63;
        }
        if (from + 512 <= at) {
            for (; sampleUntil < at; sampleUntil += 8) {
                remember(data, mask, sampleUntil);
            }
        }
        for (; at < until; at++) {
            remember(data, mask, at);
        }
    }

    void stitchToPreviousBlock(byte[] data, int mask, int howManyBytes,
            int position) {

        if (howManyBytes < 3 || position < LONGEST_COMPARED) {
            return;
        }
        int from = position - LONGEST_COMPARED + 1;
        int until = Math.min(position, from + howManyBytes);
        for (int at = from; at < until; at++) {
            int furthestBack = windowMask - Math.max(15, position - at);
            walkRecordingMatchesAndReRootingOrMerelyStoringWhereThereIsNoRoom(
                data, at, mask, LONGEST_COMPARED, furthestBack, null, null, 0);
        }
    }
}
