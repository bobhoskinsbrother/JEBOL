package org.jebol.domain.eval.brotli;

/**
 * How many bits a histogram would cost if it were written out as a code.
 *
 * <p>{@code bit_cost_inc.h}. The clustering asks this of every histogram and of
 * every pair it might merge, so it has to be quick and it does not have to be
 * exact -- it is a guess used to compare, not a measurement.
 *
 * <p>Four or fewer symbols get a flat answer, because the format has a short
 * form for those and its cost is known without building anything. Anything
 * larger is the entropy of the symbols plus a guess at what declaring the code
 * itself costs, which is worked out by counting how many symbols would land at
 * each depth and how long the runs of unused symbols are.
 */
final class BrotliHistogramCost {

    private static final double ONE_SYMBOL = 12;
    private static final double TWO_SYMBOLS = 20;
    private static final double THREE_SYMBOLS = 28;
    private static final double FOUR_SYMBOLS = 37;

    private static final int CODE_LENGTH_CODES = 18;
    private static final int REPEAT_ZERO_CODE_LENGTH = 17;
    private static final int DEEPEST_A_SYMBOL_MAY_SIT = 15;

    private BrotliHistogramCost() {
    }

    static double of(BrotliHistogram histogram, int alphabetSize) {
        if (histogram.total() == 0) {
            return ONE_SYMBOL;
        }
        int[] counts = histogram.counts();
        int[] whichAreUsed = new int[5];
        int howManyAreUsed = 0;
        for (int symbol = 0; symbol < alphabetSize; symbol++) {
            if (counts[symbol] > 0) {
                whichAreUsed[howManyAreUsed] = symbol;
                howManyAreUsed++;
                if (howManyAreUsed > 4) {
                    break;
                }
            }
        }
        if (howManyAreUsed <= 4) {
            return theShortForm(counts, whichAreUsed, howManyAreUsed,
                    histogram.total());
        }
        return theLongForm(counts, alphabetSize, histogram.total());
    }

    private static double theShortForm(int[] counts, int[] whichAreUsed,
            int howManyAreUsed, long total) {

        if (howManyAreUsed == 1) {
            return ONE_SYMBOL;
        }
        if (howManyAreUsed == 2) {
            return TWO_SYMBOLS + (double) total;
        }
        if (howManyAreUsed == 3) {
            int first = counts[whichAreUsed[0]];
            int second = counts[whichAreUsed[1]];
            int third = counts[whichAreUsed[2]];
            int largest = Math.max(first, Math.max(second, third));
            return THREE_SYMBOLS + 2L * (first + second + third) - largest;
        }
        int[] four = new int[4];
        for (int each = 0; each < 4; each++) {
            four[each] = counts[whichAreUsed[each]];
        }
        for (int each = 0; each < 4; each++) {
            for (int other = each + 1; other < 4; other++) {
                if (four[other] > four[each]) {
                    int swapped = four[other];
                    four[other] = four[each];
                    four[each] = swapped;
                }
            }
        }
        long twoSmallest = (long) four[2] + four[3];
        long largest = Math.max(twoSmallest, four[0]);
        return FOUR_SYMBOLS + 3 * twoSmallest + 2L * (four[0] + four[1]) - largest;
    }

    /**
     * The entropy of the symbols, plus a guess at what declaring the code costs.
     *
     * <p>The guess counts how many symbols would sit at each depth if each cost
     * its own surprise rounded to the nearest bit, and how long the runs of
     * unused symbols are, since a run of three or more is written as a repeat
     * with three extra bits. The final run of unused symbols is free -- the
     * format takes the code as ending there -- so it is not counted.
     */
    private static double theLongForm(int[] counts, int alphabetSize, long total) {
        double bits = 0.0;
        int deepest = 1;
        int[] howManyAtEachDepth = new int[CODE_LENGTH_CODES];
        double logOfTotal = BrotliCodes.fastLog2(total);
        int symbol = 0;
        while (symbol < alphabetSize) {
            if (counts[symbol] > 0) {
                double surprise = logOfTotal - BrotliCodes.fastLog2(counts[symbol]);
                int depth = (int) (surprise + 0.5);
                bits += counts[symbol] * surprise;
                if (depth > DEEPEST_A_SYMBOL_MAY_SIT) {
                    depth = DEEPEST_A_SYMBOL_MAY_SIT;
                }
                if (depth > deepest) {
                    deepest = depth;
                }
                howManyAtEachDepth[depth]++;
                symbol++;
                continue;
            }
            int run = 1;
            for (int ahead = symbol + 1;
                    ahead < alphabetSize && counts[ahead] == 0; ahead++) {
                run++;
            }
            symbol += run;
            if (symbol == alphabetSize) {
                break;
            }
            if (run < 3) {
                howManyAtEachDepth[0] += run;
            } else {
                run -= 2;
                while (run > 0) {
                    howManyAtEachDepth[REPEAT_ZERO_CODE_LENGTH]++;
                    bits += 3;
                    run >>= 3;
                }
            }
        }
        bits += 18 + 2 * deepest;
        bits += BrotliCodes.bitsEntropy(howManyAtEachDepth, CODE_LENGTH_CODES);
        return bits;
    }
}
