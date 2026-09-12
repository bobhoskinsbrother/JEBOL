package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliFullHasher implements BrotliHasher {

    private static final int FOUR_BYTE_MULTIPLIER = 0x1E35A7BD;
    private static final long FIVE_BYTE_MULTIPLIER = 0x1FE35A7BD3579BD3L << 24;
    private static final int SHORTEST_MATCH_FROM_THE_TABLE = 4;

    private static final int TOO_SMALL_TO_CLEAR_WHOLESALE_SHIFT = 6;

    private final int hashShift;
    private final int bucketCount;
    private final int blockBits;
    private final int blockMask;
    private final int howManyRecentDistancesToCheck;
    private final boolean hashesFiveBytesOfEight;
    private final char[] howManySeen;
    private final int[] positions;
    private final BrotliDictionarySearch dictionary = new BrotliDictionarySearch();

    static BrotliFullHasher forQuality(int quality, boolean theInputIsLarge) {
        int bucketBits = theInputIsLarge || quality >= 7 ? 15 : 14;
        int blockBits = quality - 1;
        int recentDistances = quality < 7 ? 4 : quality < 9 ? 10 : 16;
        return new BrotliFullHasher(bucketBits, blockBits, recentDistances,
                theInputIsLarge);
    }

    private BrotliFullHasher(int bucketBits, int blockBits,
            int howManyRecentDistancesToCheck, boolean hashesFiveBytesOfEight) {

        this.hashShift = 32 - bucketBits;
        this.bucketCount = 1 << bucketBits;
        this.blockBits = blockBits;
        this.blockMask = (1 << blockBits) - 1;
        this.howManyRecentDistancesToCheck = howManyRecentDistancesToCheck;
        this.hashesFiveBytesOfEight = hashesFiveBytesOfEight;
        this.howManySeen = new char[bucketCount];
        this.positions = new int[bucketCount << blockBits];
    }

    @Override
    public int howManyBytesItLooksAhead() {
        return hashesFiveBytesOfEight ? 8 : 4;
    }

    private int slotFor(byte[] data, int at) {
        if (hashesFiveBytesOfEight) {
            long eightBytes = 0;
            for (int which = 0; which < 8; which++) {
                eightBytes |= (long) (data[at + which] & 0xFF) << (which * 8);
            }
            return (int) ((eightBytes * FIVE_BYTE_MULTIPLIER) >>> (64 - 15));
        }
        int fourBytes = (data[at] & 0xFF)
                | ((data[at + 1] & 0xFF) << 8)
                | ((data[at + 2] & 0xFF) << 16)
                | ((data[at + 3] & 0xFF) << 24);
        return (fourBytes * FOUR_BYTE_MULTIPLIER) >>> hashShift;
    }

    @Override
    public void prepareFor(byte[] data, int inputSize,
            boolean theWholeInputAtOnce) {

        if (theWholeInputAtOnce
                && inputSize <= bucketCount >> TOO_SMALL_TO_CLEAR_WHOLESALE_SHIFT) {
            for (int at = 0; at < inputSize; at++) {
                howManySeen[slotFor(data, at)] = 0;
            }
        } else {
            Arrays.fill(howManySeen, (char) 0);
        }
    }

    @Override
    public void remember(byte[] data, int mask, int at) {
        int slot = slotFor(data, at & mask);
        int within = howManySeen[slot] & blockMask;
        positions[within + (slot << blockBits)] = at;
        howManySeen[slot]++;
    }

    @Override
    public void rememberRange(byte[] data, int mask, int from, int until) {
        for (int at = from; at < until; at++) {
            remember(data, mask, at);
        }
    }

    @Override
    public void stitchToPreviousBlock(byte[] data, int mask, int howManyBytes,
            int position) {

        if (howManyBytes >= howManyBytesItLooksAhead() - 1 && position >= 3) {
            remember(data, mask, position - 3);
            remember(data, mask, position - 2);
            remember(data, mask, position - 1);
        }
    }

    @Override
    public void prepareDistanceCache(int[] recentDistances) {
        if (howManyRecentDistancesToCheck <= 4) {
            return;
        }
        int last = recentDistances[0];
        recentDistances[4] = last - 1;
        recentDistances[5] = last + 1;
        recentDistances[6] = last - 2;
        recentDistances[7] = last + 2;
        recentDistances[8] = last - 3;
        recentDistances[9] = last + 3;
        if (howManyRecentDistancesToCheck > 10) {
            int beforeLast = recentDistances[1];
            recentDistances[10] = beforeLast - 1;
            recentDistances[11] = beforeLast + 1;
            recentDistances[12] = beforeLast - 2;
            recentDistances[13] = beforeLast + 2;
            recentDistances[14] = beforeLast - 3;
            recentDistances[15] = beforeLast + 3;
        }
    }

    @Override
    public void findLongestMatch(byte[] data, int mask, int[] recentDistances,
            int at, int maxLength, int maxBackward, int dictionaryDistance,
            int maxDistance, BrotliMatch best) {

        int here = at & mask;
        long scoreToBeat = best.score;
        long bestScore = best.score;
        int bestLength = (int) best.length;
        int slot = slotFor(data, here);
        int bucketAt = slot << blockBits;
        best.length = 0;
        best.lengthCodeDelta = 0;

        for (int which = 0; which < howManyRecentDistancesToCheck; which++) {
            long backward = Integer.toUnsignedLong(recentDistances[which]);
            if (backward == 0 || backward > at || backward > maxBackward) {
                continue;
            }
            int previous = (int) ((at - backward) & mask);
            if (here + bestLength > mask) {
                break;
            }
            if (previous + bestLength > mask
                    || data[here + bestLength] != data[previous + bestLength]) {
                continue;
            }
            int length = BrotliMatch.matchingBytes(data, previous, here, maxLength);
            if (length >= 3 || (length == 2 && which < 2)) {
                long score = BrotliMatch.scoreUsingLastDistance(length);
                if (bestScore < score) {
                    if (which != 0) {
                        score -= BrotliMatch.penaltyForRecentDistance(which);
                    }
                    if (bestScore < score) {
                        bestScore = score;
                        bestLength = length;
                        best.length = length;
                        best.distance = backward;
                        best.score = score;
                    }
                }
            }
        }

        if (bestLength < 3) {
            bestLength = 3;
        }

        int seen = howManySeen[slot];
        int oldestWorthWalking = seen > (1 << blockBits) ? seen - (1 << blockBits) : 0;
        for (int which = seen; which > oldestWorthWalking; ) {
            int previousAt = positions[bucketAt + ((--which) & blockMask)];
            long backward = (long) at - Integer.toUnsignedLong(previousAt);
            if (backward > maxBackward) {
                break;
            }
            int previous = previousAt & mask;
            if (here + bestLength > mask) {
                break;
            }
            if (previous + bestLength > mask
                    || !fourBytesAgree(data, here + bestLength - 3,
                            previous + bestLength - 3)) {
                continue;
            }
            int length;
            if (hashesFiveBytesOfEight) {
                if (!fourBytesAgree(data, here, previous)) {
                    continue;
                }
                length = BrotliMatch.matchingBytes(data, previous + 4, here + 4,
                        maxLength - 4) + 4;
            } else {
                length = BrotliMatch.matchingBytes(data, previous, here, maxLength);
                if (length < SHORTEST_MATCH_FROM_THE_TABLE) {
                    continue;
                }
            }
            long score = BrotliMatch.scoreFor(length, backward);
            if (bestScore < score) {
                bestScore = score;
                bestLength = length;
                best.length = length;
                best.distance = backward;
                best.score = score;
            }
        }
        positions[bucketAt + (howManySeen[slot] & blockMask)] = at;
        howManySeen[slot]++;

        if (scoreToBeat == best.score) {
            dictionary.searchFrom(data, here, maxLength, dictionaryDistance,
                    maxDistance, best, false);
        }
    }

    private static boolean fourBytesAgree(byte[] data, int first, int second) {
        return data[first] == data[second]
                && data[first + 1] == data[second + 1]
                && data[first + 2] == data[second + 2]
                && data[first + 3] == data[second + 3];
    }
}
