package org.jebol.domain.eval.compression.zstd;

import java.util.Arrays;

final class ZstdHuffmanDecoding {

    static final int TABLE_LOG_MAX = 12;
    private static final int SYMBOL_VALUE_MAX = 255;
    private static final int FAST_TABLE_LOG = 11;
    private static final int WEIGHT_FSE_LOG = 6;
    private static final int CAPACITY_LOG = 12;
    private static final int[][] ALGORITHM_TIME = {
            {0, 0, 1, 1}, {0, 0, 1, 1},
            {150, 216, 381, 119}, {170, 205, 514, 112}, {177, 199, 539, 110}, {197, 194, 644, 107},
            {221, 192, 735, 107}, {256, 189, 881, 106}, {359, 188, 1167, 109}, {582, 187, 1570, 114},
            {688, 187, 1712, 122}, {825, 186, 1965, 136}, {976, 185, 2131, 150}, {1180, 186, 2070, 175},
            {1377, 185, 1731, 202}, {1412, 185, 1695, 202}};

    private final int[] table = new int[1 << CAPACITY_LOG];
    private final int maxTableLog = CAPACITY_LOG;
    private boolean isDoubleSymbol;
    private int tableLog;

    private final byte[] weights = new byte[SYMBOL_VALUE_MAX + 1];
    private final int[] rankStats = new int[TABLE_LOG_MAX + 1];
    private final int[] rankStart = new int[TABLE_LOG_MAX + 3];
    private final int[] sortedSymbols = new int[SYMBOL_VALUE_MAX + 1];
    private final int[][] rankValues = new int[TABLE_LOG_MAX][TABLE_LOG_MAX + 1];
    private final ZstdFseDecoding weightDecoding = new ZstdFseDecoding();
    private final ZstdBitReader[] streams = {
            new ZstdBitReader(), new ZstdBitReader(), new ZstdBitReader(), new ZstdBitReader()};
    private int symbolCount;
    private int statsTableLog;
    private int lastHeaderSize;

    static boolean prefersDoubleSymbols(int destinationSize, int sourceSize) {
        int quantized = sourceSize >= destinationSize ? 15 : (int) ((long) sourceSize * 16 / destinationSize);
        int decodes256 = destinationSize >>> 8;
        long singleTime = ALGORITHM_TIME[quantized][0] + (long) ALGORITHM_TIME[quantized][1] * decodes256;
        long doubleTime = ALGORITHM_TIME[quantized][2] + (long) ALGORITHM_TIME[quantized][3] * decodes256;
        doubleTime += doubleTime >>> 5;
        return (doubleTime & 0xFFFFFFFFL) < (singleTime & 0xFFFFFFFFL);
    }

    void decompressSingleStreamWithNewTable(byte[] destination, int at, int size, byte[] source, int from,
                                            int length) {
        int headerSize = readSingleSymbolTable(source, from, length);
        if (headerSize >= length) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        decompressSingleStream(destination, at, size, source, from + headerSize, length - headerSize);
    }

    void decompressSingleStreamWithNewDoubleTable(byte[] destination, int at, int size, byte[] source, int from,
                                                  int length) {
        int headerSize = readDoubleSymbolTable(source, from, length);
        if (headerSize >= length) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        decompressSingleStream(destination, at, size, source, from + headerSize, length - headerSize);
    }

    int symbolCount() {
        return symbolCount;
    }

    int statsTableLog() {
        return statsTableLog;
    }

    byte[] weights() {
        return weights;
    }

    void decompressFourStreamsWithNewTable(byte[] destination, int at, int size, byte[] source, int from,
                                           int length) {
        if (size == 0) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        if (length == 0) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        int headerSize = prefersDoubleSymbols(size, length)
                ? readDoubleSymbolTable(source, from, length)
                : readSingleSymbolTable(source, from, length);
        if (headerSize >= length) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        decompressFourStreams(destination, at, size, source, from + headerSize, length - headerSize);
    }

    void decompressSingleStream(byte[] destination, int at, int size, byte[] source, int from, int length) {
        ZstdBitReader bits = streams[0];
        bits.open(source, from, length);
        if (isDoubleSymbol) {
            decodeStreamOfPairs(destination, at, bits, at + size, tableLog);
        } else {
            decodeStreamOfSingles(destination, at, bits, at + size, tableLog);
        }
        if (!bits.isEndOfStream()) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
    }

    void decompressFourStreams(byte[] destination, int at, int size, byte[] source, int from, int length) {
        if (decompressFourStreamsFast(destination, at, size, source, from, length)) {
            return;
        }
        decompressFourStreamsSlowly(destination, at, size, source, from, length);
    }

    int readStats(byte[] source, int from, int length) {
        if (length == 0) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        int headerSize = source[from] & 0xFF;
        int weightCount;
        if (headerSize >= 128) {
            weightCount = headerSize - 127;
            headerSize = (weightCount + 1) / 2;
            if (headerSize + 1 > length) {
                throw ZstdError.SRC_SIZE_WRONG.failure();
            }
            for (int each = 0; each < weightCount; each += 2) {
                int packed = source[from + 1 + each / 2] & 0xFF;
                weights[each] = (byte) (packed >>> 4);
                weights[each + 1] = (byte) (packed & 15);
            }
        } else {
            if (headerSize + 1 > length) {
                throw ZstdError.SRC_SIZE_WRONG.failure();
            }
            weightCount = weightDecoding.decompressWeights(weights, SYMBOL_VALUE_MAX, source, from + 1, headerSize,
                    WEIGHT_FSE_LOG);
        }
        Arrays.fill(rankStats, 0);
        int weightTotal = 0;
        for (int each = 0; each < weightCount; each++) {
            int weight = weights[each] & 0xFF;
            if (weight > TABLE_LOG_MAX) {
                throw ZstdError.CORRUPTION_DETECTED.failure();
            }
            rankStats[weight]++;
            weightTotal += (1 << weight) >> 1;
        }
        if (weightTotal == 0) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        int log = ZstdOctets.highBit(weightTotal) + 1;
        if (log > TABLE_LOG_MAX) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        int rest = (1 << log) - weightTotal;
        int verify = 1 << ZstdOctets.highBit(rest);
        int lastWeight = ZstdOctets.highBit(rest) + 1;
        if (verify != rest) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        weights[weightCount] = (byte) lastWeight;
        rankStats[lastWeight]++;
        if (rankStats[1] < 2 || (rankStats[1] & 1) != 0) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        statsTableLog = log;
        symbolCount = weightCount + 1;
        lastHeaderSize = headerSize + 1;
        return lastHeaderSize;
    }

    private int readSingleSymbolTable(byte[] source, int from, int length) {
        readStats(source, from, length);
        int log = statsTableLog;
        int targetTableLog = Math.min(maxTableLog + 1, FAST_TABLE_LOG);
        if (log < targetTableLog) {
            int scale = targetTableLog - log;
            for (int each = 0; each < symbolCount; each++) {
                weights[each] += (byte) (weights[each] == 0 ? 0 : scale);
            }
            for (int each = targetTableLog; each > scale; each--) {
                rankStats[each] = rankStats[each - scale];
            }
            for (int each = scale; each > 0; each--) {
                rankStats[each] = 0;
            }
            log = targetTableLog;
        }
        if (log > maxTableLog + 1) {
            throw ZstdError.TABLE_LOG_TOO_LARGE.failure();
        }
        isDoubleSymbol = false;
        tableLog = log;
        int nextRankStart = 0;
        for (int weight = 0; weight < log + 1; weight++) {
            int current = nextRankStart;
            nextRankStart += rankStats[weight];
            rankStart[weight] = current;
        }
        for (int each = 0; each < symbolCount; each++) {
            sortedSymbols[rankStart[weights[each] & 0xFF]++] = each;
        }
        int symbol = rankStats[0];
        int cell = 0;
        for (int weight = 1; weight < log + 1; weight++) {
            int count = rankStats[weight];
            int span = (1 << weight) >> 1;
            int bits = log + 1 - weight;
            for (int each = 0; each < count; each++) {
                int entry = bits | sortedSymbols[symbol + each] << 8;
                Arrays.fill(table, cell, cell + span, entry);
                cell += span;
            }
            symbol += count;
        }
        return lastHeaderSize;
    }

    private static int doubleEntry(int symbol, int bits, int baseSequence, int level) {
        int sequence = level == 1 ? symbol : baseSequence + (symbol << 8);
        return sequence + (bits << 16) + (level << 24);
    }

    private void fillForWeight(int at, int begin, int end, int bits, int targetLog, int baseSequence, int level) {
        int span = 1 << ((targetLog - bits) & 0x1F);
        int cell = at;
        for (int each = begin; each != end; each++) {
            Arrays.fill(table, cell, cell + span, doubleEntry(sortedSymbols[each], bits, baseSequence, level));
            cell += span;
        }
    }

    private void fillSecondLevel(int at, int targetLog, int consumedBits, int[] rankValue, int minWeight,
                                 int maxWeightPlusOne, int bitsBaseline, int baseSequence) {
        if (minWeight > 1) {
            int skipSize = rankValue[minWeight];
            Arrays.fill(table, at, at + skipSize, doubleEntry(baseSequence, consumedBits, 0, 1));
        }
        for (int weight = minWeight; weight < maxWeightPlusOne; weight++) {
            int begin = rankStart[weight];
            int end = rankStart[weight + 1];
            int bits = bitsBaseline - weight;
            fillForWeight(at + rankValue[weight], begin, end, bits + consumedBits, targetLog, baseSequence, 2);
        }
    }

    private int readDoubleSymbolTable(byte[] source, int from, int length) {
        int targetLog = maxTableLog;
        Arrays.fill(rankStart, 0);
        readStats(source, from, length);
        int log = statsTableLog;
        if (log > targetLog) {
            throw ZstdError.TABLE_LOG_TOO_LARGE.failure();
        }
        if (log <= FAST_TABLE_LOG) {
            targetLog = FAST_TABLE_LOG;
        }
        int maxWeight = log;
        while (rankStats[maxWeight] == 0) {
            maxWeight--;
        }
        int nextRankStart = 0;
        for (int weight = 1; weight < maxWeight + 1; weight++) {
            int current = nextRankStart;
            nextRankStart += rankStats[weight];
            rankStart[1 + weight] = current;
        }
        rankStart[1] = nextRankStart;
        rankStart[1 + maxWeight + 1] = nextRankStart;
        for (int each = 0; each < symbolCount; each++) {
            int weight = weights[each] & 0xFF;
            sortedSymbols[rankStart[1 + weight]++] = each;
        }
        rankStart[1] = 0;
        int[] rankValue0 = rankValues[0];
        int rescale = (targetLog - log) - 1;
        int nextRankValue = 0;
        for (int weight = 1; weight < maxWeight + 1; weight++) {
            int current = nextRankValue;
            nextRankValue += rankStats[weight] << (weight + rescale);
            rankValue0[weight] = current;
        }
        int minBits = log + 1 - maxWeight;
        for (int consumed = minBits; consumed < targetLog - minBits + 1; consumed++) {
            for (int weight = 1; weight < maxWeight + 1; weight++) {
                rankValues[consumed][weight] = rankValue0[weight] >>> consumed;
            }
        }
        fillDoubleTable(targetLog, maxWeight, log + 1);
        isDoubleSymbol = true;
        tableLog = targetLog;
        return lastHeaderSize;
    }

    private void fillDoubleTable(int targetLog, int maxWeight, int bitsBaseline) {
        int[] rankValue = rankValues[0];
        int scaleLog = bitsBaseline - targetLog;
        int minBits = bitsBaseline - maxWeight;
        int weightEnd = maxWeight + 1;
        for (int weight = 1; weight < weightEnd; weight++) {
            int begin = rankStart[weight];
            int end = rankStart[weight + 1];
            int bits = bitsBaseline - weight;
            if (targetLog - bits >= minBits) {
                int start = rankValue[weight];
                int span = 1 << ((targetLog - bits) & 0x1F);
                int minWeight = Math.max(bits + scaleLog, 1);
                for (int each = begin; each != end; each++) {
                    fillSecondLevel(start, targetLog, bits, rankValues[bits], minWeight, weightEnd, bitsBaseline,
                            sortedSymbols[each]);
                    start += span;
                }
            } else {
                fillForWeight(rankValue[weight], begin, end, bits, targetLog, 0, 1);
            }
        }
    }

    private int decodeSingle(byte[] destination, int at, ZstdBitReader bits, int log) {
        int entry = table[(int) bits.lookFast(log)];
        bits.skip(entry & 0xFF);
        destination[at] = (byte) (entry >>> 8);
        return at + 1;
    }

    private int decodeStreamOfSingles(byte[] destination, int start, ZstdBitReader bits, int end, int log) {
        int at = start;
        if (end - at > 3) {
            while ((bits.reload() == ZstdBitReader.UNFINISHED) & (at < end - 3)) {
                at = decodeSingle(destination, at, bits, log);
                at = decodeSingle(destination, at, bits, log);
                at = decodeSingle(destination, at, bits, log);
                at = decodeSingle(destination, at, bits, log);
            }
        } else {
            bits.reload();
        }
        while (at < end) {
            at = decodeSingle(destination, at, bits, log);
        }
        return end - start;
    }

    private int decodePair(byte[] destination, int at, ZstdBitReader bits, int log) {
        int entry = table[(int) bits.lookFast(log)];
        destination[at] = (byte) entry;
        destination[at + 1] = (byte) (entry >>> 8);
        bits.skip((entry >>> 16) & 0xFF);
        return at + (entry >>> 24);
    }

    private int decodeLastOfPairs(byte[] destination, int at, ZstdBitReader bits, int log) {
        int entry = table[(int) bits.lookFast(log)];
        destination[at] = (byte) entry;
        int bitCount = (entry >>> 16) & 0xFF;
        if ((entry >>> 24) == 1) {
            bits.skip(bitCount);
        } else if (bits.consumed() < 64) {
            bits.skip(bitCount);
            if (bits.consumed() > 64) {
                bits.forceConsumed(64);
            }
        }
        return at + 1;
    }

    private int decodeStreamOfPairs(byte[] destination, int start, ZstdBitReader bits, int end, int log) {
        int at = start;
        if (end - at >= 8) {
            if (log <= 11) {
                while ((bits.reload() == ZstdBitReader.UNFINISHED) & (at < end - 9)) {
                    at = decodePair(destination, at, bits, log);
                    at = decodePair(destination, at, bits, log);
                    at = decodePair(destination, at, bits, log);
                    at = decodePair(destination, at, bits, log);
                    at = decodePair(destination, at, bits, log);
                }
            } else {
                while ((bits.reload() == ZstdBitReader.UNFINISHED) & (at < end - 7)) {
                    at = decodePair(destination, at, bits, log);
                    at = decodePair(destination, at, bits, log);
                    at = decodePair(destination, at, bits, log);
                    at = decodePair(destination, at, bits, log);
                }
            }
        } else {
            bits.reload();
        }
        if (end - at >= 2) {
            while ((bits.reload() == ZstdBitReader.UNFINISHED) & (at <= end - 2)) {
                at = decodePair(destination, at, bits, log);
            }
            while (at <= end - 2) {
                at = decodePair(destination, at, bits, log);
            }
        }
        if (at < end) {
            at = decodeLastOfPairs(destination, at, bits, log);
        }
        return at - start;
    }

    private void decompressFourStreamsSlowly(byte[] destination, int at, int size, byte[] source, int from,
                                             int length) {
        if (length < 10) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        if (size < 6) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        int end = at + size;
        int length1 = ZstdOctets.readShort(source, from);
        int length2 = ZstdOctets.readShort(source, from + 2);
        int length3 = ZstdOctets.readShort(source, from + 4);
        long length4 = (long) length - (length1 + length2 + length3 + 6);
        int start1 = from + 6;
        int start2 = start1 + length1;
        int start3 = start2 + length2;
        int start4 = start3 + length3;
        int segment = (size + 3) / 4;
        int[] starts = {at, at + segment, at + 2 * segment, at + 3 * segment};
        if (length4 < 0 || length4 > length) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        if (starts[3] > end) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        streams[0].open(source, start1, length1);
        streams[1].open(source, start2, length2);
        streams[2].open(source, start3, length3);
        streams[3].open(source, start4, (int) length4);
        int[] outputs = starts.clone();
        int log = tableLog;
        if (end - outputs[3] >= 8) {
            if (isDoubleSymbol) {
                runSlowLoopOfPairs(destination, outputs, end - 7, log);
            } else {
                runSlowLoopOfSingles(destination, outputs, end - 3, log);
            }
        }
        for (int stream = 0; stream < 3; stream++) {
            if (outputs[stream] > starts[stream + 1]) {
                throw ZstdError.CORRUPTION_DETECTED.failure();
            }
        }
        for (int stream = 0; stream < 4; stream++) {
            int streamEnd = stream < 3 ? starts[stream + 1] : end;
            if (isDoubleSymbol) {
                decodeStreamOfPairs(destination, outputs[stream], streams[stream], streamEnd, log);
            } else {
                decodeStreamOfSingles(destination, outputs[stream], streams[stream], streamEnd, log);
            }
        }
        boolean isEnded = streams[0].isEndOfStream() & streams[1].isEndOfStream()
                & streams[2].isEndOfStream() & streams[3].isEndOfStream();
        if (!isEnded) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
    }

    private void runSlowLoopOfSingles(byte[] destination, int[] outputs, int limit, int log) {
        boolean isUnfinished = true;
        while (isUnfinished & (outputs[3] < limit)) {
            for (int round = 0; round < 4; round++) {
                for (int stream = 0; stream < 4; stream++) {
                    outputs[stream] = decodeSingle(destination, outputs[stream], streams[stream], log);
                }
            }
            for (int stream = 0; stream < 4; stream++) {
                isUnfinished &= streams[stream].reloadFast() == ZstdBitReader.UNFINISHED;
            }
        }
    }

    private void runSlowLoopOfPairs(byte[] destination, int[] outputs, int limit, int log) {
        boolean isUnfinished = true;
        while (isUnfinished & (outputs[3] < limit)) {
            for (int round = 0; round < 4; round++) {
                for (int stream = 0; stream < 4; stream++) {
                    outputs[stream] = decodePair(destination, outputs[stream], streams[stream], log);
                }
            }
            boolean allUnfinished = true;
            for (int stream = 0; stream < 4; stream++) {
                allUnfinished &= streams[stream].reloadFast() == ZstdBitReader.UNFINISHED;
            }
            isUnfinished = allUnfinished;
        }
    }

    private static long initialFastBits(byte[] source, int at) {
        int lastByte = source[at + 7] & 0xFF;
        int consumed = lastByte != 0 ? 8 - ZstdOctets.highBit(lastByte) : 0;
        long value = ZstdOctets.readLong(source, at) | 1;
        return value << consumed;
    }

    private boolean decompressFourStreamsFast(byte[] destination, int at, int size, byte[] source, int from,
                                              int length) {
        int end = at + size;
        if (size == 0) {
            return false;
        }
        if (length < 10) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        if (tableLog != FAST_TABLE_LOG) {
            return false;
        }
        int length1 = ZstdOctets.readShort(source, from);
        int length2 = ZstdOctets.readShort(source, from + 2);
        int length3 = ZstdOctets.readShort(source, from + 4);
        long length4 = (long) length - (length1 + length2 + length3 + 6);
        int[] inputEnds = {from + 6, from + 6 + length1, from + 6 + length1 + length2,
                from + 6 + length1 + length2 + length3};
        boolean isFourthUnderflowed = length4 < 0;
        if (length1 < 8 || length2 < 8 || length3 < 8 || (!isFourthUnderflowed && length4 < 8)) {
            return false;
        }
        if (length4 < 0 || length4 > length) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        int[] inputs = {inputEnds[1] - 8, inputEnds[2] - 8, inputEnds[3] - 8, from + length - 8};
        int segment = (size + 3) / 4;
        int[] outputs = {at, at + segment, at + 2 * segment, at + 3 * segment};
        if (outputs[3] >= end) {
            return false;
        }
        long[] bits = new long[4];
        for (int stream = 0; stream < 4; stream++) {
            bits[stream] = initialFastBits(source, inputs[stream]);
        }
        if (isDoubleSymbol) {
            runFastLoopOfPairs(destination, source, from, outputs, inputs, bits, end);
        } else {
            runFastLoopOfSingles(destination, source, from, outputs, inputs, bits, end);
        }
        int segmentEnd = at;
        for (int stream = 0; stream < 4; stream++) {
            if (segment <= end - segmentEnd) {
                segmentEnd += segment;
            } else {
                segmentEnd = end;
            }
            if (outputs[stream] > segmentEnd) {
                throw ZstdError.CORRUPTION_DETECTED.failure();
            }
            if (inputs[stream] < inputEnds[stream] - 8) {
                throw ZstdError.CORRUPTION_DETECTED.failure();
            }
            ZstdBitReader remaining = streams[stream];
            remaining.resume(source, ZstdOctets.readLong(source, inputs[stream]),
                    Long.numberOfTrailingZeros(bits[stream]), from, inputs[stream]);
            if (isDoubleSymbol) {
                outputs[stream] += decodeStreamOfPairs(destination, outputs[stream], remaining, segmentEnd,
                        FAST_TABLE_LOG);
            } else {
                outputs[stream] += decodeStreamOfSingles(destination, outputs[stream], remaining, segmentEnd,
                        FAST_TABLE_LOG);
            }
            if (outputs[stream] != segmentEnd) {
                throw ZstdError.CORRUPTION_DETECTED.failure();
            }
        }
        return true;
    }

    private static boolean isAnyInputCrossed(int[] inputs) {
        for (int stream = 1; stream < 4; stream++) {
            if (inputs[stream] < inputs[stream - 1]) {
                return true;
            }
        }
        return false;
    }

    private void runFastLoopOfSingles(byte[] destination, byte[] source, int lowest, int[] outputs, int[] inputs,
                                      long[] bits, int end) {
        for (;;) {
            int outputIterations = (end - outputs[3]) / 5;
            int inputIterations = (inputs[0] - lowest) / 7;
            int iterations = Math.min(outputIterations, inputIterations);
            int limit = outputs[3] + iterations * 5;
            if (outputs[3] == limit) {
                return;
            }
            if (isAnyInputCrossed(inputs)) {
                return;
            }
            do {
                for (int symbol = 0; symbol < 5; symbol++) {
                    for (int stream = 0; stream < 4; stream++) {
                        int entry = table[(int) (bits[stream] >>> 53)];
                        bits[stream] <<= entry & 0x3F;
                        destination[outputs[stream] + symbol] = (byte) (entry >>> 8);
                    }
                }
                for (int stream = 0; stream < 4; stream++) {
                    int trailing = Long.numberOfTrailingZeros(bits[stream]);
                    outputs[stream] += 5;
                    inputs[stream] -= trailing >> 3;
                    bits[stream] = (ZstdOctets.readLong(source, inputs[stream]) | 1) << (trailing & 7);
                }
            } while (outputs[3] < limit);
        }
    }

    private void decodeFastPair(byte[] destination, int[] outputs, long[] bits, int stream) {
        int entry = table[(int) (bits[stream] >>> 53)];
        destination[outputs[stream]] = (byte) entry;
        destination[outputs[stream] + 1] = (byte) (entry >>> 8);
        bits[stream] <<= ((entry >>> 16) & 0xFF) & 0x3F;
        outputs[stream] += entry >>> 24;
    }

    private void runFastLoopOfPairs(byte[] destination, byte[] source, int lowest, int[] outputs, int[] inputs,
                                    long[] bits, int end) {
        int[] ends = {outputs[1], outputs[2], outputs[3], end};
        for (;;) {
            int iterations = (inputs[0] - lowest) / 7;
            for (int stream = 0; stream < 4; stream++) {
                iterations = Math.min(iterations, (ends[stream] - outputs[stream]) / 10);
            }
            int limit = outputs[3] + iterations * 5;
            if (outputs[3] == limit) {
                return;
            }
            if (isAnyInputCrossed(inputs)) {
                return;
            }
            do {
                for (int symbol = 0; symbol < 5; symbol++) {
                    for (int stream = 0; stream < 3; stream++) {
                        decodeFastPair(destination, outputs, bits, stream);
                    }
                }
                decodeFastPair(destination, outputs, bits, 3);
                for (int stream = 0; stream < 4; stream++) {
                    decodeFastPair(destination, outputs, bits, 3);
                    int trailing = Long.numberOfTrailingZeros(bits[stream]);
                    inputs[stream] -= trailing >> 3;
                    bits[stream] = (ZstdOctets.readLong(source, inputs[stream]) | 1) << (trailing & 7);
                }
            } while (outputs[3] < limit);
        }
    }
}
