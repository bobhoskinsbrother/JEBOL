package org.jebol.domain.eval;

/**
 * Where LZMA looks for a repeat of what it is about to write.
 *
 * <p>{@code LzFind.c} of the LZMA SDK, dated 2017-06-10, as Rebol vendors it in
 * {@code u-lzma.c}. Two of its four finders are reachable from Rebol, because
 * {@code LzmaEnc_SetProps} pins the hash to four bytes and the level decides
 * only whether a binary tree or a hash chain holds the earlier positions:
 * levels below five use the chain and the rest use the tree.
 *
 * <p>Three hash tables sit one after another in a single array -- two bytes,
 * three bytes and four bytes wide -- and each position updates all three. The
 * two narrow ones answer at once with a match of exactly two or three bytes;
 * the wide one is the head of the chain or the root of the tree that the long
 * matches are found down.
 *
 * <p>Rebol always compresses a whole buffer it already holds, which the SDK
 * calls direct input. So there is no window to slide and no block to re-read:
 * the source array is the window, and a position in it is a position in the
 * window plus a fixed offset. The stream-reading half of the original is not
 * ported, and the fields it would have moved are not here.
 *
 * <p>Matches are reported as pairs pushed into a caller's array, each pair a
 * length and then a distance less one, in increasing order of length.
 */
final class LzmaMatchFinder {

    private static final int EMPTY_HASH_VALUE = 0;
    private static final int HASH_2_SIZE = 1 << 10;
    private static final int HASH_3_SIZE = 1 << 16;
    private static final int WHERE_THE_THREE_BYTE_HASH_STARTS = HASH_2_SIZE;
    private static final int WHERE_THE_FOUR_BYTE_HASH_STARTS =
            HASH_2_SIZE + HASH_3_SIZE;
    private static final int CRC_POLYNOMIAL = 0xEDB88320;
    private static final int MAX_HISTORY_SIZE = 7 << 29;

    private static final int[] CRC = crcTable();

    private static int[] crcTable() {
        int[] table = new int[256];
        for (int each = 0; each < 256; each++) {
            int running = each;
            for (int bit = 0; bit < 8; bit++) {
                running = (running >>> 1) ^ (CRC_POLYNOMIAL & -(running & 1));
            }
            table[each] = running;
        }
        return table;
    }

    private final byte[] source;
    private final boolean binaryTree;
    private final int cutValue;
    private final int matchMaxLen;
    private final int cyclicBufferSize;
    private final int hashMask;
    private final int[] hash;
    private final int[] son;

    private int here;
    private int position;
    private final int streamPosition;
    private int cyclicBufferPosition;
    private int lenLimit;

    LzmaMatchFinder(byte[] source, int historySize, int matchMaxLen,
            boolean binaryTree, int cutValue) {

        if (Integer.compareUnsigned(historySize, MAX_HISTORY_SIZE) > 0) {
            throw new IllegalArgumentException("history too long for LZMA");
        }
        this.source = source;
        this.binaryTree = binaryTree;
        this.cutValue = cutValue;
        this.matchMaxLen = matchMaxLen;
        this.cyclicBufferSize = asMuchHistoryAsThereCanBe(historySize, source);
        this.hashMask = hashMaskFor(historySize, source.length);
        this.hash = new int[hashMask + 1 + WHERE_THE_FOUR_BYTE_HASH_STARTS];
        this.son = new int[binaryTree ? cyclicBufferSize * 2 : cyclicBufferSize];
        this.here = 0;
        this.position = cyclicBufferSize;
        this.streamPosition = cyclicBufferSize + source.length;
        this.cyclicBufferPosition = 0;
        setLimits();
    }

    /**
     * How far back the cyclic buffer can be asked to reach.
     *
     * <p>The C sizes it at the dictionary and nothing else, so the default
     * level asks for sixteen megabytes of positions to compress fourteen bytes
     * and level nine asks for sixty-four. Nothing is ever written past the end
     * of the data, so a buffer longer than the data cannot change an answer:
     * every distance a real match has is at most how far into the data this
     * position is, and every empty hash slot is cut by the same test either
     * way. Sizing it at whichever is smaller keeps the answers and drops the
     * half-gigabyte.
     */
    private static int asMuchHistoryAsThereCanBe(int historySize, byte[] source) {
        return Math.min(historySize, source.length) + 1;
    }

    /**
     * How wide the four-byte table is, which is the history size rounded up to
     * one less than a power of two and never below 65535.
     *
     * <p>The comment in the C on the last step is "don't change it! It's
     * required for Deflate", and the size the caller asked for is cut down to
     * the size of the data when the data is smaller, which is what
     * {@code LzmaEnc_SetDataSize} is for and why compressing fourteen bytes
     * does not build a sixty-four megabyte table.
     */
    private static int hashMaskFor(int historySize, int expectedDataSize) {
        int wide = Math.min(historySize, expectedDataSize);
        if (wide != 0) {
            wide--;
        }
        wide |= wide >>> 1;
        wide |= wide >>> 2;
        wide |= wide >>> 4;
        wide |= wide >>> 8;
        wide >>>= 1;
        wide |= 0xFFFF;
        return wide > (1 << 24) ? wide >>> 1 : wide;
    }

    int availableBytes() {
        return streamPosition - position;
    }

    /** The index in the source of the byte this finder is looking at. */
    int currentPosition() {
        return here;
    }

    private void setLimits() {
        int available = streamPosition - position;
        lenLimit = Math.min(available, matchMaxLen);
    }

    private void step() {
        cyclicBufferPosition++;
        here++;
        position++;
        if (cyclicBufferPosition == cyclicBufferSize) {
            cyclicBufferPosition = 0;
        }
        setLimits();
    }

    /**
     * Fills {@code distances} with every match worth writing, longest last.
     *
     * <p>The answer is how many entries were written, always even. The two
     * short hashes go first because they are certain and cheap, then the wide
     * one walks the tree or the chain for anything longer.
     */
    int matches(int[] distances) {
        if (lenLimit < 4) {
            step();
            return 0;
        }
        int firstOfThree = CRC[source[here] & 0xFF] ^ (source[here + 1] & 0xFF);
        int twoByteSlot = firstOfThree & (HASH_2_SIZE - 1);
        int firstOfFour = firstOfThree ^ ((source[here + 2] & 0xFF) << 8);
        int threeByteSlot = firstOfFour & (HASH_3_SIZE - 1);
        int fourByteSlot =
                (firstOfFour ^ (CRC[source[here + 3] & 0xFF] << 5)) & hashMask;

        int twoByteDistance = position - hash[twoByteSlot];
        int threeByteDistance =
                position - hash[WHERE_THE_THREE_BYTE_HASH_STARTS + threeByteSlot];
        int chainHead = hash[WHERE_THE_FOUR_BYTE_HASH_STARTS + fourByteSlot];

        hash[twoByteSlot] = position;
        hash[WHERE_THE_THREE_BYTE_HASH_STARTS + threeByteSlot] = position;
        hash[WHERE_THE_FOUR_BYTE_HASH_STARTS + fourByteSlot] = position;

        int longest = 0;
        int written = 0;

        if (twoByteDistance < cyclicBufferSize
                && source[here - twoByteDistance] == source[here]) {
            longest = 2;
            distances[0] = 2;
            distances[1] = twoByteDistance - 1;
            written = 2;
        }
        if (twoByteDistance != threeByteDistance
                && threeByteDistance < cyclicBufferSize
                && source[here - threeByteDistance] == source[here]) {
            longest = 3;
            distances[written + 1] = threeByteDistance - 1;
            written += 2;
            twoByteDistance = threeByteDistance;
        }
        if (written != 0) {
            longest = howFarTheyAgree(twoByteDistance, longest);
            distances[written - 2] = longest;
            if (longest == lenLimit) {
                if (binaryTree) {
                    skipDownTheTree(chainHead);
                } else {
                    son[cyclicBufferPosition] = chainHead;
                }
                step();
                return written;
            }
        }
        if (longest < 3) {
            longest = 3;
        }
        int total = binaryTree
                ? matchesDownTheTree(chainHead, distances, written, longest)
                : matchesDownTheChain(chainHead, distances, written, longest);
        step();
        return total;
    }

    private int howFarTheyAgree(int distance, int from) {
        int len = from;
        while (len != lenLimit && source[here + len - distance] == source[here + len]) {
            len++;
        }
        return len;
    }

    /** Walks {@code num} positions on without reporting what is at them. */
    void skip(int num) {
        for (int each = 0; each < num; each++) {
            if (lenLimit < 4) {
                step();
                continue;
            }
            int firstOfThree = CRC[source[here] & 0xFF] ^ (source[here + 1] & 0xFF);
            int twoByteSlot = firstOfThree & (HASH_2_SIZE - 1);
            int firstOfFour = firstOfThree ^ ((source[here + 2] & 0xFF) << 8);
            int threeByteSlot = firstOfFour & (HASH_3_SIZE - 1);
            int fourByteSlot =
                    (firstOfFour ^ (CRC[source[here + 3] & 0xFF] << 5)) & hashMask;
            int chainHead = hash[WHERE_THE_FOUR_BYTE_HASH_STARTS + fourByteSlot];
            hash[twoByteSlot] = position;
            hash[WHERE_THE_THREE_BYTE_HASH_STARTS + threeByteSlot] = position;
            hash[WHERE_THE_FOUR_BYTE_HASH_STARTS + fourByteSlot] = position;
            if (binaryTree) {
                skipDownTheTree(chainHead);
            } else {
                son[cyclicBufferPosition] = chainHead;
            }
            step();
        }
    }

    private int childOf(int distance) {
        return cyclicBufferPosition - distance
                + (distance > cyclicBufferPosition ? cyclicBufferSize : 0);
    }

    private int matchesDownTheChain(
            int chainHead, int[] distances, int written, int longestSoFar) {

        int candidate = chainHead;
        int longest = longestSoFar;
        int at = written;
        son[cyclicBufferPosition] = candidate;
        int budget = cutValue;
        while (true) {
            int distance = position - candidate;
            if (budget-- == 0 || distance >= cyclicBufferSize) {
                return at;
            }
            int earlier = here - distance;
            candidate = son[childOf(distance)];
            if (source[earlier + longest] == source[here + longest]
                    && source[earlier] == source[here]) {
                int len = 0;
                while (++len != lenLimit) {
                    if (source[earlier + len] != source[here + len]) {
                        break;
                    }
                }
                if (longest < len) {
                    longest = len;
                    distances[at++] = len;
                    distances[at++] = distance - 1;
                    if (len == lenLimit) {
                        return at;
                    }
                }
            }
        }
    }

    /**
     * The binary tree walk, which splits the chain by what sorts before and
     * after the bytes at this position and rebuilds both halves as it goes.
     *
     * <p>{@code ptr0} and {@code ptr1} are where the two halves are being
     * written back, and the loop leaves the tree in order for the next
     * position to walk.
     */
    private int matchesDownTheTree(
            int chainHead, int[] distances, int written, int longestSoFar) {

        int candidate = chainHead;
        int longest = longestSoFar;
        int at = written;
        int greaterSide = (cyclicBufferPosition << 1) + 1;
        int lesserSide = cyclicBufferPosition << 1;
        int agreedLesser = 0;
        int agreedGreater = 0;
        int budget = cutValue;
        while (true) {
            int distance = position - candidate;
            if (budget-- == 0 || distance >= cyclicBufferSize) {
                son[greaterSide] = EMPTY_HASH_VALUE;
                son[lesserSide] = EMPTY_HASH_VALUE;
                return at;
            }
            int pair = childOf(distance) << 1;
            int earlier = here - distance;
            int len = Math.min(agreedLesser, agreedGreater);
            if (source[earlier + len] == source[here + len]) {
                if (++len != lenLimit
                        && source[earlier + len] == source[here + len]) {
                    while (++len != lenLimit) {
                        if (source[earlier + len] != source[here + len]) {
                            break;
                        }
                    }
                }
                if (longest < len) {
                    longest = len;
                    distances[at++] = len;
                    distances[at++] = distance - 1;
                    if (len == lenLimit) {
                        son[lesserSide] = son[pair];
                        son[greaterSide] = son[pair + 1];
                        return at;
                    }
                }
            }
            if ((source[earlier + len] & 0xFF) < (source[here + len] & 0xFF)) {
                son[lesserSide] = candidate;
                lesserSide = pair + 1;
                candidate = son[lesserSide];
                agreedGreater = len;
            } else {
                son[greaterSide] = candidate;
                greaterSide = pair;
                candidate = son[greaterSide];
                agreedLesser = len;
            }
        }
    }

    private void skipDownTheTree(int chainHead) {
        int candidate = chainHead;
        int greaterSide = (cyclicBufferPosition << 1) + 1;
        int lesserSide = cyclicBufferPosition << 1;
        int agreedLesser = 0;
        int agreedGreater = 0;
        int budget = cutValue;
        while (true) {
            int distance = position - candidate;
            if (budget-- == 0 || distance >= cyclicBufferSize) {
                son[greaterSide] = EMPTY_HASH_VALUE;
                son[lesserSide] = EMPTY_HASH_VALUE;
                return;
            }
            int pair = childOf(distance) << 1;
            int earlier = here - distance;
            int len = Math.min(agreedLesser, agreedGreater);
            if (source[earlier + len] == source[here + len]) {
                while (++len != lenLimit) {
                    if (source[earlier + len] != source[here + len]) {
                        break;
                    }
                }
                if (len == lenLimit) {
                    son[lesserSide] = son[pair];
                    son[greaterSide] = son[pair + 1];
                    return;
                }
            }
            if ((source[earlier + len] & 0xFF) < (source[here + len] & 0xFF)) {
                son[lesserSide] = candidate;
                lesserSide = pair + 1;
                candidate = son[lesserSide];
                agreedGreater = len;
            } else {
                son[greaterSide] = candidate;
                greaterSide = pair;
                candidate = son[greaterSide];
                agreedLesser = len;
            }
        }
    }
}
