package org.jebol.domain.eval.compression.zstd;

final class ZstdFseDecoding {

    private static final int MAX_SYMBOL_VALUE = 255;
    private static final int MAX_TABLE_SIZE = 1 << 12;
    private static final int WEIGHT_TABLE_LOG = 6;
    private static final int LARGEST_WEIGHT_SYMBOL = 11;
    private static final int COUNTS_WORDS = (MAX_SYMBOL_VALUE + 1) / 2 + 1;
    private static final int COUNTS_BYTES = 2 * (MAX_SYMBOL_VALUE + 1);
    private static final int WEIGHT_WORKSPACE_WORDS = workspaceWordsNeeded(WEIGHT_TABLE_LOG, LARGEST_WEIGHT_SYMBOL);

    private final ZstdNormalizedCount normalized = new ZstdNormalizedCount(MAX_SYMBOL_VALUE + 1);
    private final int[] symbol = new int[MAX_TABLE_SIZE];
    private final int[] nbBits = new int[MAX_TABLE_SIZE];
    private final int[] newState = new int[MAX_TABLE_SIZE];
    private final int[] symbolNext = new int[MAX_SYMBOL_VALUE + 1];
    private final ZstdBitReader bits = new ZstdBitReader();
    private int tableLog;
    private boolean isFastMode;

    static int tableStep(int tableSize) {
        return (tableSize >>> 1) + (tableSize >>> 3) + 3;
    }

    private static int tableWords(int log) {
        return 1 + (1 << log);
    }

    private static int buildBytesNeeded(int log, int maxSymbolValue) {
        return 2 * (maxSymbolValue + 1) + (1 << log) + 8;
    }

    private static int workspaceWordsNeeded(int log, int maxSymbolValue) {
        return tableWords(log) + 1 + (buildBytesNeeded(log, maxSymbolValue) + 3) / 4 + COUNTS_WORDS;
    }

    int decompressWeights(byte[] destination, int capacity, byte[] source, int from, int length, int maxLog) {
        int headerLength = normalized.read(MAX_SYMBOL_VALUE, source, from, length);
        if (normalized.tableLog > maxLog) {
            throw ZstdError.TABLE_LOG_TOO_LARGE.failure();
        }
        if (workspaceWordsNeeded(normalized.tableLog, normalized.maxSymbol) > WEIGHT_WORKSPACE_WORDS) {
            throw ZstdError.TABLE_LOG_TOO_LARGE.failure();
        }
        int buildRoom = WEIGHT_WORKSPACE_WORDS * 4 - COUNTS_BYTES - 4 * tableWords(normalized.tableLog);
        if (buildBytesNeeded(normalized.tableLog, normalized.maxSymbol) > buildRoom) {
            throw ZstdError.MAX_SYMBOL_VALUE_TOO_LARGE.failure();
        }
        buildTable(normalized.counts, normalized.maxSymbol, normalized.tableLog);
        return decompress(destination, capacity, source, from + headerLength, length - headerLength);
    }

    private void buildTable(short[] counts, int maxSymbolValue, int log) {
        int maxSymbolsPlusOne = maxSymbolValue + 1;
        int tableSize = 1 << log;
        int highThreshold = tableSize - 1;
        tableLog = log;
        isFastMode = true;
        int largeLimit = (short) (1 << (log - 1));
        for (int each = 0; each < maxSymbolsPlusOne; each++) {
            if (counts[each] == -1) {
                symbol[highThreshold--] = each;
                symbolNext[each] = 1;
            } else {
                if (counts[each] >= largeLimit) {
                    isFastMode = false;
                }
                symbolNext[each] = counts[each] & 0xFFFF;
            }
        }
        int tableMask = tableSize - 1;
        int step = tableStep(tableSize);
        if (highThreshold == tableSize - 1) {
            spreadWithoutLowProbabilities(counts, maxSymbolsPlusOne, tableSize, tableMask, step);
        } else {
            int position = 0;
            for (int each = 0; each < maxSymbolsPlusOne; each++) {
                for (int copy = 0; copy < counts[each]; copy++) {
                    symbol[position] = each;
                    position = (position + step) & tableMask;
                    while (position > highThreshold) {
                        position = (position + step) & tableMask;
                    }
                }
            }
            if (position != 0) {
                throw ZstdError.GENERIC.failure();
            }
        }
        for (int cell = 0; cell < tableSize; cell++) {
            int next = symbolNext[symbol[cell]]++;
            nbBits[cell] = log - ZstdOctets.highBit(next);
            newState[cell] = ((next << nbBits[cell]) - tableSize) & 0xFFFF;
        }
    }

    private void spreadWithoutLowProbabilities(short[] counts, int maxSymbolsPlusOne, int tableSize, int tableMask,
                                               int step) {
        int[] spread = new int[tableSize + 8];
        int at = 0;
        for (int each = 0; each < maxSymbolsPlusOne; each++) {
            for (int copy = 0; copy < counts[each]; copy++) {
                spread[at + copy] = each;
            }
            at += counts[each];
        }
        int position = 0;
        for (int cell = 0; cell < tableSize; cell += 2) {
            symbol[position & tableMask] = spread[cell];
            symbol[(position + step) & tableMask] = spread[cell + 1];
            position = (position + 2 * step) & tableMask;
        }
    }

    private int decodeSymbol(int[] state) {
        int cell = state[0];
        int count = nbBits[cell];
        long low = isFastMode ? bits.readFast(count) : bits.read(count);
        state[0] = (int) (newState[cell] + low);
        return symbol[cell];
    }

    private int decompress(byte[] destination, int capacity, byte[] source, int from, int length) {
        int out = 0;
        int olimit = capacity - 3;
        bits.open(source, from, length);
        int[] first = {(int) bits.read(tableLog)};
        bits.reload();
        int[] second = {(int) bits.read(tableLog)};
        bits.reload();
        if (bits.reload() == ZstdBitReader.OVERFLOW) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        while ((bits.reload() == ZstdBitReader.UNFINISHED) & (out < olimit)) {
            destination[out] = (byte) decodeSymbol(first);
            destination[out + 1] = (byte) decodeSymbol(second);
            destination[out + 2] = (byte) decodeSymbol(first);
            destination[out + 3] = (byte) decodeSymbol(second);
            out += 4;
        }
        for (;;) {
            if (out > capacity - 2) {
                throw ZstdError.DST_SIZE_TOO_SMALL.failure();
            }
            destination[out++] = (byte) decodeSymbol(first);
            if (bits.reload() == ZstdBitReader.OVERFLOW) {
                destination[out++] = (byte) decodeSymbol(second);
                break;
            }
            if (out > capacity - 2) {
                throw ZstdError.DST_SIZE_TOO_SMALL.failure();
            }
            destination[out++] = (byte) decodeSymbol(second);
            if (bits.reload() == ZstdBitReader.OVERFLOW) {
                destination[out++] = (byte) decodeSymbol(first);
                break;
            }
        }
        return out;
    }
}
