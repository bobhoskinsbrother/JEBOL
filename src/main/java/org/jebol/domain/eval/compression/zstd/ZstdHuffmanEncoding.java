package org.jebol.domain.eval.compression.zstd;

import java.util.Arrays;

final class ZstdHuffmanEncoding {

    static final int TABLE_LOG_MAX = 12;
    static final int TABLE_LOG_DEFAULT = 11;
    static final int FLAG_OPTIMAL_DEPTH = 1 << 1;
    static final int FLAG_PREFER_REPEAT = 1 << 2;
    static final int FLAG_SUSPECT_UNCOMPRESSIBLE = 1 << 3;

    private static final int SYMBOLS = ZstdHistogram.SYMBOL_VALUE_MAX + 1;
    private static final int START_NODE = SYMBOLS;
    private static final int RANK_POSITION_TABLE_SIZE = 192;
    private static final int RANK_POSITION_LOG_BUCKETS_BEGIN = (RANK_POSITION_TABLE_SIZE - 1) - 32 - 1;
    private static final int RANK_POSITION_DISTINCT_COUNT_CUTOFF = RANK_POSITION_LOG_BUCKETS_BEGIN
            + ZstdOctets.highBit(RANK_POSITION_LOG_BUCKETS_BEGIN);
    private static final int NO_SYMBOL = 0xF0F0F0F0;
    private static final int WEIGHT_TABLE_LOG = 6;
    private static final int BLOCK_SIZE_MAX = 128 * 1024;
    private static final int SUSPECT_SAMPLE_SIZE = 4096;
    private static final int SUSPECT_SAMPLE_RATIO = 10;
    private static final int INSERTION_SORT_THRESHOLD = 8;

    private final long[] nodeCount = new long[2 * SYMBOLS + 1];
    private final int[] nodeParent = new int[2 * SYMBOLS + 1];
    private final int[] nodeSymbol = new int[2 * SYMBOLS + 1];
    private final int[] nodeBits = new int[2 * SYMBOLS + 1];
    private final int[] rankBase = new int[RANK_POSITION_TABLE_SIZE];
    private final int[] rankCurrent = new int[RANK_POSITION_TABLE_SIZE];
    private final int[] count = new int[SYMBOLS];
    private final ZstdHuffmanTable built = new ZstdHuffmanTable();
    private final byte[] weightScratch = new byte[SYMBOLS];
    private final byte[] headerScratch = new byte[1024];

    private static int indexOfCount(long count) {
        return count < RANK_POSITION_DISTINCT_COUNT_CUTOFF
                ? (int) count
                : 63 - Long.numberOfLeadingZeros(count) + RANK_POSITION_LOG_BUCKETS_BEGIN;
    }

    private void swapNodes(int first, int second) {
        long countHeld = nodeCount[first];
        int parentHeld = nodeParent[first];
        int symbolHeld = nodeSymbol[first];
        int bitsHeld = nodeBits[first];
        nodeCount[first] = nodeCount[second];
        nodeParent[first] = nodeParent[second];
        nodeSymbol[first] = nodeSymbol[second];
        nodeBits[first] = nodeBits[second];
        nodeCount[second] = countHeld;
        nodeParent[second] = parentHeld;
        nodeSymbol[second] = symbolHeld;
        nodeBits[second] = bitsHeld;
    }

    private void insertionSort(int base, int low, int high) {
        int size = high - low + 1;
        int origin = base + low;
        for (int at = 1; at < size; at++) {
            long keyCount = nodeCount[origin + at];
            int keyParent = nodeParent[origin + at];
            int keySymbol = nodeSymbol[origin + at];
            int keyBits = nodeBits[origin + at];
            int before = at - 1;
            while (before >= 0 && nodeCount[origin + before] < keyCount) {
                nodeCount[origin + before + 1] = nodeCount[origin + before];
                nodeParent[origin + before + 1] = nodeParent[origin + before];
                nodeSymbol[origin + before + 1] = nodeSymbol[origin + before];
                nodeBits[origin + before + 1] = nodeBits[origin + before];
                before--;
            }
            nodeCount[origin + before + 1] = keyCount;
            nodeParent[origin + before + 1] = keyParent;
            nodeSymbol[origin + before + 1] = keySymbol;
            nodeBits[origin + before + 1] = keyBits;
        }
    }

    private int partition(int base, int low, int high) {
        long pivot = nodeCount[base + high];
        int before = low - 1;
        for (int at = low; at < high; at++) {
            if (nodeCount[base + at] > pivot) {
                before++;
                swapNodes(base + before, base + at);
            }
        }
        swapNodes(base + before + 1, base + high);
        return before + 1;
    }

    private void quickSort(int base, int lowest, int highest) {
        int low = lowest;
        int high = highest;
        if (high - low < INSERTION_SORT_THRESHOLD) {
            insertionSort(base, low, high);
            return;
        }
        while (low < high) {
            int pivot = partition(base, low, high);
            if (pivot - low < high - pivot) {
                quickSort(base, low, pivot - 1);
                low = pivot + 1;
            } else {
                quickSort(base, pivot + 1, high);
                high = pivot - 1;
            }
        }
    }

    private void sort(int[] counts, int maxSymbol) {
        int symbols = maxSymbol + 1;
        Arrays.fill(rankBase, 0);
        Arrays.fill(rankCurrent, 0);
        for (int symbol = 0; symbol < symbols; symbol++) {
            rankBase[indexOfCount(counts[symbol] & 0xFFFFFFFFL)]++;
        }
        for (int rank = RANK_POSITION_TABLE_SIZE - 1; rank > 0; rank--) {
            rankBase[rank - 1] = (rankBase[rank - 1] + rankBase[rank]) & 0xFFFF;
            rankCurrent[rank - 1] = rankBase[rank - 1];
        }
        for (int symbol = 0; symbol < symbols; symbol++) {
            long symbolCount = counts[symbol] & 0xFFFFFFFFL;
            int rank = indexOfCount(symbolCount) + 1;
            int position = rankCurrent[rank]++;
            nodeCount[1 + position] = symbolCount;
            nodeSymbol[1 + position] = symbol;
        }
        for (int rank = RANK_POSITION_DISTINCT_COUNT_CUTOFF; rank < RANK_POSITION_TABLE_SIZE - 1; rank++) {
            int bucketSize = rankCurrent[rank] - rankBase[rank];
            int bucketStart = rankBase[rank];
            if (bucketSize > 1) {
                quickSort(1 + bucketStart, 0, bucketSize - 1);
            }
        }
    }

    private int buildTree(int maxSymbol) {
        int nonNullRank = maxSymbol;
        while (nodeCount[1 + nonNullRank] == 0) {
            nonNullRank--;
        }
        int nodeNumber = START_NODE;
        int lowSymbol = nonNullRank;
        int nodeRoot = nodeNumber + lowSymbol - 1;
        int lowNode = nodeNumber;
        nodeCount[1 + nodeNumber] = nodeCount[1 + lowSymbol] + nodeCount[1 + lowSymbol - 1];
        nodeParent[1 + lowSymbol] = nodeNumber;
        nodeParent[1 + lowSymbol - 1] = nodeNumber;
        nodeNumber++;
        lowSymbol -= 2;
        for (int node = nodeNumber; node <= nodeRoot; node++) {
            nodeCount[1 + node] = 1L << 30;
        }
        nodeCount[0] = 1L << 31;
        while (nodeNumber <= nodeRoot) {
            int first = nodeCount[1 + lowSymbol] < nodeCount[1 + lowNode] ? lowSymbol-- : lowNode++;
            int second = nodeCount[1 + lowSymbol] < nodeCount[1 + lowNode] ? lowSymbol-- : lowNode++;
            nodeCount[1 + nodeNumber] = nodeCount[1 + first] + nodeCount[1 + second];
            nodeParent[1 + first] = nodeNumber;
            nodeParent[1 + second] = nodeNumber;
            nodeNumber++;
        }
        nodeBits[1 + nodeRoot] = 0;
        for (int node = nodeRoot - 1; node >= START_NODE; node--) {
            nodeBits[1 + node] = nodeBits[1 + nodeParent[1 + node]] + 1;
        }
        for (int node = 0; node <= nonNullRank; node++) {
            nodeBits[1 + node] = nodeBits[1 + nodeParent[1 + node]] + 1;
        }
        return nonNullRank;
    }

    private int setMaxHeight(int lastNonNull, int targetBits) {
        int largestBits = nodeBits[1 + lastNonNull];
        if (largestBits <= targetBits) {
            return largestBits;
        }
        int totalCost = 0;
        int baseCost = 1 << (largestBits - targetBits);
        int node = lastNonNull;
        while (nodeBits[1 + node] > targetBits) {
            totalCost += baseCost - (1 << (largestBits - nodeBits[1 + node]));
            nodeBits[1 + node] = targetBits;
            node--;
        }
        while (nodeBits[1 + node] == targetBits) {
            --node;
        }
        totalCost >>= (largestBits - targetBits);
        int[] rankLast = new int[TABLE_LOG_MAX + 2];
        Arrays.fill(rankLast, NO_SYMBOL);
        int currentBits = targetBits;
        for (int position = node; position >= 0; position--) {
            if (nodeBits[1 + position] >= currentBits) {
                continue;
            }
            currentBits = nodeBits[1 + position];
            rankLast[targetBits - currentBits] = position;
        }
        while (totalCost > 0) {
            int bitsToDecrease = ZstdOctets.highBit(totalCost) + 1;
            for (; bitsToDecrease > 1; bitsToDecrease--) {
                int highPosition = rankLast[bitsToDecrease];
                int lowPosition = rankLast[bitsToDecrease - 1];
                if (highPosition == NO_SYMBOL) {
                    continue;
                }
                if (lowPosition == NO_SYMBOL) {
                    break;
                }
                long highTotal = nodeCount[1 + highPosition];
                long lowTotal = 2 * nodeCount[1 + lowPosition];
                if (highTotal <= lowTotal) {
                    break;
                }
            }
            while (bitsToDecrease <= TABLE_LOG_MAX && rankLast[bitsToDecrease] == NO_SYMBOL) {
                bitsToDecrease++;
            }
            totalCost -= 1 << (bitsToDecrease - 1);
            nodeBits[1 + rankLast[bitsToDecrease]]++;
            if (rankLast[bitsToDecrease - 1] == NO_SYMBOL) {
                rankLast[bitsToDecrease - 1] = rankLast[bitsToDecrease];
            }
            if (rankLast[bitsToDecrease] == 0) {
                rankLast[bitsToDecrease] = NO_SYMBOL;
            } else {
                rankLast[bitsToDecrease]--;
                if (nodeBits[1 + rankLast[bitsToDecrease]] != targetBits - bitsToDecrease) {
                    rankLast[bitsToDecrease] = NO_SYMBOL;
                }
            }
        }
        while (totalCost < 0) {
            if (rankLast[1] == NO_SYMBOL) {
                while (nodeBits[1 + node] == targetBits) {
                    node--;
                }
                nodeBits[1 + node + 1]--;
                rankLast[1] = node + 1;
                totalCost++;
                continue;
            }
            nodeBits[1 + rankLast[1] + 1]--;
            rankLast[1]++;
            totalCost++;
        }
        return targetBits;
    }

    private void buildTableFromTree(ZstdHuffmanTable table, int nonNullRank, int maxSymbol, int maxBits) {
        int[] perRank = new int[TABLE_LOG_MAX + 1];
        int[] valuePerRank = new int[TABLE_LOG_MAX + 1];
        int alphabetSize = maxSymbol + 1;
        for (int node = 0; node <= nonNullRank; node++) {
            perRank[nodeBits[1 + node]]++;
        }
        int minimum = 0;
        for (int rank = maxBits; rank > 0; rank--) {
            valuePerRank[rank] = minimum;
            minimum = (minimum + perRank[rank]) & 0xFFFF;
            minimum >>= 1;
        }
        for (int node = 0; node < alphabetSize; node++) {
            table.bits[nodeSymbol[1 + node]] = nodeBits[1 + node];
            table.values[nodeSymbol[1 + node]] = 0;
        }
        for (int symbol = 0; symbol < alphabetSize; symbol++) {
            int bitCount = table.bits[symbol];
            int value = valuePerRank[bitCount];
            valuePerRank[bitCount] = (valuePerRank[bitCount] + 1) & 0xFFFF;
            if (bitCount > 0) {
                table.values[symbol] = value;
            }
        }
        table.tableLog = maxBits;
        table.maxSymbolValue = maxSymbol;
    }

    int buildTable(ZstdHuffmanTable table, int[] counts, int maxSymbol, int maxBits) {
        int targetBits = maxBits == 0 ? TABLE_LOG_DEFAULT : maxBits;
        Arrays.fill(nodeCount, 0);
        Arrays.fill(nodeParent, 0);
        Arrays.fill(nodeSymbol, 0);
        Arrays.fill(nodeBits, 0);
        sort(counts, maxSymbol);
        int nonNullRank = buildTree(maxSymbol);
        int largest = setMaxHeight(nonNullRank, targetBits);
        if (largest > TABLE_LOG_MAX) {
            throw ZstdError.GENERIC.failure();
        }
        buildTableFromTree(table, nonNullRank, maxSymbol, largest);
        return largest;
    }

    private static int compressWeights(byte[] destination, int at, int capacity, byte[] weights, int weightCount) {
        if (weightCount <= 1) {
            return 0;
        }
        int[] weightCounts = new int[TABLE_LOG_MAX + 1];
        int[] maxSymbol = {TABLE_LOG_MAX};
        int maxCount = ZstdHistogram.countSimply(weightCounts, maxSymbol, weights, 0, weightCount);
        if (maxCount == weightCount) {
            return 1;
        }
        if (maxCount == 1) {
            return 0;
        }
        int log = ZstdFseEncoding.optimalTableLog(WEIGHT_TABLE_LOG, weightCount, maxSymbol[0]);
        short[] normalized = new short[TABLE_LOG_MAX + 1];
        ZstdFseEncoding.normalizeCount(normalized, log, weightCounts, weightCount, maxSymbol[0], false);
        int out = at;
        int end = at + capacity;
        out += ZstdFseEncoding.writeCount(destination, out, end - out, normalized, maxSymbol[0], log);
        ZstdFseEncoding table = new ZstdFseEncoding(WEIGHT_TABLE_LOG, TABLE_LOG_MAX);
        table.build(normalized, maxSymbol[0], log);
        int compressed = table.compress(destination, out, end - out, weights, 0, weightCount);
        if (compressed == 0) {
            return 0;
        }
        out += compressed;
        return out - at;
    }

    int writeTable(byte[] destination, int at, int capacity, ZstdHuffmanTable table, int maxSymbol, int log) {
        if (maxSymbol > ZstdHistogram.SYMBOL_VALUE_MAX) {
            throw ZstdError.MAX_SYMBOL_VALUE_TOO_LARGE.failure();
        }
        int[] bitsToWeight = new int[TABLE_LOG_MAX + 1];
        for (int bits = 1; bits < log + 1; bits++) {
            bitsToWeight[bits] = log + 1 - bits;
        }
        for (int symbol = 0; symbol < maxSymbol; symbol++) {
            weightScratch[symbol] = (byte) bitsToWeight[table.bits[symbol]];
        }
        if (capacity < 1) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        int headerSize = compressWeights(destination, at + 1, capacity - 1, weightScratch, maxSymbol);
        if (headerSize > 1 && headerSize < maxSymbol / 2) {
            destination[at] = (byte) headerSize;
            return headerSize + 1;
        }
        if (maxSymbol > (256 - 128)) {
            throw ZstdError.GENERIC.failure();
        }
        if (((maxSymbol + 1) / 2) + 1 > capacity) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        destination[at] = (byte) (128 + (maxSymbol - 1));
        weightScratch[maxSymbol] = 0;
        for (int symbol = 0; symbol < maxSymbol; symbol += 2) {
            destination[at + (symbol / 2) + 1] = (byte) ((weightScratch[symbol] << 4) + weightScratch[symbol + 1]);
        }
        return ((maxSymbol + 1) / 2) + 1;
    }

    private static int cardinality(int[] counts, int maxSymbol) {
        int cardinality = 0;
        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            if (counts[symbol] != 0) {
                cardinality++;
            }
        }
        return cardinality;
    }

    int optimalTableLog(int maxTableLog, int sourceSize, int maxSymbol, ZstdHuffmanTable table, int[] counts,
                        int flags) {
        if ((flags & FLAG_OPTIMAL_DEPTH) == 0) {
            return ZstdFseEncoding.optimalTableLog(maxTableLog, sourceSize, maxSymbol, 1);
        }
        int minTableLog = ZstdOctets.highBit(cardinality(counts, maxSymbol)) + 1;
        long optimalSize = Long.MAX_VALUE - 1;
        int optimalLog = maxTableLog;
        for (int guess = minTableLog; guess <= maxTableLog; guess++) {
            int maxBits;
            try {
                maxBits = buildTable(table, counts, maxSymbol, guess);
            } catch (ZstdFailure failed) {
                continue;
            }
            if (maxBits < guess && guess > minTableLog) {
                break;
            }
            int headerSize;
            try {
                headerSize = writeTable(headerScratch, 0, headerScratch.length, table, maxSymbol, maxBits);
            } catch (ZstdFailure failed) {
                continue;
            }
            long newSize = table.estimateCompressedSize(counts, maxSymbol) + headerSize;
            if (newSize > optimalSize + 1) {
                break;
            }
            if (newSize < optimalSize) {
                optimalSize = newSize;
                optimalLog = guess;
            }
        }
        return optimalLog;
    }

    private static int compressSingleStream(byte[] destination, int at, int capacity, byte[] source, int from,
                                            int size, ZstdHuffmanTable table) {
        if (capacity < 8) {
            return 0;
        }
        ZstdBitWriter bits = new ZstdBitWriter();
        if (!bits.open(destination, at, capacity)) {
            return 0;
        }
        for (int input = from + size - 1; input >= from; input--) {
            int symbol = source[input] & 0xFF;
            bits.addBits(table.values[symbol], table.bits[symbol]);
            bits.flush();
        }
        return bits.close();
    }

    private static int compressFourStreams(byte[] destination, int at, int capacity, byte[] source, int from,
                                           int size, ZstdHuffmanTable table) {
        int segmentSize = (size + 3) / 4;
        int input = from;
        int end = from + size;
        int output = at;
        int outputEnd = at + capacity;
        if (capacity < 6 + 1 + 1 + 1 + 8) {
            return 0;
        }
        if (size < 12) {
            return 0;
        }
        output += 6;
        for (int stream = 0; stream < 4; stream++) {
            int length = stream == 3 ? end - input : segmentSize;
            int compressed = compressSingleStream(destination, output, outputEnd - output, source, input, length,
                    table);
            if (compressed == 0 || compressed > 65535) {
                return 0;
            }
            if (stream < 3) {
                ZstdOctets.writeShort(destination, at + 2 * stream, compressed);
            }
            output += compressed;
            input += segmentSize;
        }
        return output - at;
    }

    private static int compressWithTable(byte[] destination, int start, int at, int end, byte[] source, int from,
                                         int size, boolean isSingleStream, ZstdHuffmanTable table) {
        int compressed = isSingleStream
                ? compressSingleStream(destination, at, end - at, source, from, size, table)
                : compressFourStreams(destination, at, end - at, source, from, size, table);
        if (compressed == 0) {
            return 0;
        }
        int output = at + compressed;
        if (output - start >= size - 1) {
            return 0;
        }
        return output - start;
    }

    int compress(byte[] destination, int at, int capacity, byte[] source, int from, int size, int maxSymbolValue,
                 int huffLog, boolean isSingleStream, ZstdHuffmanTable oldTable, int[] repeat, int flags) {
        int start = at;
        int end = at + capacity;
        if (size == 0) {
            return 0;
        }
        if (capacity == 0) {
            return 0;
        }
        if (size > BLOCK_SIZE_MAX) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        int maxSymbol = maxSymbolValue == 0 ? ZstdHistogram.SYMBOL_VALUE_MAX : maxSymbolValue;
        int log = huffLog == 0 ? TABLE_LOG_DEFAULT : huffLog;
        if ((flags & FLAG_PREFER_REPEAT) != 0 && repeat[0] == ZstdHuffmanTable.REPEAT_VALID) {
            return compressWithTable(destination, start, at, end, source, from, size, isSingleStream, oldTable);
        }
        if ((flags & FLAG_SUSPECT_UNCOMPRESSIBLE) != 0 && size >= SUSPECT_SAMPLE_SIZE * SUSPECT_SAMPLE_RATIO) {
            int[] beginMax = {maxSymbol};
            long largestTotal = ZstdHistogram.countSimply(count, beginMax, source, from, SUSPECT_SAMPLE_SIZE);
            int[] endMax = {maxSymbol};
            largestTotal += ZstdHistogram.countSimply(count, endMax, source, from + size - SUSPECT_SAMPLE_SIZE,
                    SUSPECT_SAMPLE_SIZE);
            if (largestTotal <= ((2 * SUSPECT_SAMPLE_SIZE) >> 7) + 4) {
                return 0;
            }
        }
        int[] maxSymbolHolder = {maxSymbol};
        int largest = ZstdHistogram.count(count, maxSymbolHolder, source, from, size);
        maxSymbol = maxSymbolHolder[0];
        if (largest == size) {
            destination[start] = source[from];
            return 1;
        }
        if (largest <= (size >>> 7) + 4) {
            return 0;
        }
        if (repeat[0] == ZstdHuffmanTable.REPEAT_CHECK && !oldTable.isValidFor(count, maxSymbol)) {
            repeat[0] = ZstdHuffmanTable.REPEAT_NONE;
        }
        if ((flags & FLAG_PREFER_REPEAT) != 0 && repeat[0] != ZstdHuffmanTable.REPEAT_NONE) {
            return compressWithTable(destination, start, at, end, source, from, size, isSingleStream, oldTable);
        }
        log = optimalTableLog(log, size, maxSymbol, built, count, flags);
        log = buildTable(built, count, maxSymbol, log);
        int headerSize = writeTable(destination, at, capacity, built, maxSymbol, log);
        if (repeat[0] != ZstdHuffmanTable.REPEAT_NONE) {
            long oldSize = oldTable.estimateCompressedSize(count, maxSymbol);
            long newSize = built.estimateCompressedSize(count, maxSymbol);
            if (oldSize <= headerSize + newSize || headerSize + 12 >= size) {
                return compressWithTable(destination, start, at, end, source, from, size, isSingleStream, oldTable);
            }
        }
        if (headerSize + 12L >= size) {
            return 0;
        }
        int output = at + headerSize;
        repeat[0] = ZstdHuffmanTable.REPEAT_NONE;
        oldTable.copyFrom(built);
        return compressWithTable(destination, start, output, end, source, from, size, isSingleStream, built);
    }
}
