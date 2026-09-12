package org.jebol.domain.eval.brotli;

sealed interface BrotliHasher
        permits BrotliQuickHasher, BrotliFullHasher {

    int howManyBytesItLooksAhead();

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
