package org.jebol.domain.eval.compression.zstd;

import java.util.Arrays;

final class ZstdHistogram {

    static final int SYMBOL_VALUE_MAX = 255;
    private static final int SIMPLE_BELOW = 1500;

    private ZstdHistogram() {
    }

    static int countSimply(int[] count, int[] maxSymbolValue, byte[] source, int from, int size) {
        int max = maxSymbolValue[0];
        Arrays.fill(count, 0, max + 1, 0);
        if (size == 0) {
            maxSymbolValue[0] = 0;
            return 0;
        }
        for (int at = from; at < from + size; at++) {
            count[source[at] & 0xFF]++;
        }
        while (count[max] == 0) {
            max--;
        }
        maxSymbolValue[0] = max;
        int largest = 0;
        for (int symbol = 0; symbol <= max; symbol++) {
            if (count[symbol] > largest) {
                largest = count[symbol];
            }
        }
        return largest;
    }

    private static int countInParallel(int[] count, int[] maxSymbolValue, byte[] source, int from, int size,
                                       boolean isChecked) {
        int counted = maxSymbolValue[0] + 1;
        if (size == 0) {
            Arrays.fill(count, 0, counted, 0);
            maxSymbolValue[0] = 0;
            return 0;
        }
        int[] counting = new int[SYMBOL_VALUE_MAX + 1];
        for (int at = from; at < from + size; at++) {
            counting[source[at] & 0xFF]++;
        }
        int largest = 0;
        for (int symbol = 0; symbol <= SYMBOL_VALUE_MAX; symbol++) {
            if (Integer.compareUnsigned(counting[symbol], largest) > 0) {
                largest = counting[symbol];
            }
        }
        int max = SYMBOL_VALUE_MAX;
        while (counting[max] == 0) {
            max--;
        }
        if (isChecked && max > maxSymbolValue[0]) {
            throw ZstdError.MAX_SYMBOL_VALUE_TOO_SMALL.failure();
        }
        maxSymbolValue[0] = max;
        System.arraycopy(counting, 0, count, 0, counted);
        return largest;
    }

    static int countFast(int[] count, int[] maxSymbolValue, byte[] source, int from, int size) {
        if (size < SIMPLE_BELOW) {
            return countSimply(count, maxSymbolValue, source, from, size);
        }
        return countInParallel(count, maxSymbolValue, source, from, size, false);
    }

    static int count(int[] count, int[] maxSymbolValue, byte[] source, int from, int size) {
        if (maxSymbolValue[0] < SYMBOL_VALUE_MAX) {
            return countInParallel(count, maxSymbolValue, source, from, size, true);
        }
        maxSymbolValue[0] = SYMBOL_VALUE_MAX;
        return countFast(count, maxSymbolValue, source, from, size);
    }
}
