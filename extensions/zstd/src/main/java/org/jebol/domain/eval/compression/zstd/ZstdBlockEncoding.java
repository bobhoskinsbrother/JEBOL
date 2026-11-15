package org.jebol.domain.eval.compression.zstd;

final class ZstdBlockEncoding {

    static final int SET_BASIC = 0;
    static final int SET_RLE = 1;
    static final int SET_COMPRESSED = 2;
    static final int SET_REPEAT = 3;

    static final long ERROR_GENERIC = -1L;

    private static final int LITERAL_HUFFMAN_LOG = 11;
    private static final int SUSPECT_UNCOMPRESSIBLE_LITERAL_RATIO = 20;
    private static final int LONG_SEQUENCE_COUNT = 0x7F00;
    private static final int DEFAULT_MAX_OFFSET = 28;
    private static final int MAX_SEQUENCE_SYMBOL = 52;
    private static final int[] INVERSE_PROBABILITY_LOG_256 = {
            0, 2048, 1792, 1642, 1536, 1453, 1386, 1329, 1280, 1236, 1197, 1162,
            1130, 1100, 1073, 1047, 1024, 1001, 980, 960, 941, 923, 906, 889,
            874, 859, 844, 830, 817, 804, 791, 779, 768, 756, 745, 734,
            724, 714, 704, 694, 685, 676, 667, 658, 650, 642, 633, 626,
            618, 610, 603, 595, 588, 581, 574, 567, 561, 554, 548, 542,
            535, 529, 523, 517, 512, 506, 500, 495, 489, 484, 478, 473,
            468, 463, 458, 453, 448, 443, 438, 434, 429, 424, 420, 415,
            411, 407, 402, 398, 394, 390, 386, 382, 377, 373, 370, 366,
            362, 358, 354, 350, 347, 343, 339, 336, 332, 329, 325, 322,
            318, 315, 311, 308, 305, 302, 298, 295, 292, 289, 286, 282,
            279, 276, 273, 270, 267, 264, 261, 258, 256, 253, 250, 247,
            244, 241, 239, 236, 233, 230, 228, 225, 222, 220, 217, 215,
            212, 209, 207, 204, 202, 199, 197, 194, 192, 190, 187, 185,
            182, 180, 178, 175, 173, 171, 168, 166, 164, 162, 159, 157,
            155, 153, 151, 149, 146, 144, 142, 140, 138, 136, 134, 132,
            130, 128, 126, 123, 121, 119, 117, 115, 114, 112, 110, 108,
            106, 104, 102, 100, 98, 96, 94, 93, 91, 89, 87, 85,
            83, 82, 80, 78, 76, 74, 73, 71, 69, 67, 66, 64,
            62, 61, 59, 57, 55, 54, 52, 50, 49, 47, 46, 44,
            42, 41, 39, 37, 36, 34, 33, 31, 30, 28, 26, 25,
            23, 22, 20, 19, 17, 16, 14, 13, 11, 10, 8, 7,
            5, 4, 2, 1};

    final ZstdHuffmanEncoding huffman = new ZstdHuffmanEncoding();
    private final int[] count = new int[MAX_SEQUENCE_SYMBOL + 1];
    private final short[] normalized = new short[MAX_SEQUENCE_SYMBOL + 1];
    private final byte[] countScratch = new byte[512];

    static int minGain(long sourceSize, int strategy) {
        int minLog = strategy >= ZstdCompressionParameters.BTULTRA ? strategy - 1 : 6;
        return (int) ((sourceSize >>> minLog) + 2);
    }

    static int writeRawLiterals(byte[] destination, int at, int capacity, byte[] source, int from, int size) {
        int headerSize = 1 + (size > 31 ? 1 : 0) + (size > 4095 ? 1 : 0);
        if ((long) size + headerSize > capacity) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        switch (headerSize) {
            case 1 -> destination[at] = (byte) (SET_BASIC + (size << 3));
            case 2 -> ZstdOctets.writeShort(destination, at, SET_BASIC + (1 << 2) + (size << 4));
            default -> ZstdOctets.writeInt(destination, at, SET_BASIC + (3 << 2) + (size << 4));
        }
        System.arraycopy(source, from, destination, at + headerSize, size);
        return size + headerSize;
    }

    private static int writeRleLiterals(byte[] destination, int at, byte[] source, int from, int size) {
        int headerSize = 1 + (size > 31 ? 1 : 0) + (size > 4095 ? 1 : 0);
        switch (headerSize) {
            case 1 -> destination[at] = (byte) (SET_RLE + (size << 3));
            case 2 -> ZstdOctets.writeShort(destination, at, SET_RLE + (1 << 2) + (size << 4));
            default -> ZstdOctets.writeInt(destination, at, SET_RLE + (3 << 2) + (size << 4));
        }
        destination[at + headerSize] = source[from];
        return headerSize + 1;
    }

    private static boolean isAllOneByte(byte[] source, int from, int size) {
        for (int at = from + 1; at < from + size; at++) {
            if (source[at] != source[from]) {
                return false;
            }
        }
        return true;
    }

    private static int minLiteralsToCompress(int strategy, int huffmanRepeat) {
        int shift = Math.min(9 - strategy, 3);
        return huffmanRepeat == ZstdHuffmanTable.REPEAT_VALID ? 6 : 8 << shift;
    }

    int compressLiterals(byte[] destination, int at, int capacity, byte[] source, int from, int size,
                         ZstdEntropyTables previous, ZstdEntropyTables next, int strategy,
                         boolean isLiteralCompressionDisabled, boolean isSuspectUncompressible) {
        int headerSize = 3 + (size >= 1024 ? 1 : 0) + (size >= 16 * 1024 ? 1 : 0);
        boolean isSingleStream = size < 256;
        int type = SET_COMPRESSED;
        next.copyHuffmanFrom(previous);
        if (isLiteralCompressionDisabled) {
            return writeRawLiterals(destination, at, capacity, source, from, size);
        }
        if (size < minLiteralsToCompress(strategy, previous.huffmanRepeat)) {
            return writeRawLiterals(destination, at, capacity, source, from, size);
        }
        if (capacity < headerSize + 1) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        int[] repeat = {previous.huffmanRepeat};
        int flags = (strategy < ZstdCompressionParameters.LAZY && size <= 1024
                ? ZstdHuffmanEncoding.FLAG_PREFER_REPEAT : 0)
                | (strategy >= ZstdCompressionParameters.BTULTRA ? ZstdHuffmanEncoding.FLAG_OPTIMAL_DEPTH : 0)
                | (isSuspectUncompressible ? ZstdHuffmanEncoding.FLAG_SUSPECT_UNCOMPRESSIBLE : 0);
        if (repeat[0] == ZstdHuffmanTable.REPEAT_VALID && headerSize == 3) {
            isSingleStream = true;
        }
        long compressedSize;
        try {
            compressedSize = huffman.compress(destination, at + headerSize, capacity - headerSize, source, from, size,
                    ZstdHistogram.SYMBOL_VALUE_MAX, LITERAL_HUFFMAN_LOG, isSingleStream, next.huffman, repeat, flags);
        } catch (ZstdFailure failed) {
            compressedSize = -1;
        }
        if (repeat[0] != ZstdHuffmanTable.REPEAT_NONE) {
            type = SET_REPEAT;
        }
        long gain = minGain(size, strategy);
        if (compressedSize == 0 || compressedSize < 0 || compressedSize >= size - gain) {
            next.copyHuffmanFrom(previous);
            return writeRawLiterals(destination, at, capacity, source, from, size);
        }
        if (compressedSize == 1) {
            if (size >= 8 || isAllOneByte(source, from, size)) {
                next.copyHuffmanFrom(previous);
                return writeRleLiterals(destination, at, source, from, size);
            }
        }
        if (type == SET_COMPRESSED) {
            next.huffmanRepeat = ZstdHuffmanTable.REPEAT_CHECK;
        }
        int compressed = (int) compressedSize;
        switch (headerSize) {
            case 3 -> {
                int header = type + ((isSingleStream ? 0 : 1) << 2) + (size << 4) + (compressed << 14);
                destination[at] = (byte) header;
                destination[at + 1] = (byte) (header >>> 8);
                destination[at + 2] = (byte) (header >>> 16);
            }
            case 4 -> ZstdOctets.writeInt(destination, at, type + (2 << 2) + (size << 4) + (compressed << 18));
            default -> {
                ZstdOctets.writeInt(destination, at, type + (3 << 2) + (size << 4) + (compressed << 22));
                destination[at + 4] = (byte) (compressed >>> 10);
            }
        }
        return headerSize + compressed;
    }

    private static long entropyCost(int[] counts, int max, long total) {
        int cost = 0;
        for (int symbol = 0; symbol <= max; symbol++) {
            int norm = (int) ((256L * counts[symbol]) / total);
            if (counts[symbol] != 0 && norm == 0) {
                norm = 1;
            }
            cost += counts[symbol] * INVERSE_PROBABILITY_LOG_256[norm];
        }
        return (cost & 0xFFFFFFFFL) >>> 8;
    }

    static long fseBitCost(ZstdFseEncoding table, int[] counts, int max) {
        int accuracyLog = 8;
        long cost = 0;
        if (table.maxSymbolValue < max) {
            return ERROR_GENERIC;
        }
        for (int symbol = 0; symbol <= max; symbol++) {
            int tableLog = table.tableLog;
            int badCost = (tableLog + 1) << accuracyLog;
            int bitCost = table.bitCost(symbol, accuracyLog);
            if (counts[symbol] == 0) {
                continue;
            }
            if (Integer.compareUnsigned(bitCost, badCost) >= 0) {
                return ERROR_GENERIC;
            }
            cost += (long) counts[symbol] * (bitCost & 0xFFFFFFFFL);
        }
        return cost >>> accuracyLog;
    }

    static long crossEntropyCost(short[] norm, int accuracyLog, int[] counts, int max) {
        int shift = 8 - accuracyLog;
        long cost = 0;
        for (int symbol = 0; symbol <= max; symbol++) {
            int normAccuracy = norm[symbol] != -1 ? norm[symbol] : 1;
            int norm256 = normAccuracy << shift;
            cost += (long) counts[symbol] * INVERSE_PROBABILITY_LOG_256[norm256];
        }
        return cost >>> 8;
    }

    private long countCost(int[] counts, int max, int sequences, int fseLog) {
        int log = ZstdFseEncoding.optimalTableLog(fseLog, sequences, max);
        try {
            ZstdFseEncoding.normalizeCount(normalized, log, counts, sequences, max, sequences >= 2048);
            return ZstdFseEncoding.writeCount(countScratch, 0, countScratch.length, normalized, max, log);
        } catch (ZstdFailure failed) {
            return -failed.error().code();
        }
    }

    int selectEncodingType(int[] repeatMode, int[] counts, int max, long mostFrequent, int sequences, int fseLog,
                           ZstdFseEncoding previousTable, short[] defaultNorm, int defaultNormLog,
                           boolean isDefaultAllowed, int strategy) {
        if (mostFrequent == sequences) {
            repeatMode[0] = ZstdEntropyTables.FSE_REPEAT_NONE;
            if (isDefaultAllowed && sequences <= 2) {
                return SET_BASIC;
            }
            return SET_RLE;
        }
        if (strategy < ZstdCompressionParameters.LAZY) {
            if (isDefaultAllowed) {
                int staticSequencesMax = 1000;
                int multiplier = 10 - strategy;
                int baseLog = 3;
                long dynamicSequencesMin = ((1L << defaultNormLog) * multiplier) >>> baseLog;
                if (repeatMode[0] == ZstdEntropyTables.FSE_REPEAT_VALID && sequences < staticSequencesMax) {
                    return SET_REPEAT;
                }
                if (sequences < dynamicSequencesMin || mostFrequent < (sequences >>> (defaultNormLog - 1))) {
                    repeatMode[0] = ZstdEntropyTables.FSE_REPEAT_NONE;
                    return SET_BASIC;
                }
            }
        } else {
            long basicCost = isDefaultAllowed ? crossEntropyCost(defaultNorm, defaultNormLog, counts, max)
                    : ERROR_GENERIC;
            long repeatCost = repeatMode[0] != ZstdEntropyTables.FSE_REPEAT_NONE
                    ? fseBitCost(previousTable, counts, max) : ERROR_GENERIC;
            long countCost = countCost(counts, max, sequences, fseLog);
            long compressedCost = (countCost << 3) + entropyCost(counts, max, sequences);
            if (Long.compareUnsigned(basicCost, repeatCost) <= 0 && Long.compareUnsigned(basicCost, compressedCost) <= 0) {
                repeatMode[0] = ZstdEntropyTables.FSE_REPEAT_NONE;
                return SET_BASIC;
            }
            if (Long.compareUnsigned(repeatCost, compressedCost) <= 0) {
                return SET_REPEAT;
            }
        }
        repeatMode[0] = ZstdEntropyTables.FSE_REPEAT_CHECK;
        return SET_COMPRESSED;
    }

    int buildTable(byte[] destination, int at, int capacity, ZstdFseEncoding nextTable, int fseLog, int type,
                   int[] counts, int max, byte[] codes, int codesFrom, int sequences, short[] defaultNorm,
                   int defaultNormLog, int defaultMax, ZstdFseEncoding previousTable) {
        switch (type) {
            case SET_RLE -> {
                nextTable.buildRle(max);
                if (capacity == 0) {
                    throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                }
                destination[at] = codes[codesFrom];
                return 1;
            }
            case SET_REPEAT -> {
                nextTable.copyFrom(previousTable);
                return 0;
            }
            case SET_BASIC -> {
                nextTable.build(defaultNorm, defaultMax, defaultNormLog);
                return 0;
            }
            default -> {
                int lessOne = sequences;
                int log = ZstdFseEncoding.optimalTableLog(fseLog, sequences, max);
                int last = codes[codesFrom + sequences - 1] & 0xFF;
                if (counts[last] > 1) {
                    counts[last]--;
                    lessOne--;
                }
                ZstdFseEncoding.normalizeCount(normalized, log, counts, lessOne, max, lessOne >= 2048);
                int countSize = ZstdFseEncoding.writeCount(destination, at, capacity, normalized, max, log);
                nextTable.build(normalized, max, log);
                return countSize;
            }
        }
    }

    record Statistics(int literalLengthType, int offsetType, int matchLengthType, int size, int lastCountSize) {
    }

    Statistics buildSequencesStatistics(ZstdSequenceStore store, int sequences, ZstdEntropyTables previous,
                                        ZstdEntropyTables next, byte[] destination, int at, int end, int strategy) {
        int output = at;
        int lastCountSize = 0;
        store.toCodes();
        int from = store.sequencesStart;
        int[] maxHolder = {ZstdEntropyTables.MAX_LITERAL_LENGTH};
        int mostFrequent = ZstdHistogram.countFast(count, maxHolder, store.literalLengthCodes, from, sequences);
        int[] repeat = {previous.literalLengthRepeat};
        int literalLengthType = selectEncodingType(repeat, count, maxHolder[0], mostFrequent, sequences,
                ZstdEntropyTables.LITERAL_LENGTH_LOG, previous.literalLengths,
                ZstdSequenceTable.LITERAL_LENGTH_DEFAULT_NORM, ZstdSequenceTable.LITERAL_LENGTH_DEFAULT_LOG, true,
                strategy);
        next.literalLengthRepeat = repeat[0];
        int countSize = buildTable(destination, output, end - output, next.literalLengths,
                ZstdEntropyTables.LITERAL_LENGTH_LOG, literalLengthType, count, maxHolder[0],
                store.literalLengthCodes, from, sequences, ZstdSequenceTable.LITERAL_LENGTH_DEFAULT_NORM,
                ZstdSequenceTable.LITERAL_LENGTH_DEFAULT_LOG, ZstdEntropyTables.MAX_LITERAL_LENGTH,
                previous.literalLengths);
        if (literalLengthType == SET_COMPRESSED) {
            lastCountSize = countSize;
        }
        output += countSize;

        maxHolder[0] = ZstdEntropyTables.MAX_OFFSET;
        mostFrequent = ZstdHistogram.countFast(count, maxHolder, store.offsetCodes, from, sequences);
        boolean isDefaultAllowed = maxHolder[0] <= DEFAULT_MAX_OFFSET;
        repeat[0] = previous.offsetRepeat;
        int offsetType = selectEncodingType(repeat, count, maxHolder[0], mostFrequent, sequences,
                ZstdEntropyTables.OFFSET_LOG, previous.offsets, ZstdSequenceTable.OFFSET_DEFAULT_NORM,
                ZstdSequenceTable.OFFSET_DEFAULT_LOG, isDefaultAllowed, strategy);
        next.offsetRepeat = repeat[0];
        countSize = buildTable(destination, output, end - output, next.offsets, ZstdEntropyTables.OFFSET_LOG,
                offsetType, count, maxHolder[0], store.offsetCodes, from, sequences,
                ZstdSequenceTable.OFFSET_DEFAULT_NORM, ZstdSequenceTable.OFFSET_DEFAULT_LOG, DEFAULT_MAX_OFFSET,
                previous.offsets);
        if (offsetType == SET_COMPRESSED) {
            lastCountSize = countSize;
        }
        output += countSize;

        maxHolder[0] = ZstdEntropyTables.MAX_MATCH_LENGTH;
        mostFrequent = ZstdHistogram.countFast(count, maxHolder, store.matchLengthCodes, from, sequences);
        repeat[0] = previous.matchLengthRepeat;
        int matchLengthType = selectEncodingType(repeat, count, maxHolder[0], mostFrequent, sequences,
                ZstdEntropyTables.MATCH_LENGTH_LOG, previous.matchLengths,
                ZstdSequenceTable.MATCH_LENGTH_DEFAULT_NORM, ZstdSequenceTable.MATCH_LENGTH_DEFAULT_LOG, true,
                strategy);
        next.matchLengthRepeat = repeat[0];
        countSize = buildTable(destination, output, end - output, next.matchLengths,
                ZstdEntropyTables.MATCH_LENGTH_LOG, matchLengthType, count, maxHolder[0],
                store.matchLengthCodes, from, sequences, ZstdSequenceTable.MATCH_LENGTH_DEFAULT_NORM,
                ZstdSequenceTable.MATCH_LENGTH_DEFAULT_LOG, ZstdEntropyTables.MAX_MATCH_LENGTH,
                previous.matchLengths);
        if (matchLengthType == SET_COMPRESSED) {
            lastCountSize = countSize;
        }
        output += countSize;
        return new Statistics(literalLengthType, offsetType, matchLengthType, output - at, lastCountSize);
    }

    static int encodeSequences(byte[] destination, int at, int capacity, ZstdEntropyTables tables,
                               ZstdSequenceStore store, int sequences) {
        ZstdBitWriter bits = new ZstdBitWriter();
        if (!bits.open(destination, at, capacity)) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        byte[] literalLengthCodes = store.literalLengthCodes;
        byte[] offsetCodes = store.offsetCodes;
        byte[] matchLengthCodes = store.matchLengthCodes;
        int first = store.sequencesStart;
        int last = first + sequences - 1;
        ZstdFseEncoding matchLengthTable = tables.matchLengths;
        ZstdFseEncoding offsetTable = tables.offsets;
        ZstdFseEncoding literalLengthTable = tables.literalLengths;
        long matchLengthState = matchLengthTable.initialStateWith(matchLengthCodes[last] & 0xFF);
        long offsetState = offsetTable.initialStateWith(offsetCodes[last] & 0xFF);
        long literalLengthState = literalLengthTable.initialStateWith(literalLengthCodes[last] & 0xFF);
        bits.addBits(store.literalLengths[last], ZstdSequenceTable.LITERAL_LENGTH_BITS[literalLengthCodes[last]]);
        bits.addBits(store.matchLengthBases[last], ZstdSequenceTable.MATCH_LENGTH_BITS[matchLengthCodes[last]]);
        bits.addBits(store.offBases[last] & 0xFFFFFFFFL, offsetCodes[last]);
        bits.flush();
        for (int at2 = last - 1; at2 >= first; at2--) {
            int literalLengthCode = literalLengthCodes[at2];
            int offsetCode = offsetCodes[at2];
            int matchLengthCode = matchLengthCodes[at2];
            int literalBits = ZstdSequenceTable.LITERAL_LENGTH_BITS[literalLengthCode];
            int matchBits = ZstdSequenceTable.MATCH_LENGTH_BITS[matchLengthCode];
            offsetState = offsetTable.encode(bits, offsetState, offsetCode);
            matchLengthState = matchLengthTable.encode(bits, matchLengthState, matchLengthCode);
            literalLengthState = literalLengthTable.encode(bits, literalLengthState, literalLengthCode);
            if (offsetCode + matchBits + literalBits >= 64 - 7 - (9 + 9 + 8)) {
                bits.flush();
            }
            bits.addBits(store.literalLengths[at2], literalBits);
            bits.addBits(store.matchLengthBases[at2], matchBits);
            if (offsetCode + matchBits + literalBits > 56) {
                bits.flush();
            }
            bits.addBits(store.offBases[at2] & 0xFFFFFFFFL, offsetCode);
            bits.flush();
        }
        matchLengthTable.flushState(bits, matchLengthState);
        offsetTable.flushState(bits, offsetState);
        literalLengthTable.flushState(bits, literalLengthState);
        int size = bits.close();
        if (size == 0) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        return size;
    }

    int compressSequenceStore(byte[] destination, int at, int capacity, ZstdSequenceStore store,
                              ZstdEntropyTables previous, ZstdEntropyTables next, int strategy,
                              boolean isLiteralCompressionDisabled) {
        int output = at;
        int end = at + capacity;
        int sequences = store.count();
        int literalSize = store.literalSize();
        boolean isSuspectUncompressible = sequences == 0 || literalSize / sequences >= SUSPECT_UNCOMPRESSIBLE_LITERAL_RATIO;
        output += compressLiterals(destination, output, capacity, store.literals, store.literalsStart, literalSize,
                previous, next, strategy, isLiteralCompressionDisabled, isSuspectUncompressible);
        if (end - output < 3 + 1) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        if (sequences < 128) {
            destination[output++] = (byte) sequences;
        } else if (sequences < LONG_SEQUENCE_COUNT) {
            destination[output] = (byte) ((sequences >>> 8) + 0x80);
            destination[output + 1] = (byte) sequences;
            output += 2;
        } else {
            destination[output] = (byte) 0xFF;
            ZstdOctets.writeShort(destination, output + 1, sequences - LONG_SEQUENCE_COUNT);
            output += 3;
        }
        if (sequences == 0) {
            next.copyFseFrom(previous);
            return output - at;
        }
        int header = output++;
        Statistics statistics = buildSequencesStatistics(store, sequences, previous, next, destination, output, end,
                strategy);
        destination[header] = (byte) ((statistics.literalLengthType() << 6) + (statistics.offsetType() << 4)
                + (statistics.matchLengthType() << 2));
        output += statistics.size();
        int bitstreamSize = encodeSequences(destination, output, end - output, next, store, sequences);
        output += bitstreamSize;
        if (statistics.lastCountSize() != 0 && statistics.lastCountSize() + bitstreamSize < 4) {
            return 0;
        }
        return output - at;
    }

    int compressSequenceStoreOrRefuse(byte[] destination, int at, int capacity, ZstdSequenceStore store,
                                      ZstdEntropyTables previous, ZstdEntropyTables next, int strategy,
                                      boolean isLiteralCompressionDisabled, int blockSize) {
        int compressed;
        try {
            compressed = compressSequenceStore(destination, at, capacity, store, previous, next, strategy,
                    isLiteralCompressionDisabled);
        } catch (ZstdFailure failure) {
            if (failure.error() == ZstdError.DST_SIZE_TOO_SMALL && blockSize <= capacity) {
                return 0;
            }
            throw failure;
        }
        if (compressed == 0) {
            return 0;
        }
        int maxCompressedSize = blockSize - minGain(blockSize, strategy);
        if (compressed >= maxCompressedSize) {
            return 0;
        }
        return compressed;
    }
}
