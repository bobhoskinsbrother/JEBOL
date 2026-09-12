package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliHistogram {

    private final int[] counts;
    private long total;

    private double costKeptSoClusteringNeedNotReAsk;

    BrotliHistogram(int alphabetSize) {
        this.counts = new int[alphabetSize];
    }

    double cost() {
        return costKeptSoClusteringNeedNotReAsk;
    }

    void costIs(double bits) {
        costKeptSoClusteringNeedNotReAsk = bits;
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
        costKeptSoClusteringNeedNotReAsk = other.costKeptSoClusteringNeedNotReAsk;
    }

    static BrotliHistogram[] freshRow(int howMany, int alphabetSize) {
        BrotliHistogram[] row = new BrotliHistogram[howMany];
        for (int which = 0; which < howMany; which++) {
            row[which] = new BrotliHistogram(alphabetSize);
        }
        return row;
    }
}
