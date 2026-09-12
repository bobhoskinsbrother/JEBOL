package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliQuickHasher implements BrotliHasher {

    private static final long MIXING_MULTIPLIER = 0x1FE35A7BD3579BD3L;
    private static final int SHORTEST_MATCH_WORTH_TAKING = 4;

    private static final int TOO_SMALL_TO_CLEAR_WHOLESALE_SHIFT = 5;

    private final int bucketBits;
    private final int sweep;
    private final int bucketMask;
    private final int sweepMask;
    private final int bytesHashed;
    private final boolean consultsTheDictionary;
    private final int[] buckets;
    private final BrotliDictionarySearch dictionary = new BrotliDictionarySearch();

    static BrotliQuickHasher forQuality(int quality, boolean theInputIsLarge) {
        return switch (quality) {
            case 2 -> new BrotliQuickHasher(16, 0, 5, true);
            case 3 -> new BrotliQuickHasher(16, 1, 5, false);
            case 4 -> theInputIsLarge
                    ? new BrotliQuickHasher(20, 2, 7, false)
                    : new BrotliQuickHasher(17, 2, 5, true);
            default -> throw new IllegalArgumentException(
                    "the quick hasher serves qualities two to four, not " + quality);
        };
    }

    private BrotliQuickHasher(int bucketBits, int sweepBits, int bytesHashed,
            boolean consultsTheDictionary) {

        this.bucketBits = bucketBits;
        this.sweep = 1 << sweepBits;
        this.bucketMask = (1 << bucketBits) - 1;
        this.sweepMask = (this.sweep - 1) << 3;
        this.bytesHashed = bytesHashed;
        this.consultsTheDictionary = consultsTheDictionary;
        this.buckets = new int[1 << bucketBits];
    }

    @Override
    public int howManyBytesItLooksAhead() {
        return 8;
    }

    private int slotFor(byte[] data, int at) {
        long eightBytes = 0;
        for (int which = 0; which < 8; which++) {
            eightBytes |= (long) (data[at + which] & 0xFF) << (which * 8);
        }
        long mixed = (eightBytes << (64 - 8 * bytesHashed)) * MIXING_MULTIPLIER;
        return (int) (mixed >>> (64 - bucketBits));
    }

    @Override
    public void prepareFor(byte[] data, int inputSize,
            boolean theWholeInputAtOnce) {

        if (theWholeInputAtOnce
                && inputSize <= (1 << bucketBits) >> TOO_SMALL_TO_CLEAR_WHOLESALE_SHIFT) {
            for (int at = 0; at < inputSize; at++) {
                int slot = slotFor(data, at);
                if (sweep == 1) {
                    buckets[slot] = 0;
                } else {
                    for (int which = 0; which < sweep; which++) {
                        buckets[(slot + (which << 3)) & bucketMask] = 0;
                    }
                }
            }
        } else {
            Arrays.fill(buckets, 0);
        }
    }

    @Override
    public void remember(byte[] data, int mask, int at) {
        int slot = slotFor(data, at & mask);
        if (sweep == 1) {
            buckets[slot] = at;
        } else {
            buckets[(slot + (at & sweepMask)) & bucketMask] = at;
        }
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
    }

    @Override
    public void findLongestMatch(byte[] data, int mask, int[] recentDistances,
            int at, int maxLength, int maxBackward, int dictionaryDistance,
            int maxDistance, BrotliMatch best) {

        long scoreToBeat = best.score;
        int lengthBefore = (int) best.length;
        int here = at & mask;
        int compareCharacter = data[here + lengthBefore] & 0xFF;
        int slot = slotFor(data, here);
        int bestLength = lengthBefore;
        long bestScore = best.score;
        best.lengthCodeDelta = 0;

        int cachedBackward = recentDistances[0];
        if (cachedBackward > 0 && cachedBackward <= at) {
            int previous = (at - cachedBackward) & mask;
            if (compareCharacter == (data[previous + bestLength] & 0xFF)) {
                int length = BrotliMatch.matchingBytes(data, previous, here, maxLength);
                if (length >= SHORTEST_MATCH_WORTH_TAKING) {
                    long score = BrotliMatch.scoreUsingLastDistance(length);
                    if (bestScore < score) {
                        best.length = length;
                        best.distance = cachedBackward;
                        best.score = score;
                        if (sweep == 1) {
                            buckets[slot] = at;
                            return;
                        }
                        bestLength = length;
                        bestScore = score;
                        compareCharacter = data[here + length] & 0xFF;
                    }
                }
            }
        }

        int slotToWrite = slot;
        if (sweep == 1) {
            int previousAt = buckets[slot];
            buckets[slot] = at;
            int backward = at - previousAt;
            int previous = previousAt & mask;
            if (compareCharacter != (data[previous + lengthBefore] & 0xFF)
                    || backward == 0 || backward > maxBackward) {
                return;
            }
            int length = BrotliMatch.matchingBytes(data, previous, here, maxLength);
            if (length >= SHORTEST_MATCH_WORTH_TAKING) {
                long score = BrotliMatch.scoreFor(length, backward);
                if (bestScore < score) {
                    best.length = length;
                    best.distance = backward;
                    best.score = score;
                    return;
                }
            }
        } else {
            int[] slots = new int[sweep];
            for (int which = 0; which < sweep; which++) {
                slots[which] = (slot + (which << 3)) & bucketMask;
            }
            slotToWrite = slots[(at & sweepMask) >> 3];
            for (int which = 0; which < sweep; which++) {
                int previousAt = buckets[slots[which]];
                int backward = at - previousAt;
                int previous = previousAt & mask;
                if (compareCharacter != (data[previous + bestLength] & 0xFF)
                        || backward == 0 || backward > maxBackward) {
                    continue;
                }
                int length = BrotliMatch.matchingBytes(data, previous, here, maxLength);
                if (length >= SHORTEST_MATCH_WORTH_TAKING) {
                    long score = BrotliMatch.scoreFor(length, backward);
                    if (bestScore < score) {
                        bestLength = length;
                        best.length = length;
                        compareCharacter = data[here + length] & 0xFF;
                        bestScore = score;
                        best.score = score;
                        best.distance = backward;
                    }
                }
            }
        }

        if (consultsTheDictionary && scoreToBeat == best.score) {
            dictionary.searchFrom(data, here, maxLength, dictionaryDistance,
                    maxDistance, best, true);
        }
        if (sweep != 1) {
            buckets[slotToWrite] = at;
        }
    }
}
