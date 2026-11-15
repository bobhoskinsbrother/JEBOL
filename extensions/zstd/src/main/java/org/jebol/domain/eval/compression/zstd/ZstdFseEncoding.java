package org.jebol.domain.eval.compression.zstd;

final class ZstdFseEncoding {

    static final int MIN_TABLE_LOG = 5;
    static final int MAX_TABLE_LOG = 12;
    private static final int DEFAULT_TABLE_LOG = 11;
    private static final int[] REST_TO_BEAT = {0, 473195, 504333, 520860, 550000, 700000, 750000, 830000};
    private static final short NOT_YET_ASSIGNED = -2;

    int tableLog;
    int maxSymbolValue;
    final int[] stateTable;
    final int[] deltaNumberOfBits;
    final int[] deltaFindState;

    ZstdFseEncoding(int maxLog, int maxSymbol) {
        stateTable = new int[1 << maxLog];
        deltaNumberOfBits = new int[maxSymbol + 1];
        deltaFindState = new int[maxSymbol + 1];
    }

    void copyFrom(ZstdFseEncoding other) {
        tableLog = other.tableLog;
        maxSymbolValue = other.maxSymbolValue;
        System.arraycopy(other.stateTable, 0, stateTable, 0, stateTable.length);
        System.arraycopy(other.deltaNumberOfBits, 0, deltaNumberOfBits, 0, deltaNumberOfBits.length);
        System.arraycopy(other.deltaFindState, 0, deltaFindState, 0, deltaFindState.length);
    }

    void buildRle(int symbol) {
        tableLog = 0;
        maxSymbolValue = symbol;
        stateTable[0] = 0;
        stateTable[1] = 0;
        deltaNumberOfBits[symbol] = 0;
        deltaFindState[symbol] = 0;
    }

    void build(short[] normalized, int maxSymbol, int log) {
        int tableSize = 1 << log;
        int tableMask = tableSize - 1;
        int step = ZstdFseDecoding.tableStep(tableSize);
        int symbolsPlusOne = maxSymbol + 1;
        int[] cumulative = new int[symbolsPlusOne + 1];
        int[] tableSymbol = new int[tableSize];
        int highThreshold = tableSize - 1;
        tableLog = log;
        maxSymbolValue = maxSymbol;
        for (int at = 1; at <= symbolsPlusOne; at++) {
            if (normalized[at - 1] == -1) {
                cumulative[at] = cumulative[at - 1] + 1;
                tableSymbol[highThreshold--] = at - 1;
            } else {
                cumulative[at] = (cumulative[at - 1] + (normalized[at - 1] & 0xFFFF)) & 0xFFFF;
            }
        }
        cumulative[symbolsPlusOne] = tableSize + 1;
        if (highThreshold == tableSize - 1) {
            int[] spread = new int[tableSize + 8];
            int position = 0;
            for (int symbol = 0; symbol < symbolsPlusOne; symbol++) {
                for (int each = 0; each < normalized[symbol]; each++) {
                    spread[position + each] = symbol;
                }
                position += normalized[symbol];
            }
            int cell = 0;
            for (int at = 0; at < tableSize; at += 2) {
                tableSymbol[cell & tableMask] = spread[at];
                tableSymbol[(cell + step) & tableMask] = spread[at + 1];
                cell = (cell + 2 * step) & tableMask;
            }
        } else {
            int position = 0;
            for (int symbol = 0; symbol < symbolsPlusOne; symbol++) {
                for (int each = 0; each < normalized[symbol]; each++) {
                    tableSymbol[position] = symbol;
                    position = (position + step) & tableMask;
                    while (position > highThreshold) {
                        position = (position + step) & tableMask;
                    }
                }
            }
        }
        for (int at = 0; at < tableSize; at++) {
            int symbol = tableSymbol[at];
            stateTable[cumulative[symbol]++] = (tableSize + at) & 0xFFFF;
        }
        int total = 0;
        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            int count = normalized[symbol];
            switch (count) {
                case 0 -> deltaNumberOfBits[symbol] = ((log + 1) << 16) - (1 << log);
                case -1, 1 -> {
                    deltaNumberOfBits[symbol] = (log << 16) - (1 << log);
                    deltaFindState[symbol] = total - 1;
                    total++;
                }
                default -> {
                    int maxBitsOut = log - ZstdOctets.highBit(count - 1);
                    int minStatePlus = count << maxBitsOut;
                    deltaNumberOfBits[symbol] = (maxBitsOut << 16) - minStatePlus;
                    deltaFindState[symbol] = total - count;
                    total += count;
                }
            }
        }
    }

    int maxBitsOf(int symbol) {
        return (int) (((deltaNumberOfBits[symbol] & 0xFFFFFFFFL) + 0xFFFF) >>> 16);
    }

    int bitCost(int symbol, int accuracyLog) {
        long delta = deltaNumberOfBits[symbol] & 0xFFFFFFFFL;
        int minBits = (int) (delta >>> 16);
        int threshold = (minBits + 1) << 16;
        int tableSize = 1 << tableLog;
        int fromThreshold = threshold - (int) (delta + tableSize);
        int normalizedFromThreshold = (int) (((fromThreshold & 0xFFFFFFFFL) << accuracyLog & 0xFFFFFFFFL) >>> tableLog);
        int multiplier = 1 << accuracyLog;
        return (minBits + 1) * multiplier - normalizedFromThreshold;
    }

    static int optimalTableLog(int maxTableLog, long sourceSize, int maxSymbol, int minus) {
        int maxBitsSource = ZstdOctets.highBit((int) (sourceSize - 1)) - minus;
        int log = maxTableLog;
        int minBits = minTableLog(sourceSize, maxSymbol);
        if (log == 0) {
            log = DEFAULT_TABLE_LOG;
        }
        if (Integer.compareUnsigned(maxBitsSource, log) < 0) {
            log = maxBitsSource;
        }
        if (minBits > log) {
            log = minBits;
        }
        if (log < MIN_TABLE_LOG) {
            log = MIN_TABLE_LOG;
        }
        if (log > MAX_TABLE_LOG) {
            log = MAX_TABLE_LOG;
        }
        return log;
    }

    static int optimalTableLog(int maxTableLog, long sourceSize, int maxSymbol) {
        return optimalTableLog(maxTableLog, sourceSize, maxSymbol, 2);
    }

    private static int minTableLog(long sourceSize, int maxSymbol) {
        int minBitsSource = ZstdOctets.highBit((int) sourceSize) + 1;
        int minBitsSymbols = ZstdOctets.highBit(maxSymbol) + 2;
        return Math.min(minBitsSource, minBitsSymbols);
    }

    static int normalizeCount(short[] normalized, int log, int[] count, long total, int maxSymbol,
                              boolean isLowProbabilityUsed) {
        int tableLog = log == 0 ? DEFAULT_TABLE_LOG : log;
        if (tableLog < MIN_TABLE_LOG) {
            throw ZstdError.GENERIC.failure();
        }
        if (tableLog > MAX_TABLE_LOG) {
            throw ZstdError.TABLE_LOG_TOO_LARGE.failure();
        }
        if (tableLog < minTableLog(total, maxSymbol)) {
            throw ZstdError.GENERIC.failure();
        }
        short lowProbability = (short) (isLowProbabilityUsed ? -1 : 1);
        int scale = 62 - tableLog;
        long step = (1L << 62) / total;
        long vStep = 1L << (scale - 20);
        int stillToDistribute = 1 << tableLog;
        int largest = 0;
        short largestProbability = 0;
        long lowThreshold = total >>> tableLog;
        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            long symbolCount = count[symbol] & 0xFFFFFFFFL;
            if (symbolCount == total) {
                return 0;
            }
            if (symbolCount == 0) {
                normalized[symbol] = 0;
                continue;
            }
            if (symbolCount <= lowThreshold) {
                normalized[symbol] = lowProbability;
                stillToDistribute--;
            } else {
                short probability = (short) ((symbolCount * step) >>> scale);
                if (probability < 8) {
                    long restToBeat = vStep * REST_TO_BEAT[probability];
                    if (symbolCount * step - ((long) probability << scale) > restToBeat) {
                        probability++;
                    }
                }
                if (probability > largestProbability) {
                    largestProbability = probability;
                    largest = symbol;
                }
                normalized[symbol] = probability;
                stillToDistribute -= probability;
            }
        }
        if (-stillToDistribute >= (normalized[largest] >> 1)) {
            normalizeSecondWay(normalized, tableLog, count, total, maxSymbol, lowProbability);
        } else {
            normalized[largest] += (short) stillToDistribute;
        }
        return tableLog;
    }

    private static void normalizeSecondWay(short[] normalized, int tableLog, int[] count, long totalCount,
                                           int maxSymbol, short lowProbability) {
        long total = totalCount;
        int distributed = 0;
        long lowThreshold = total >>> tableLog;
        long lowOne = (total * 3) >>> (tableLog + 1);
        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            long symbolCount = count[symbol] & 0xFFFFFFFFL;
            if (symbolCount == 0) {
                normalized[symbol] = 0;
                continue;
            }
            if (symbolCount <= lowThreshold) {
                normalized[symbol] = lowProbability;
                distributed++;
                total -= symbolCount;
                continue;
            }
            if (symbolCount <= lowOne) {
                normalized[symbol] = 1;
                distributed++;
                total -= symbolCount;
                continue;
            }
            normalized[symbol] = NOT_YET_ASSIGNED;
        }
        int toDistribute = (1 << tableLog) - distributed;
        if (toDistribute == 0) {
            return;
        }
        if (total / toDistribute > lowOne) {
            lowOne = (total * 3) / (toDistribute * 2L);
            for (int symbol = 0; symbol <= maxSymbol; symbol++) {
                if (normalized[symbol] == NOT_YET_ASSIGNED && (count[symbol] & 0xFFFFFFFFL) <= lowOne) {
                    normalized[symbol] = 1;
                    distributed++;
                    total -= count[symbol] & 0xFFFFFFFFL;
                }
            }
            toDistribute = (1 << tableLog) - distributed;
        }
        if (distributed == maxSymbol + 1) {
            int maxValue = 0;
            long maxCount = 0;
            for (int symbol = 0; symbol <= maxSymbol; symbol++) {
                if ((count[symbol] & 0xFFFFFFFFL) > maxCount) {
                    maxValue = symbol;
                    maxCount = count[symbol] & 0xFFFFFFFFL;
                }
            }
            normalized[maxValue] += (short) toDistribute;
            return;
        }
        if (total == 0) {
            for (int symbol = 0; toDistribute > 0; symbol = (symbol + 1) % (maxSymbol + 1)) {
                if (normalized[symbol] > 0) {
                    toDistribute--;
                    normalized[symbol]++;
                }
            }
            return;
        }
        int vStepLog = 62 - tableLog;
        long middle = (1L << (vStepLog - 1)) - 1;
        long rStep = Long.divideUnsigned(((1L << vStepLog) * toDistribute) + middle, total & 0xFFFFFFFFL);
        long runningTotal = middle;
        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            if (normalized[symbol] == NOT_YET_ASSIGNED) {
                long end = runningTotal + (count[symbol] & 0xFFFFFFFFL) * rStep;
                int startCell = (int) (runningTotal >>> vStepLog);
                int endCell = (int) (end >>> vStepLog);
                int weight = endCell - startCell;
                if (weight < 1) {
                    throw ZstdError.GENERIC.failure();
                }
                normalized[symbol] = (short) weight;
                runningTotal = end;
            }
        }
    }

    static int writeCountBound(int maxSymbol, int log) {
        int maxHeaderSize = (((maxSymbol + 1) * log + 4 + 2) / 8) + 1 + 2;
        return maxSymbol != 0 ? maxHeaderSize : 512;
    }

    static int writeCount(byte[] header, int at, int capacity, short[] normalized, int maxSymbol, int log) {
        if (log > MAX_TABLE_LOG) {
            throw ZstdError.TABLE_LOG_TOO_LARGE.failure();
        }
        if (log < MIN_TABLE_LOG) {
            throw ZstdError.GENERIC.failure();
        }
        boolean isWriteSafe = capacity >= writeCountBound(maxSymbol, log);
        int out = at;
        int end = at + capacity;
        int tableSize = 1 << log;
        int bitStream = 0;
        int bitCount = 0;
        int symbol = 0;
        int alphabetSize = maxSymbol + 1;
        boolean isPreviousZero = false;
        bitStream += (log - MIN_TABLE_LOG) << bitCount;
        bitCount += 4;
        int remaining = tableSize + 1;
        int threshold = tableSize;
        int bits = log + 1;
        while (symbol < alphabetSize && remaining > 1) {
            if (isPreviousZero) {
                int run = symbol;
                while (symbol < alphabetSize && normalized[symbol] == 0) {
                    symbol++;
                }
                if (symbol == alphabetSize) {
                    break;
                }
                while (symbol >= run + 24) {
                    run += 24;
                    bitStream += 0xFFFF << bitCount;
                    if (!isWriteSafe && out > end - 2) {
                        throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                    }
                    header[out] = (byte) bitStream;
                    header[out + 1] = (byte) (bitStream >>> 8);
                    out += 2;
                    bitStream >>>= 16;
                }
                while (symbol >= run + 3) {
                    run += 3;
                    bitStream += 3 << bitCount;
                    bitCount += 2;
                }
                bitStream += (symbol - run) << bitCount;
                bitCount += 2;
                if (bitCount > 16) {
                    if (!isWriteSafe && out > end - 2) {
                        throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                    }
                    header[out] = (byte) bitStream;
                    header[out + 1] = (byte) (bitStream >>> 8);
                    out += 2;
                    bitStream >>>= 16;
                    bitCount -= 16;
                }
            }
            int count = normalized[symbol++];
            int max = (2 * threshold - 1) - remaining;
            remaining -= count < 0 ? -count : count;
            count++;
            if (count >= threshold) {
                count += max;
            }
            bitStream += count << bitCount;
            bitCount += bits;
            bitCount -= count < max ? 1 : 0;
            isPreviousZero = count == 1;
            if (remaining < 1) {
                throw ZstdError.GENERIC.failure();
            }
            while (remaining < threshold) {
                bits--;
                threshold >>= 1;
            }
            if (bitCount > 16) {
                if (!isWriteSafe && out > end - 2) {
                    throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                }
                header[out] = (byte) bitStream;
                header[out + 1] = (byte) (bitStream >>> 8);
                out += 2;
                bitStream >>>= 16;
                bitCount -= 16;
            }
        }
        if (remaining != 1) {
            throw ZstdError.GENERIC.failure();
        }
        if (!isWriteSafe && out > end - 2) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        header[out] = (byte) bitStream;
        header[out + 1] = (byte) (bitStream >>> 8);
        out += (bitCount + 7) / 8;
        return out - at;
    }

    long initialState() {
        return 1L << tableLog;
    }

    long initialStateWith(int symbol) {
        long delta = deltaNumberOfBits[symbol] & 0xFFFFFFFFL;
        int bitsOut = (int) ((delta + (1 << 15)) >>> 16);
        long value = (((long) bitsOut << 16) - delta) & 0xFFFFFFFFL;
        return stateTable[(int) (value >> bitsOut) + deltaFindState[symbol]];
    }

    long encode(ZstdBitWriter bits, long state, int symbol) {
        int bitsOut = (int) ((state + (deltaNumberOfBits[symbol] & 0xFFFFFFFFL)) >>> 16);
        bits.addBits(state, bitsOut);
        return stateTable[(int) (state >> bitsOut) + deltaFindState[symbol]];
    }

    void flushState(ZstdBitWriter bits, long state) {
        bits.addBits(state, tableLog);
        bits.flush();
    }

    int compress(byte[] destination, int at, int capacity, byte[] source, int from, int size) {
        if (size <= 2) {
            return 0;
        }
        ZstdBitWriter bits = new ZstdBitWriter();
        if (!bits.open(destination, at, capacity)) {
            return 0;
        }
        int input = from + size;
        long first;
        long second;
        if ((size & 1) != 0) {
            first = initialStateWith(source[--input] & 0xFF);
            second = initialStateWith(source[--input] & 0xFF);
            first = encode(bits, first, source[--input] & 0xFF);
            bits.flush();
        } else {
            second = initialStateWith(source[--input] & 0xFF);
            first = initialStateWith(source[--input] & 0xFF);
        }
        int remaining = size - 2;
        if ((remaining & 2) != 0) {
            second = encode(bits, second, source[--input] & 0xFF);
            first = encode(bits, first, source[--input] & 0xFF);
            bits.flush();
        }
        while (input > from) {
            second = encode(bits, second, source[--input] & 0xFF);
            first = encode(bits, first, source[--input] & 0xFF);
            second = encode(bits, second, source[--input] & 0xFF);
            first = encode(bits, first, source[--input] & 0xFF);
            bits.flush();
        }
        flushState(bits, second);
        flushState(bits, first);
        return bits.close();
    }
}
