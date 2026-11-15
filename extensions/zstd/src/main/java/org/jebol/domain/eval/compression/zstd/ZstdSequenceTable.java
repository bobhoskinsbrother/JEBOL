package org.jebol.domain.eval.compression.zstd;

final class ZstdSequenceTable {

    static final int[] LITERAL_LENGTH_BASE = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            16, 18, 20, 22, 24, 28, 32, 40, 48, 64, 0x80, 0x100, 0x200, 0x400, 0x800, 0x1000,
            0x2000, 0x4000, 0x8000, 0x10000};
    static final int[] LITERAL_LENGTH_BITS = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 2, 2, 3, 3, 4, 6, 7, 8, 9, 10, 11, 12,
            13, 14, 15, 16};
    static final int[] MATCH_LENGTH_BASE = {
            3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
            19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34,
            35, 37, 39, 41, 43, 47, 51, 59, 67, 83, 99, 0x83, 0x103, 0x203, 0x403, 0x803,
            0x1003, 0x2003, 0x4003, 0x8003, 0x10003};
    static final int[] MATCH_LENGTH_BITS = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 7, 8, 9, 10, 11,
            12, 13, 14, 15, 16};
    static final int[] OFFSET_BASE = {
            0, 1, 1, 5, 0xD, 0x1D, 0x3D, 0x7D, 0xFD, 0x1FD, 0x3FD, 0x7FD, 0xFFD, 0x1FFD, 0x3FFD, 0x7FFD,
            0xFFFD, 0x1FFFD, 0x3FFFD, 0x7FFFD, 0xFFFFD, 0x1FFFFD, 0x3FFFFD, 0x7FFFFD,
            0xFFFFFD, 0x1FFFFFD, 0x3FFFFFD, 0x7FFFFFD, 0xFFFFFFD, 0x1FFFFFFD, 0x3FFFFFFD, 0x7FFFFFFD};
    static final int[] OFFSET_BITS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31};

    static final short[] LITERAL_LENGTH_DEFAULT_NORM = {
            4, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 2, 1, 1, 1, 1, 1,
            -1, -1, -1, -1};
    static final short[] MATCH_LENGTH_DEFAULT_NORM = {
            1, 4, 3, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1, -1,
            -1, -1, -1, -1, -1};
    static final short[] OFFSET_DEFAULT_NORM = {
            1, 1, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, -1, -1, -1, -1, -1};

    static final int LITERAL_LENGTH_DEFAULT_LOG = 6;
    static final int MATCH_LENGTH_DEFAULT_LOG = 6;
    static final int OFFSET_DEFAULT_LOG = 5;

    private static final int MAX_SEQUENCE_SYMBOL = 52;

    static final ZstdSequenceTable LITERAL_LENGTH_DEFAULT = defaultTable(LITERAL_LENGTH_DEFAULT_NORM,
            LITERAL_LENGTH_BASE, LITERAL_LENGTH_BITS, LITERAL_LENGTH_DEFAULT_LOG);
    static final ZstdSequenceTable MATCH_LENGTH_DEFAULT = defaultTable(MATCH_LENGTH_DEFAULT_NORM,
            MATCH_LENGTH_BASE, MATCH_LENGTH_BITS, MATCH_LENGTH_DEFAULT_LOG);
    static final ZstdSequenceTable OFFSET_DEFAULT = defaultTable(OFFSET_DEFAULT_NORM,
            OFFSET_BASE, OFFSET_BITS, OFFSET_DEFAULT_LOG);

    final int[] nextState;
    final int[] additionalBits;
    final int[] stateBits;
    final int[] baseValue;
    int tableLog;
    boolean isFastMode;

    ZstdSequenceTable(int maxLog) {
        int size = 1 << maxLog;
        nextState = new int[size];
        additionalBits = new int[size];
        stateBits = new int[size];
        baseValue = new int[size];
    }

    private static ZstdSequenceTable defaultTable(short[] norm, int[] base, int[] bits, int log) {
        ZstdSequenceTable table = new ZstdSequenceTable(log);
        table.build(norm, norm.length - 1, base, bits, log);
        return table;
    }

    void buildRle(int base, int bits) {
        tableLog = 0;
        isFastMode = false;
        stateBits[0] = 0;
        nextState[0] = 0;
        additionalBits[0] = bits;
        baseValue[0] = base;
    }

    void build(short[] counts, int maxSymbolValue, int[] base, int[] bits, int log) {
        int maxSymbolsPlusOne = maxSymbolValue + 1;
        int tableSize = 1 << log;
        int highThreshold = tableSize - 1;
        int[] symbolNext = new int[MAX_SEQUENCE_SYMBOL + 1];
        int[] symbolOf = new int[tableSize];
        tableLog = log;
        isFastMode = true;
        int largeLimit = (short) (1 << (log - 1));
        for (int each = 0; each < maxSymbolsPlusOne; each++) {
            if (counts[each] == -1) {
                symbolOf[highThreshold--] = each;
                symbolNext[each] = 1;
            } else {
                if (counts[each] >= largeLimit) {
                    isFastMode = false;
                }
                symbolNext[each] = counts[each] & 0xFFFF;
            }
        }
        int tableMask = tableSize - 1;
        int step = ZstdFseDecoding.tableStep(tableSize);
        if (highThreshold == tableSize - 1) {
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
                symbolOf[position & tableMask] = spread[cell];
                symbolOf[(position + step) & tableMask] = spread[cell + 1];
                position = (position + 2 * step) & tableMask;
            }
        } else {
            int position = 0;
            for (int each = 0; each < maxSymbolsPlusOne; each++) {
                for (int copy = 0; copy < counts[each]; copy++) {
                    symbolOf[position] = each;
                    position = (position + step) & tableMask;
                    while (position > highThreshold) {
                        position = (position + step) & tableMask;
                    }
                }
            }
        }
        for (int cell = 0; cell < tableSize; cell++) {
            int symbol = symbolOf[cell];
            int next = symbolNext[symbol]++;
            stateBits[cell] = log - ZstdOctets.highBit(next);
            nextState[cell] = ((next << stateBits[cell]) - tableSize) & 0xFFFF;
            additionalBits[cell] = bits[symbol];
            baseValue[cell] = base[symbol];
        }
    }
}
