package org.jebol.domain.eval.compression.zstd;

final class ZstdHuffmanTable {

    static final int REPEAT_NONE = 0;
    static final int REPEAT_CHECK = 1;
    static final int REPEAT_VALID = 2;

    int tableLog;
    int maxSymbolValue;
    final int[] bits = new int[ZstdHistogram.SYMBOL_VALUE_MAX + 1];
    final int[] values = new int[ZstdHistogram.SYMBOL_VALUE_MAX + 1];

    void copyFrom(ZstdHuffmanTable other) {
        tableLog = other.tableLog;
        maxSymbolValue = other.maxSymbolValue;
        System.arraycopy(other.bits, 0, bits, 0, bits.length);
        System.arraycopy(other.values, 0, values, 0, values.length);
    }

    void clear() {
        tableLog = 0;
        maxSymbolValue = 0;
        java.util.Arrays.fill(bits, 0);
        java.util.Arrays.fill(values, 0);
    }

    long estimateCompressedSize(int[] count, int maxSymbol) {
        long total = 0;
        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            total += (long) bits[symbol] * (count[symbol] & 0xFFFFFFFFL);
        }
        return total >>> 3;
    }

    boolean isValidFor(int[] count, int maxSymbol) {
        if (maxSymbolValue < maxSymbol) {
            return false;
        }
        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            if (count[symbol] != 0 && bits[symbol] == 0) {
                return false;
            }
        }
        return true;
    }
}
