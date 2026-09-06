package org.jebol.domain.eval;

/**
 * What the search for backward references needs of a hash table.
 *
 * <p>The C makes this a template and compiles the reference finder once per
 * hasher. There are two here -- the quick one for qualities two to four and the
 * chained one for five to nine -- and they differ in how far they look ahead as
 * well as in what they find, so the finder asks rather than assumes.
 */
sealed interface BrotliHasher
        permits BrotliQuickHasher, BrotliFullHasher {

    /**
     * How many bytes past a position its hash reads.
     *
     * <p>Also how many positions at the end of a block cannot be hashed yet,
     * which is why a block stitches the last few onto the next one.
     */
    int howManyBytesItLooksAhead();

    /**
     * Clears the table, once, before the first block is searched.
     *
     * <p>An input that arrives whole and fits in one block clears only the slots
     * it could possibly reach; anything else clears the lot. The distinction
     * changes the answer, not just the speed, because a slot left holding a
     * stale position is a match that would not otherwise be found.
     */
    void prepareFor(byte[] data, int inputSize, boolean theWholeInputAtOnce);

    void remember(byte[] data, int mask, int at);

    void rememberRange(byte[] data, int mask, int from, int until);

    void stitchToPreviousBlock(byte[] data, int mask, int howManyBytes,
            int position);

    void prepareDistanceCache(int[] recentDistances);

    void findLongestMatch(byte[] data, int mask, int[] recentDistances,
            int at, int maxLength, int maxBackward, int dictionaryDistance,
            int maxDistance, BrotliMatch best);
}
