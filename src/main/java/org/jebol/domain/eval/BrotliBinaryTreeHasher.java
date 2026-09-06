package org.jebol.domain.eval;

import java.util.Arrays;

/**
 * Every match at a position, not just the best one, for the two levels that
 * price them all.
 *
 * <p>{@code hash_to_binary_tree_inc.h}, the C's H10. Each hash owns a binary
 * search tree of the positions whose next four bytes hash the same way, ordered
 * by what the bytes there say rather than by where they are. Walking that tree
 * from a new position visits candidates in order of how well they match, so the
 * walk can stop early and every match it passes is worth recording.
 *
 * <p>The walk rebuilds the tree as it goes. The new position becomes the root
 * and everything it passed is hung underneath on the side it belongs, so the
 * tree stays sorted without a second pass. That only happens when there are a
 * hundred and twenty eight bytes left to compare: a position with fewer cannot
 * be placed, because where it belongs depends on bytes that have not arrived.
 *
 * <p>The tree lives in one flat array of two entries per position, the left and
 * right children of whatever sits at that position in the window. Nothing is
 * ever removed; a position that has fallen out of the window is recognised by
 * being too far back rather than by being deleted.
 */
final class BrotliBinaryTreeHasher {

    private static final int MIXING_MULTIPLIER = 0x1E35A7BD;
    private static final int BUCKET_BITS = 17;
    private static final int HOW_FAR_DOWN_THE_TREE_TO_WALK = 64;

    /** How many bytes are compared before two positions are called alike. */
    static final int LONGEST_COMPARED = 128;

    /** Sixty-four from the near scan plus one per level of the tree walk. */
    static final int MOST_MATCHES_AT_ONE_POSITION = 128;

    private final int windowMask;

    /**
     * What an empty branch of the tree holds.
     *
     * <p>The C spells it as an unsigned position so far past the end that the
     * distance to it always fails the too-far test. Held here as the same bits,
     * which read as a negative number, so every comparison against it has to be
     * made unsigned or made to treat a negative distance as too far -- which is
     * what the walk below does.
     */
    private final int nowhere;
    private final int[] buckets = new int[1 << BUCKET_BITS];
    private final int[] forest;

    BrotliBinaryTreeHasher(int windowBits, int howMuchInputThereIs,
            boolean theWholeInputAtOnce) {

        this.windowMask = (1 << windowBits) - 1;
        this.nowhere = -windowMask;
        int positions = 1 << windowBits;
        if (theWholeInputAtOnce && howMuchInputThereIs < positions) {
            positions = howMuchInputThereIs;
        }
        this.forest = new int[2 * Math.max(positions, 1)];
        Arrays.fill(buckets, nowhere);
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

    /**
     * Walks the tree for this position, recording matches and re-rooting.
     *
     * <p>Answers how many matches were written. Passing no room for matches
     * makes it a pure store, which is what the positions inside a copy get.
     */
    private int walk(byte[] data, int at, int mask, int maxLength,
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
                    forest[nodeLeft] = nowhere;
                    forest[nodeRight] = nowhere;
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

    /**
     * Every match worth having at this position, in increasing length order.
     *
     * <p>Three sources, in this order. A plain scan of the last sixteen
     * positions -- sixty four at the top level -- which catches the very short
     * matches the tree will not, and only runs while nothing longer than two
     * bytes has been found. Then the tree. Then the dictionary, for lengths
     * longer than anything the tree turned up.
     */
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
            written = walk(data, at, mask, maxLength, maxBackward, bestLength,
                    into, written);
        }
        return written + dictionaryMatches(data, here, maxLength, bestLength[0],
                dictionaryDistance, furthestDistanceAllowed, into, written);
    }

    private static int dictionaryMatches(byte[] data, int here, int maxLength,
            int bestSoFar, int dictionaryDistance, int furthestDistanceAllowed,
            BrotliMatches into, int writtenSoFar) {

        int[] found = new int[BrotliDictionaryMatches.LONGEST_MATCH + 1];
        Arrays.fill(found, BrotliDictionaryMatches.NOTHING_FOUND);
        int shortest = Math.max(4, bestSoFar + 1);
        if (!BrotliDictionaryMatches.findAll(data, here, shortest, maxLength, found)) {
            return 0;
        }
        int longest = Math.min(BrotliDictionaryMatches.LONGEST_MATCH, maxLength);
        int written = 0;
        for (int length = shortest; length <= longest; length++) {
            int packed = found[length];
            if (Integer.compareUnsigned(packed,
                    BrotliDictionaryMatches.NOTHING_FOUND) >= 0) {
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

    /** Places a position in the tree without asking what it matches. */
    void remember(byte[] data, int mask, int at) {
        int furthestBack = windowMask - 16 + 1;
        walk(data, at, mask, LONGEST_COMPARED, furthestBack, null, null, 0);
    }

    /**
     * Places a run of positions, skipping most of a long run.
     *
     * <p>Only the last sixty-three matter for what comes next, and placing a
     * whole copy's worth would cost more than it saves, so a long run is
     * sampled every eighth position until near its end.
     */
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
            walk(data, at, mask, LONGEST_COMPARED, furthestBack, null, null, 0);
        }
    }
}
