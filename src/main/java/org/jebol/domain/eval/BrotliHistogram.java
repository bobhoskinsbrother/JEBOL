package org.jebol.domain.eval;

import java.util.Arrays;

/**
 * How often each symbol of one alphabet was used.
 *
 * <p>{@code histogram.h}. The encoder keeps one of these per block type per
 * alphabet and turns each into a prefix code at the end. Merging two is how the
 * block splitter asks whether two stretches of input are alike enough to share
 * a code.
 */
final class BrotliHistogram {

    private final int[] counts;
    private long total;

    /** What it was last reckoned to cost, kept so clustering need not re-ask. */
    private double cost;

    BrotliHistogram(int alphabetSize) {
        this.counts = new int[alphabetSize];
    }

    double cost() {
        return cost;
    }

    void costIs(double bits) {
        cost = bits;
    }

    int[] counts() {
        return counts;
    }

    long total() {
        return total;
    }

    void add(int symbol) {
        counts[symbol]++;
        total++;
    }

    void addAll(BrotliHistogram other) {
        total += other.total;
        for (int symbol = 0; symbol < counts.length; symbol++) {
            counts[symbol] += other.counts[symbol];
        }
    }

    void clear() {
        Arrays.fill(counts, 0);
        total = 0;
    }

    void copyFrom(BrotliHistogram other) {
        System.arraycopy(other.counts, 0, counts, 0, counts.length);
        total = other.total;
        cost = other.cost;
    }

    static BrotliHistogram[] freshRow(int howMany, int alphabetSize) {
        BrotliHistogram[] row = new BrotliHistogram[howMany];
        for (int which = 0; which < howMany; which++) {
            row[which] = new BrotliHistogram(alphabetSize);
        }
        return row;
    }
}
