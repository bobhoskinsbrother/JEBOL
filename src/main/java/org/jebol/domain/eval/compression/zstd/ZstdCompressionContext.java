package org.jebol.domain.eval.compression.zstd;

import org.jebol.domain.eval.hashing.RunningXxHash64;

final class ZstdCompressionContext {

    static final int BLOCK_SIZE_MAX = 1 << 17;
    static final int FRAME_HEADER_SIZE_MAX = 18;
    static final int BLOCK_HEADER_SIZE = 3;

    private static final int STAGE_CREATED = 0;
    private static final int STAGE_INIT = 1;
    private static final int STAGE_ONGOING = 2;
    private static final int STAGE_ENDING = 3;

    private static final int MIN_CBLOCK_SIZE = 2;
    private static final int RLE_MAX_LENGTH = 25;
    private static final long INDEX_OVERFLOW_MARGIN = 16L * 1024 * 1024;
    private static final int HASH_LOG3_MAX = 17;
    private static final int WORKSPACE_TOO_LARGE_FACTOR = 3;
    private static final int WORKSPACE_TOO_LARGE_DURATION = 128;
    private static final int ALIGNMENT = 64;
    private static final int TEMPORARY_WORKSPACE = 8920;
    private static final int BLOCK_STATE_SIZE = 5632;
    private static final int OPT_SIZE = (1 << 12) + 3;
    private static final int[] SPLIT_LEVELS = {0, 0, 1, 2, 2, 3, 3, 4, 4, 4};

    final ZstdMatchState state = new ZstdMatchState();
    final ZstdBlockEncoding encoding = new ZstdBlockEncoding();
    final RunningXxHash64 checksum = new RunningXxHash64();
    private final ZstdTableRegion tables = new ZstdTableRegion();
    final ZstdLongDistanceMatching longDistance = new ZstdLongDistanceMatching();
    final ZstdBlockSplitter splitter = new ZstdBlockSplitter(this);

    ZstdCompressionParameters parameters;
    int compressionLevel;
    boolean isRowMatchFinderUsed;
    boolean isPostBlockSplitterUsed;
    boolean isLongDistanceMatchingUsed;
    boolean isContentSizeWritten;
    boolean isChecksumWritten;
    boolean isLiteralCompressionDisabled;

    ZstdEntropyTables previousBlock = new ZstdEntropyTables();
    ZstdEntropyTables nextBlock = new ZstdEntropyTables();
    ZstdSequenceStore store;
    private int storeSequences = -1;
    private int storeLiterals = -1;

    private boolean isInitialized;
    private long workspaceSize;
    private long workspaceUsed;
    private int oversizedDuration;
    private int tableValidEnd;
    private int tableEnd;
    private int tagTableLength;

    boolean isFirstBlock;
    int stage = STAGE_CREATED;
    long pledgedSourceSizePlusOne;
    long consumedSourceSize;
    long producedCompressedSize;
    int blockSizeMax;

    byte[] inBuffer = new byte[0];
    byte[] outBuffer = new byte[0];

    static long compressBound(long size) {
        return size + (size >>> 8) + (size < 128 * 1024 ? (128L * 1024 - size) >>> 11 : 0);
    }

    private static long aligned64(long size) {
        return (size + ALIGNMENT - 1) & ~(ALIGNMENT - 1L);
    }

    private static boolean isChainTableAllocated(int strategy, boolean isRowUsed) {
        return strategy != ZstdCompressionParameters.FAST
                && !(isRowUsed && ZstdCompressionParameters.isRowMatchFinderSupported(strategy));
    }

    private long neededSpace(ZstdCompressionParameters cParameters, long pledgedSize, long bufferIn,
                             long bufferOut) {
        long windowSize = Math.max(1, Math.min(1L << cParameters.windowLog, pledgedSize < 0 ? Long.MAX_VALUE
                : pledgedSize));
        long blockSize = Math.min(BLOCK_SIZE_MAX, windowSize);
        long maxSequences = blockSize / (cParameters.minMatch == 3 ? 3 : 4);
        long tokenSpace = ZstdDecompressionContext.WILDCOPY_OVERLENGTH + blockSize
                + aligned64(maxSequences * 8) + 3 * maxSequences;
        long blockStateSpace = 2L * BLOCK_STATE_SIZE;
        long matchStateSize = matchStateSize(cParameters);
        long ldmSpace = 0;
        long ldmSequenceSpace = 0;
        if (isLongDistanceMatchingUsed) {
            ldmSpace = longDistance.tableSize();
            ldmSequenceSpace = aligned64(blockSize / longDistance.minMatchLength() * 12);
        }
        return TEMPORARY_WORKSPACE + blockStateSpace + ldmSpace + ldmSequenceSpace + matchStateSize + tokenSpace
                + bufferIn + bufferOut;
    }

    private long matchStateSize(ZstdCompressionParameters cParameters) {
        long chainSize = isChainTableAllocated(cParameters.strategy, isRowMatchFinderUsed)
                ? 1L << cParameters.chainLog : 0;
        long hashSize = 1L << cParameters.hashLog;
        int hashLog3 = cParameters.minMatch == 3 ? Math.min(HASH_LOG3_MAX, cParameters.windowLog) : 0;
        long hash3Size = hashLog3 != 0 ? 1L << hashLog3 : 0;
        long tableSpace = (chainSize + hashSize + hash3Size) * 4;
        long optSpace = cParameters.strategy >= ZstdCompressionParameters.BTOPT
                ? aligned64(53 * 4) + aligned64(36 * 4) + aligned64(32 * 4) + aligned64(256 * 4)
                + aligned64(OPT_SIZE * 8L) + aligned64(OPT_SIZE * 28L) : 0;
        long lazySpace = isRowMatchFinderUsed && ZstdCompressionParameters.isRowMatchFinderSupported(
                cParameters.strategy) ? aligned64(hashSize) : 0;
        return tableSpace + optSpace + ALIGNMENT * 2 + lazySpace;
    }

    void reset(ZstdCompressionParameters applied, long pledgedSize, boolean isBuffered) {
        isFirstBlock = true;
        parameters = applied;
        if (isLongDistanceMatchingUsed) {
            longDistance.adjustParameters(applied);
        }
        long windowSize = Math.max(1, Math.min(1L << applied.windowLog,
                pledgedSize == ZstdCompressionParameters.CONTENT_SIZE_UNKNOWN ? Long.MAX_VALUE : pledgedSize));
        int blockSize = (int) Math.min(BLOCK_SIZE_MAX, windowSize);
        int maxSequences = blockSize / (applied.minMatch == 3 ? 3 : 4);
        long bufferOut = isBuffered ? compressBound(blockSize) + 1 : 0;
        long bufferIn = isBuffered ? windowSize + blockSize : 0;
        boolean isIndexTooClose = state.nextSource - state.base > ZstdMatchState.CURRENT_MAX - INDEX_OVERFLOW_MARGIN;
        boolean isIndexReset = isIndexTooClose || !isInitialized;
        long needed = neededSpace(applied, pledgedSize, bufferIn, bufferOut);
        oversizedDuration++;
        long available = workspaceSize - workspaceUsed;
        boolean isTooSmall = workspaceSize < needed;
        boolean isWasteful = available >= needed * WORKSPACE_TOO_LARGE_FACTOR
                && oversizedDuration > WORKSPACE_TOO_LARGE_DURATION;
        if (isTooSmall || isWasteful) {
            isIndexReset = true;
            workspaceSize = needed;
            oversizedDuration = 0;
            tables.forget();
            tableValidEnd = 0;
            tableEnd = 0;
            state.tagTable = null;
            tagTableLength = 0;
        }
        workspaceUsed = needed;
        pledgedSourceSizePlusOne = pledgedSize + 1;
        consumedSourceSize = 0;
        producedCompressedSize = 0;
        isContentSizeWritten = pledgedSize != ZstdCompressionParameters.CONTENT_SIZE_UNKNOWN;
        blockSizeMax = blockSize;
        checksum.reset();
        stage = STAGE_INIT;
        previousBlock.reset();
        resetMatchState(applied, isIndexReset);
        if (storeSequences != maxSequences || storeLiterals != blockSize) {
            store = new ZstdSequenceStore(Math.max(maxSequences, 1), blockSize);
            storeSequences = maxSequences;
            storeLiterals = blockSize;
        }
        if (isLongDistanceMatchingUsed) {
            longDistance.reset(blockSize);
        }
        if (inBuffer.length != bufferIn) {
            inBuffer = new byte[(int) bufferIn];
        }
        if (outBuffer.length != bufferOut) {
            outBuffer = new byte[(int) bufferOut];
        }
        isInitialized = true;
    }

    private void resetMatchState(ZstdCompressionParameters applied, boolean isIndexReset) {
        ZstdMatchState match = state;
        int chainSize = isChainTableAllocated(applied.strategy, isRowMatchFinderUsed) ? 1 << applied.chainLog : 0;
        int hashSize = 1 << applied.hashLog;
        int hashLog3 = applied.minMatch == 3 ? Math.min(HASH_LOG3_MAX, applied.windowLog) : 0;
        int hash3Size = hashLog3 != 0 ? 1 << hashLog3 : 0;
        if (isIndexReset) {
            match.initWindow();
            tableValidEnd = 0;
        }
        match.hashLog3 = hashLog3;
        match.isLazySkipping = false;
        match.clearWindow();
        match.nextToUpdate = match.dictionaryLimit;
        match.loadedDictionaryEnd = 0;
        match.optimal.literalLengthSum = 0;
        match.hashTable = new ZstdIndexTable(tables, 0, hashSize);
        match.chainTable = new ZstdIndexTable(tables, hashSize, chainSize);
        match.hashTable3 = new ZstdIndexTable(tables, hashSize + chainSize, hash3Size);
        tableEnd = hashSize + chainSize + hash3Size;
        if (tableValidEnd < tableEnd) {
            tables.zero(tableValidEnd, tableEnd);
            tableValidEnd = tableEnd;
        }
        if (isRowMatchFinderUsed && ZstdCompressionParameters.isRowMatchFinderSupported(applied.strategy)) {
            int tagBytes = (int) aligned64(hashSize);
            if (match.tagTable == null || tagTableLength != tagBytes) {
                byte[] fresh = new byte[tagBytes];
                if (match.tagTable != null) {
                    int kept = Math.min(tagBytes, tagTableLength);
                    System.arraycopy(match.tagTable, tagTableLength - kept, fresh, tagBytes - kept, kept);
                }
                match.tagTable = fresh;
                tagTableLength = tagBytes;
            }
            match.hashSalt = bitmix(match.hashSalt, 8) ^ bitmix(match.hashSaltEntropy & 0xFFFFFFFFL, 4);
            int rowLog = Math.max(4, Math.min(applied.searchLog, 6));
            match.rowHashLog = applied.hashLog - rowLog;
        }
        match.parameters = applied;
    }

    private static long bitmix(long seed, long length) {
        long value = seed;
        value ^= Long.rotateRight(value, 49) ^ Long.rotateRight(value, 24);
        value *= 0x9FB21C651E98DF25L;
        value ^= (value >>> 35) + length;
        value *= 0x9FB21C651E98DF25L;
        return value ^ (value >>> 28);
    }

    private int writeFrameHeader(byte[] destination, int at, int capacity, long pledgedSize) {
        int checksumFlag = isChecksumWritten ? 1 : 0;
        long windowSize = 1L << parameters.windowLog;
        boolean isSingleSegment = isContentSizeWritten && Long.compareUnsigned(windowSize, pledgedSize) >= 0;
        int windowLogByte = (parameters.windowLog - ZstdCompressionParameters.WINDOW_LOG_MIN) << 3;
        int contentSizeCode = isContentSizeWritten
                ? (Long.compareUnsigned(pledgedSize, 256) >= 0 ? 1 : 0)
                + (Long.compareUnsigned(pledgedSize, 65536 + 256) >= 0 ? 1 : 0)
                + (Long.compareUnsigned(pledgedSize, 0xFFFFFFFFL) >= 0 ? 1 : 0)
                : 0;
        int descriptor = (checksumFlag << 2) + ((isSingleSegment ? 1 : 0) << 5) + (contentSizeCode << 6);
        if (capacity < FRAME_HEADER_SIZE_MAX) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        ZstdOctets.writeInt(destination, at, ZstdFrameHeader.MAGIC_NUMBER);
        int position = at + 4;
        destination[position++] = (byte) descriptor;
        if (!isSingleSegment) {
            destination[position++] = (byte) windowLogByte;
        }
        switch (contentSizeCode) {
            case 1 -> {
                ZstdOctets.writeShort(destination, position, (int) (pledgedSize - 256));
                position += 2;
            }
            case 2 -> {
                ZstdOctets.writeInt(destination, position, (int) pledgedSize);
                position += 4;
            }
            case 3 -> {
                ZstdOctets.writeLong(destination, position, pledgedSize);
                position += 8;
            }
            default -> {
                if (isSingleSegment) {
                    destination[position++] = (byte) pledgedSize;
                }
            }
        }
        return position - at;
    }

    private static boolean isRle(byte[] source, int from, int length) {
        for (int at = from + 1; at < from + length; at++) {
            if (source[at] != source[from]) {
                return false;
            }
        }
        return true;
    }

    void confirmRepeatsAndEntropy() {
        ZstdEntropyTables held = previousBlock;
        previousBlock = nextBlock;
        nextBlock = held;
    }

    boolean buildSequenceStore(byte[] source, int from, int size) {
        ZstdMatchState match = state;
        if (size < MIN_CBLOCK_SIZE + BLOCK_HEADER_SIZE + 1 + 1) {
            return false;
        }
        store.reset();
        match.optimal.symbolCosts = previousBlock;
        long current = match.indexOf(from);
        if (current > match.nextToUpdate + 384) {
            match.nextToUpdate = current - Math.min(192, current - match.nextToUpdate - 384);
        }
        System.arraycopy(previousBlock.repeats, 0, nextBlock.repeats, 0, 3);
        int lastLiterals;
        if (isLongDistanceMatchingUsed) {
            lastLiterals = longDistance.compressBlock(this, source, from, size);
        } else {
            match.longDistanceSequences = null;
            lastLiterals = ZstdMatchers.compressBlock(match, store, nextBlock.repeats, from, size,
                    isRowMatchFinderUsed);
        }
        store.storeLastLiterals(source, from + size - lastLiterals, lastLiterals);
        return true;
    }

    private int compressBlockInternal(byte[] destination, int at, int capacity, byte[] source, int from, int size) {
        int compressed;
        if (!buildSequenceStore(source, from, size)) {
            compressed = 0;
        } else {
            compressed = encoding.compressSequenceStoreOrRefuse(destination, at, capacity, store, previousBlock,
                    nextBlock, parameters.strategy, isLiteralCompressionDisabled, size);
            if (!isFirstBlock && compressed < RLE_MAX_LENGTH && isRle(source, from, size)) {
                compressed = 1;
                destination[at] = source[from];
            }
        }
        if (compressed > 1) {
            confirmRepeatsAndEntropy();
        }
        if (previousBlock.offsetRepeat == ZstdEntropyTables.FSE_REPEAT_VALID) {
            previousBlock.offsetRepeat = ZstdEntropyTables.FSE_REPEAT_CHECK;
        }
        return compressed;
    }

    static int writeRawBlock(byte[] destination, int at, int capacity, byte[] source, int from, int size,
                             boolean isLast) {
        int header = (isLast ? 1 : 0) + (size << 3);
        if ((long) size + BLOCK_HEADER_SIZE > capacity) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        writeBlockHeader(destination, at, header);
        System.arraycopy(source, from, destination, at + BLOCK_HEADER_SIZE, size);
        return BLOCK_HEADER_SIZE + size;
    }

    static int writeRleBlock(byte[] destination, int at, int capacity, byte value, int size, boolean isLast) {
        int header = (isLast ? 1 : 0) + (1 << 1) + (size << 3);
        if (capacity < 4) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        writeBlockHeader(destination, at, header);
        destination[at + 3] = value;
        return 4;
    }

    static void writeBlockHeader(byte[] destination, int at, int header) {
        destination[at] = (byte) header;
        destination[at + 1] = (byte) (header >>> 8);
        destination[at + 2] = (byte) (header >>> 16);
    }

    private void correctOverflowIfNeeded(int from, int end) {
        ZstdMatchState match = state;
        int cycleLog = ZstdCompressionParameters.cycleLog(parameters.chainLog, parameters.strategy);
        long maxDistance = 1L << parameters.windowLog;
        long current = match.indexOf(end) & 0xFFFFFFFFL;
        if (current > ZstdMatchState.CURRENT_MAX) {
            long cycleSize = 1L << cycleLog;
            long cycleMask = cycleSize - 1;
            long at = match.indexOf(from) & 0xFFFFFFFFL;
            long currentCycle = at & cycleMask;
            long correctionOfCycle = currentCycle < ZstdMatchState.WINDOW_START_INDEX
                    ? Math.max(cycleSize, ZstdMatchState.WINDOW_START_INDEX) : 0;
            long newCurrent = currentCycle + correctionOfCycle + Math.max(maxDistance, cycleSize);
            long correction = (at - newCurrent) & 0xFFFFFFFFL;
            match.base += correction;
            match.dictionaryBase += correction;
            match.lowLimit = match.lowLimit < correction + ZstdMatchState.WINDOW_START_INDEX
                    ? ZstdMatchState.WINDOW_START_INDEX : match.lowLimit - correction;
            match.dictionaryLimit = match.dictionaryLimit < correction + ZstdMatchState.WINDOW_START_INDEX
                    ? ZstdMatchState.WINDOW_START_INDEX : match.dictionaryLimit - correction;
            match.overflowCorrections++;
            ZstdMatchers.reduceIndex(match, parameters, (int) correction, isRowMatchFinderUsed);
            match.nextToUpdate = match.nextToUpdate < correction ? 0 : match.nextToUpdate - correction;
            match.loadedDictionaryEnd = 0;
        }
    }

    private void enforceMaxDistance(int blockEnd, long maxDistance) {
        ZstdMatchState match = state;
        long blockEndIndex = match.indexOf(blockEnd) & 0xFFFFFFFFL;
        if (blockEndIndex > maxDistance + match.loadedDictionaryEnd) {
            long newLowLimit = blockEndIndex - maxDistance;
            if (match.lowLimit < newLowLimit) {
                match.lowLimit = newLowLimit;
            }
            if (match.dictionaryLimit < match.lowLimit) {
                match.dictionaryLimit = match.lowLimit;
            }
            match.loadedDictionaryEnd = 0;
        }
    }

    private int optimalBlockSize(byte[] source, int from, int size, long savings) {
        if (size < 128 * 1024 || blockSizeMax < 128 * 1024) {
            return Math.min(size, blockSizeMax);
        }
        if (savings < 3) {
            return 128 * 1024;
        }
        int splitLevel = SPLIT_LEVELS[parameters.strategy];
        return ZstdBlockSplitting.splitBlock(source, from, blockSizeMax, splitLevel);
    }

    private int compressFrameChunk(byte[] destination, int at, int capacity, byte[] source, int from, int size,
                                   boolean isLastFrameChunk) {
        int remaining = size;
        int input = from;
        int output = at;
        int room = capacity;
        long maxDistance = 1L << parameters.windowLog;
        long savings = consumedSourceSize - producedCompressedSize;
        if (isChecksumWritten && size != 0) {
            checksum.update(source, from, size);
        }
        while (remaining != 0) {
            int blockSize = optimalBlockSize(source, input, remaining, savings);
            boolean isLastBlock = isLastFrameChunk && blockSize == remaining;
            if (room < BLOCK_HEADER_SIZE + MIN_CBLOCK_SIZE + 1) {
                throw ZstdError.DST_SIZE_TOO_SMALL.failure();
            }
            correctOverflowIfNeeded(input, input + blockSize);
            enforceMaxDistance(input, maxDistance);
            if (state.nextToUpdate < state.lowLimit) {
                state.nextToUpdate = state.lowLimit;
            }
            int compressed;
            if (isPostBlockSplitterUsed) {
                compressed = splitter.compressBlock(destination, output, room, source, input, blockSize, isLastBlock);
            } else {
                compressed = compressBlockInternal(destination, output + BLOCK_HEADER_SIZE,
                        room - BLOCK_HEADER_SIZE, source, input, blockSize);
                if (compressed == 0) {
                    compressed = writeRawBlock(destination, output, room, source, input, blockSize, isLastBlock);
                } else {
                    int header = compressed == 1
                            ? (isLastBlock ? 1 : 0) + (1 << 1) + (blockSize << 3)
                            : (isLastBlock ? 1 : 0) + (2 << 1) + (compressed << 3);
                    writeBlockHeader(destination, output, header);
                    compressed += BLOCK_HEADER_SIZE;
                }
            }
            savings += (long) blockSize - compressed;
            input += blockSize;
            remaining -= blockSize;
            output += compressed;
            room -= compressed;
            isFirstBlock = false;
        }
        if (isLastFrameChunk && output > at) {
            stage = STAGE_ENDING;
        }
        return output - at;
    }

    int compressContinue(byte[] destination, int at, int capacity, byte[] source, int from, int size,
                         boolean isLastFrameChunk) {
        if (stage == STAGE_CREATED) {
            throw ZstdError.STAGE_WRONG.failure();
        }
        int headerSize = 0;
        int output = at;
        int room = capacity;
        if (stage == STAGE_INIT) {
            headerSize = writeFrameHeader(destination, output, room, pledgedSourceSizePlusOne - 1);
            room -= headerSize;
            output += headerSize;
            stage = STAGE_ONGOING;
        }
        if (size == 0) {
            return headerSize;
        }
        if (!state.updateWindow(source, from, size, false)) {
            state.nextToUpdate = state.dictionaryLimit;
        }
        if (isLongDistanceMatchingUsed) {
            longDistance.updateWindow(source, from, size);
        }
        int compressed = compressFrameChunk(destination, output, room, source, from, size, isLastFrameChunk);
        consumedSourceSize += size;
        producedCompressedSize += compressed + headerSize;
        if (pledgedSourceSizePlusOne != 0 && consumedSourceSize + 1 > pledgedSourceSizePlusOne
                && pledgedSourceSizePlusOne != 0) {
            if (Long.compareUnsigned(consumedSourceSize + 1, pledgedSourceSizePlusOne) > 0) {
                throw ZstdError.SRC_SIZE_WRONG.failure();
            }
        }
        return compressed + headerSize;
    }

    private int writeEpilogue(byte[] destination, int at, int capacity) {
        int output = at;
        int room = capacity;
        if (stage == STAGE_CREATED) {
            throw ZstdError.STAGE_WRONG.failure();
        }
        if (stage == STAGE_INIT) {
            boolean wasWritten = isContentSizeWritten;
            int headerSize = writeFrameHeader(destination, output, room, 0);
            isContentSizeWritten = wasWritten;
            room -= headerSize;
            output += headerSize;
            stage = STAGE_ONGOING;
        }
        if (stage != STAGE_ENDING) {
            if (room < 3) {
                throw ZstdError.DST_SIZE_TOO_SMALL.failure();
            }
            writeBlockHeader(destination, output, 1);
            output += BLOCK_HEADER_SIZE;
            room -= BLOCK_HEADER_SIZE;
        }
        if (isChecksumWritten) {
            if (room < 4) {
                throw ZstdError.DST_SIZE_TOO_SMALL.failure();
            }
            ZstdOctets.writeInt(destination, output, (int) checksum.digest());
            output += 4;
        }
        stage = STAGE_CREATED;
        return output - at;
    }

    int compressEnd(byte[] destination, int at, int capacity, byte[] source, int from, int size) {
        int compressed = compressContinue(destination, at, capacity, source, from, size, true);
        int ending = writeEpilogue(destination, at + compressed, capacity - compressed);
        if (pledgedSourceSizePlusOne != 0 && pledgedSourceSizePlusOne != consumedSourceSize + 1) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        return compressed + ending;
    }

    void applyParameters(ZstdCompressionParameters applied, int level) {
        compressionLevel = level;
        isRowMatchFinderUsed = ZstdCompressionParameters.isRowMatchFinderSupported(applied.strategy)
                && applied.windowLog > 14;
        isPostBlockSplitterUsed = applied.strategy >= ZstdCompressionParameters.BTOPT && applied.windowLog >= 17;
        isLongDistanceMatchingUsed = applied.strategy >= ZstdCompressionParameters.BTOPT && applied.windowLog >= 27;
        isLiteralCompressionDisabled = applied.strategy == ZstdCompressionParameters.FAST
                && applied.targetLength > 0;
        isChecksumWritten = false;
    }

    static byte[] compressOnce(byte[] source, int level) {
        ZstdCompressionContext context = new ZstdCompressionContext();
        ZstdCompressionParameters applied = ZstdCompressionParameters.forLevel(level, source.length);
        context.applyParameters(applied, level == 0 ? ZstdCompressionParameters.DEFAULT_LEVEL : level);
        context.reset(applied, source.length, false);
        byte[] destination = new byte[(int) compressBound(source.length)];
        int size = context.compressEnd(destination, 0, destination.length, source, 0, source.length);
        return java.util.Arrays.copyOf(destination, size);
    }
}
