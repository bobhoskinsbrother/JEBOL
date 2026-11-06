package org.jebol.domain.eval.compression.zstd;

final class ZstdOptimalState {

    static final int OPT_NUMBER = 1 << 12;
    static final int OPT_SIZE = OPT_NUMBER + 3;
    static final int PRICE_DYNAMIC = 0;
    static final int PRICE_PREDEFINED = 1;

    final int[] literalFrequencies = new int[256];
    final int[] literalLengthFrequencies = new int[ZstdEntropyTables.MAX_LITERAL_LENGTH + 1];
    final int[] matchLengthFrequencies = new int[ZstdEntropyTables.MAX_MATCH_LENGTH + 1];
    final int[] offsetCodeFrequencies = new int[ZstdEntropyTables.MAX_OFFSET + 1];
    final int[] matchOffsets = new int[OPT_SIZE];
    final int[] matchLengths = new int[OPT_SIZE];
    final int[] prices = new int[OPT_SIZE];
    final int[] offsets = new int[OPT_SIZE];
    final int[] lengths = new int[OPT_SIZE];
    final int[] literalLengths = new int[OPT_SIZE];
    final int[] repeats = new int[OPT_SIZE * 3];
    int literalSum;
    int literalLengthSum;
    int matchLengthSum;
    int offsetCodeSum;
    int literalSumBasePrice;
    int literalLengthSumBasePrice;
    int matchLengthSumBasePrice;
    int offsetCodeSumBasePrice;
    int priceType;
    ZstdEntropyTables symbolCosts;

    void copySlot(int to, int from) {
        prices[to] = prices[from];
        offsets[to] = offsets[from];
        lengths[to] = lengths[from];
        literalLengths[to] = literalLengths[from];
        repeats[3 * to] = repeats[3 * from];
        repeats[3 * to + 1] = repeats[3 * from + 1];
        repeats[3 * to + 2] = repeats[3 * from + 2];
    }
}
