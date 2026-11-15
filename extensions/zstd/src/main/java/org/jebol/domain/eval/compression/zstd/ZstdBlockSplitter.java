package org.jebol.domain.eval.compression.zstd;

import org.jebol.domain.eval.compression.CodecTrace;

final class ZstdBlockSplitter {

    private static final int MIN_SEQUENCES_BLOCK_SPLITTING = 300;
    private static final int MAX_BLOCK_SPLITS = 196;
    private static final int RLE_MAX_LENGTH = 25;
    private static final int MAX_HUFFMAN_HEADER_SIZE = 128;
    private static final int MAX_FSE_HEADERS_SIZE = ((52 + 1) * 9 + (35 + 1) * 9 + (31 + 1) * 8 + 7) / 8;
    private static final int COMPRESS_LITERALS_SIZE_MIN = 63;

    private final ZstdCompressionContext context;
    private final int[] partitions = new int[MAX_BLOCK_SPLITS + 1];
    private int splitCount;
    private final int[] count = new int[256];
    private final byte[] huffmanHeader = new byte[MAX_HUFFMAN_HEADER_SIZE];
    private final byte[] fseHeaders = new byte[MAX_FSE_HEADERS_SIZE];
    private int huffmanType;
    private int huffmanHeaderSize;
    private int literalLengthType;
    private int offsetType;
    private int matchLengthType;
    private int fseTablesSize;
    private ZstdSequenceStore fullChunk;
    private ZstdSequenceStore firstHalf;
    private ZstdSequenceStore secondHalf;
    private ZstdSequenceStore currentChunk;
    private ZstdSequenceStore nextChunk;

    ZstdBlockSplitter(ZstdCompressionContext context) {
        this.context = context;
    }

    private void prepareViews() {
        ZstdSequenceStore store = context.store;
        if (fullChunk == null || fullChunk.offBases != store.offBases) {
            fullChunk = store.sharingArrays();
            firstHalf = store.sharingArrays();
            secondHalf = store.sharingArrays();
            currentChunk = store.sharingArrays();
            nextChunk = store.sharingArrays();
        }
    }

    private int buildLiteralStatistics(ZstdSequenceStore store, ZstdEntropyTables previous, ZstdEntropyTables next,
                                       int flags) {
        int size = store.literalSize();
        next.copyHuffmanFrom(previous);
        if (context.isLiteralCompressionDisabled) {
            huffmanType = ZstdBlockEncoding.SET_BASIC;
            return 0;
        }
        int minimum = previous.huffmanRepeat == ZstdHuffmanTable.REPEAT_VALID ? 6 : COMPRESS_LITERALS_SIZE_MIN;
        if (size <= minimum) {
            huffmanType = ZstdBlockEncoding.SET_BASIC;
            return 0;
        }
        int[] maxSymbol = {255};
        int largest = ZstdHistogram.count(count, maxSymbol, store.literals, store.literalsStart, size);
        if (largest == size) {
            huffmanType = ZstdBlockEncoding.SET_RLE;
            return 0;
        }
        if (largest <= (size >>> 7) + 4) {
            huffmanType = ZstdBlockEncoding.SET_BASIC;
            return 0;
        }
        int repeat = previous.huffmanRepeat;
        if (repeat == ZstdHuffmanTable.REPEAT_CHECK && !previous.huffman.isValidFor(count, maxSymbol[0])) {
            repeat = ZstdHuffmanTable.REPEAT_NONE;
        }
        next.huffman.clear();
        ZstdHuffmanEncoding huffman = context.encoding.huffman;
        int log = huffman.optimalTableLog(11, size, maxSymbol[0], next.huffman, count, flags);
        log = huffman.buildTable(next.huffman, count, maxSymbol[0], log);
        long newSize = next.huffman.estimateCompressedSize(count, maxSymbol[0]);
        long headerSize;
        try {
            headerSize = huffman.writeTable(huffmanHeader, 0, huffmanHeader.length, next.huffman, maxSymbol[0], log);
        } catch (ZstdFailure failure) {
            headerSize = -failure.error().code();
        }
        if (repeat != ZstdHuffmanTable.REPEAT_NONE) {
            long oldSize = previous.huffman.estimateCompressedSize(count, maxSymbol[0]);
            if (oldSize < size && (Long.compareUnsigned(oldSize, headerSize + newSize) <= 0
                    || Long.compareUnsigned(headerSize + 12, size) >= 0)) {
                next.copyHuffmanFrom(previous);
                huffmanType = ZstdBlockEncoding.SET_REPEAT;
                return 0;
            }
        }
        if (Long.compareUnsigned(newSize + headerSize, size) >= 0) {
            next.copyHuffmanFrom(previous);
            huffmanType = ZstdBlockEncoding.SET_BASIC;
            return 0;
        }
        huffmanType = ZstdBlockEncoding.SET_COMPRESSED;
        next.huffmanRepeat = ZstdHuffmanTable.REPEAT_CHECK;
        if (headerSize < 0) {
            throw ZstdError.GENERIC.failure();
        }
        return (int) headerSize;
    }

    private int buildSequenceStatistics(ZstdSequenceStore store, ZstdEntropyTables previous, ZstdEntropyTables next) {
        int sequences = store.count();
        if (sequences == 0) {
            literalLengthType = ZstdBlockEncoding.SET_BASIC;
            offsetType = ZstdBlockEncoding.SET_BASIC;
            matchLengthType = ZstdBlockEncoding.SET_BASIC;
            next.literalLengthRepeat = ZstdEntropyTables.FSE_REPEAT_NONE;
            next.offsetRepeat = ZstdEntropyTables.FSE_REPEAT_NONE;
            next.matchLengthRepeat = ZstdEntropyTables.FSE_REPEAT_NONE;
            return 0;
        }
        ZstdBlockEncoding.Statistics statistics = context.encoding.buildSequencesStatistics(store, sequences, previous,
                next, fseHeaders, 0, fseHeaders.length, context.parameters.strategy);
        literalLengthType = statistics.literalLengthType();
        offsetType = statistics.offsetType();
        matchLengthType = statistics.matchLengthType();
        return statistics.size();
    }

    private long estimateLiterals(ZstdSequenceStore store, ZstdHuffmanTable table, boolean isEntropyWritten) {
        int size = store.literalSize();
        int headerSize = 3 + (size >= 1024 ? 1 : 0) + (size >= 16 * 1024 ? 1 : 0);
        boolean isSingleStream = size < 256;
        if (huffmanType == ZstdBlockEncoding.SET_BASIC) {
            return size;
        }
        if (huffmanType == ZstdBlockEncoding.SET_RLE) {
            return 1;
        }
        int[] maxSymbol = {255};
        try {
            ZstdHistogram.count(count, maxSymbol, store.literals, store.literalsStart, size);
        } catch (ZstdFailure failure) {
            return size;
        }
        long estimate = table.estimateCompressedSize(count, maxSymbol[0]);
        if (isEntropyWritten) {
            estimate += huffmanHeaderSize;
        }
        if (!isSingleStream) {
            estimate += 6;
        }
        return estimate + headerSize;
    }

    private long estimateSymbolType(int type, byte[] codes, int from, int sequences, int maxCode,
                                    ZstdFseEncoding table, int[] additionalBits, short[] defaultNorm,
                                    int defaultNormLog) {
        int[] max = {maxCode};
        ZstdHistogram.countFast(count, max, codes, from, sequences);
        long bits;
        if (type == ZstdBlockEncoding.SET_BASIC) {
            bits = ZstdBlockEncoding.crossEntropyCost(defaultNorm, defaultNormLog, count, max[0]);
        } else if (type == ZstdBlockEncoding.SET_RLE) {
            bits = 0;
        } else {
            bits = ZstdBlockEncoding.fseBitCost(table, count, max[0]);
        }
        if (bits < 0) {
            return sequences * 10L;
        }
        for (int at = from; at < from + sequences; at++) {
            bits += additionalBits != null ? additionalBits[codes[at]] : codes[at];
        }
        return bits >>> 3;
    }

    private long estimateSequences(ZstdSequenceStore store, ZstdEntropyTables tables, boolean isEntropyWritten) {
        int sequences = store.count();
        long headerSize = 1 + 1 + (sequences >= 128 ? 1 : 0) + (sequences >= 0x7F00 ? 1 : 0);
        int from = store.sequencesStart;
        long estimate = estimateSymbolType(offsetType, store.offsetCodes, from, sequences,
                ZstdEntropyTables.MAX_OFFSET, tables.offsets, null, ZstdSequenceTable.OFFSET_DEFAULT_NORM,
                ZstdSequenceTable.OFFSET_DEFAULT_LOG);
        estimate += estimateSymbolType(literalLengthType, store.literalLengthCodes, from, sequences,
                ZstdEntropyTables.MAX_LITERAL_LENGTH, tables.literalLengths, ZstdSequenceTable.LITERAL_LENGTH_BITS,
                ZstdSequenceTable.LITERAL_LENGTH_DEFAULT_NORM, ZstdSequenceTable.LITERAL_LENGTH_DEFAULT_LOG);
        estimate += estimateSymbolType(matchLengthType, store.matchLengthCodes, from, sequences,
                ZstdEntropyTables.MAX_MATCH_LENGTH, tables.matchLengths, ZstdSequenceTable.MATCH_LENGTH_BITS,
                ZstdSequenceTable.MATCH_LENGTH_DEFAULT_NORM, ZstdSequenceTable.MATCH_LENGTH_DEFAULT_LOG);
        if (isEntropyWritten) {
            estimate += fseTablesSize;
        }
        return estimate + headerSize;
    }

    private long estimateSubBlockSize(ZstdSequenceStore store) {
        ZstdEntropyTables previous = context.previousBlock;
        ZstdEntropyTables next = context.nextBlock;
        int flags = context.parameters.strategy >= ZstdCompressionParameters.BTULTRA
                ? ZstdHuffmanEncoding.FLAG_OPTIMAL_DEPTH : 0;
        huffmanHeaderSize = buildLiteralStatistics(store, previous, next, flags);
        fseTablesSize = buildSequenceStatistics(store, previous, next);
        long literals = estimateLiterals(store, next.huffman, huffmanType == ZstdBlockEncoding.SET_COMPRESSED);
        long sequences = estimateSequences(store, next, true);
        return sequences + literals + ZstdCompressionContext.BLOCK_HEADER_SIZE;
    }

    private long estimateOrRefuse(ZstdSequenceStore store) {
        try {
            return estimateSubBlockSize(store);
        } catch (ZstdFailure failure) {
            return -1;
        }
    }

    private void deriveSplits(int start, int end) {
        int middle = (start + end) / 2;
        if (end - start < MIN_SEQUENCES_BLOCK_SPLITTING || splitCount >= MAX_BLOCK_SPLITS) {
            return;
        }
        ZstdSequenceStore original = context.store;
        fullChunk.deriveChunk(original, start, end);
        firstHalf.deriveChunk(original, start, middle);
        secondHalf.deriveChunk(original, middle, end);
        long whole = estimateOrRefuse(fullChunk);
        long first = estimateOrRefuse(firstHalf);
        long second = estimateOrRefuse(secondHalf);
        CodecTrace.current().note(() -> "zstd split " + start + " " + middle + " " + end + ": " + whole + " "
                + first + " " + second);
        if (whole < 0 || first < 0 || second < 0) {
            return;
        }
        if (first + second < whole) {
            deriveSplits(start, middle);
            partitions[splitCount] = middle;
            splitCount++;
            deriveSplits(middle, end);
        }
    }

    private static long resolveRepeatToRawOffset(int[] repeats, int offBase, int isLiteralLengthZero) {
        int adjusted = offBase - 1 + isLiteralLengthZero;
        if (adjusted == ZstdSequenceStore.REPEAT_COUNT) {
            return (repeats[0] - 1L) & 0xFFFFFFFFL;
        }
        return repeats[adjusted] & 0xFFFFFFFFL;
    }

    private static void resolveOffsetCodes(int[] decoderRepeats, int[] compressorRepeats, ZstdSequenceStore store) {
        int sequences = store.count();
        int longLiteralIndex = store.longLengthType == ZstdSequenceStore.LONG_LENGTH_LITERAL
                ? store.longLengthPosition : sequences;
        for (int at = 0; at < sequences; at++) {
            int absolute = store.sequencesStart + at;
            int isZero = store.literalLengths[absolute] == 0 && at != longLiteralIndex ? 1 : 0;
            int offBase = store.offBases[absolute];
            if (offBase >= 1 && offBase <= ZstdSequenceStore.REPEAT_COUNT) {
                long decoderOffset = resolveRepeatToRawOffset(decoderRepeats, offBase, isZero);
                long compressorOffset = resolveRepeatToRawOffset(compressorRepeats, offBase, isZero);
                if (decoderOffset != compressorOffset) {
                    store.offBases[absolute] = (int) compressorOffset + ZstdSequenceStore.REPEAT_COUNT;
                }
            }
            ZstdSequenceStore.updateRepeats(decoderRepeats, store.offBases[absolute], isZero);
            ZstdSequenceStore.updateRepeats(compressorRepeats, offBase, isZero);
        }
    }

    private int compressSingleBlock(ZstdSequenceStore store, int[] decoderRepeats, int[] compressorRepeats,
                                    byte[] destination, int at, int capacity, byte[] source, int from, int size,
                                    boolean isLast, boolean isPartition) {
        int[] originalRepeats = decoderRepeats.clone();
        if (isPartition) {
            resolveOffsetCodes(decoderRepeats, compressorRepeats, store);
        }
        if (capacity < ZstdCompressionContext.BLOCK_HEADER_SIZE) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        int compressed = context.encoding.compressSequenceStoreOrRefuse(destination,
                at + ZstdCompressionContext.BLOCK_HEADER_SIZE, capacity - ZstdCompressionContext.BLOCK_HEADER_SIZE,
                store, context.previousBlock, context.nextBlock, context.parameters.strategy,
                context.isLiteralCompressionDisabled, size);
        if (!context.isFirstBlock && compressed < RLE_MAX_LENGTH && isAllOneByte(source, from, size)) {
            compressed = 1;
        }
        int written;
        if (compressed == 0) {
            written = ZstdCompressionContext.writeRawBlock(destination, at, capacity, source, from, size, isLast);
            System.arraycopy(originalRepeats, 0, decoderRepeats, 0, 3);
        } else if (compressed == 1) {
            written = ZstdCompressionContext.writeRleBlock(destination, at, capacity, source[from], size, isLast);
            System.arraycopy(originalRepeats, 0, decoderRepeats, 0, 3);
        } else {
            context.confirmRepeatsAndEntropy();
            int header = (isLast ? 1 : 0) + (2 << 1) + (compressed << 3);
            ZstdCompressionContext.writeBlockHeader(destination, at, header);
            written = ZstdCompressionContext.BLOCK_HEADER_SIZE + compressed;
        }
        if (context.previousBlock.offsetRepeat == ZstdEntropyTables.FSE_REPEAT_VALID) {
            context.previousBlock.offsetRepeat = ZstdEntropyTables.FSE_REPEAT_CHECK;
        }
        return written;
    }

    private static boolean isAllOneByte(byte[] source, int from, int size) {
        for (int at = from + 1; at < from + size; at++) {
            if (source[at] != source[from]) {
                return false;
            }
        }
        return true;
    }

    int compressBlock(byte[] destination, int at, int capacity, byte[] source, int from, int blockSize,
                      boolean isLast) {
        if (!context.buildSequenceStore(source, from, blockSize)) {
            if (context.previousBlock.offsetRepeat == ZstdEntropyTables.FSE_REPEAT_VALID) {
                context.previousBlock.offsetRepeat = ZstdEntropyTables.FSE_REPEAT_CHECK;
            }
            return ZstdCompressionContext.writeRawBlock(destination, at, capacity, source, from, blockSize, isLast);
        }
        prepareViews();
        ZstdSequenceStore store = context.store;
        int sequences = store.count();
        int[] decoderRepeats = context.previousBlock.repeats.clone();
        int[] compressorRepeats = context.previousBlock.repeats.clone();
        splitCount = 0;
        if (sequences > 4) {
            deriveSplits(0, sequences);
            partitions[splitCount] = sequences;
        }
        if (splitCount == 0) {
            return compressSingleBlock(store, decoderRepeats, compressorRepeats, destination, at, capacity, source,
                    from, blockSize, isLast, false);
        }
        int output = at;
        int room = capacity;
        int input = from;
        long totalSourceBytes = 0;
        int written = 0;
        currentChunk.deriveChunk(store, 0, partitions[0]);
        for (int split = 0; split <= splitCount; split++) {
            boolean isLastPartition = split == splitCount;
            boolean isLastOfAll = false;
            long sourceBytes = currentChunk.countLiteralBytes() + currentChunk.countMatchBytes();
            totalSourceBytes += sourceBytes;
            if (isLastPartition) {
                sourceBytes += blockSize - totalSourceBytes;
                isLastOfAll = isLast;
            } else {
                nextChunk.deriveChunk(store, partitions[split], partitions[split + 1]);
            }
            int chunk = compressSingleBlock(currentChunk, decoderRepeats, compressorRepeats, destination, output, room,
                    source, input, (int) sourceBytes, isLastOfAll, true);
            input += (int) sourceBytes;
            output += chunk;
            room -= chunk;
            written += chunk;
            currentChunk.copyFrom(nextChunk);
        }
        System.arraycopy(decoderRepeats, 0, context.previousBlock.repeats, 0, 3);
        return written;
    }
}
