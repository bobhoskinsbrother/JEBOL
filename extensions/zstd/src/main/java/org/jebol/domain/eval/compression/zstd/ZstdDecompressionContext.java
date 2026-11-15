package org.jebol.domain.eval.compression.zstd;

import org.jebol.domain.eval.hashing.RunningXxHash64;

import java.util.Arrays;

final class ZstdDecompressionContext {

    static final int BLOCK_HEADER_SIZE = 3;
    static final int WILDCOPY_OVERLENGTH = 32;
    static final int LITERAL_EXTRA_SIZE = 1 << 16;

    static final int STAGE_GET_FRAME_HEADER_SIZE = 0;
    static final int STAGE_DECODE_FRAME_HEADER = 1;
    static final int STAGE_DECODE_BLOCK_HEADER = 2;
    static final int STAGE_DECOMPRESS_BLOCK = 3;
    static final int STAGE_DECOMPRESS_LAST_BLOCK = 4;
    static final int STAGE_CHECK_CHECKSUM = 5;
    static final int STAGE_DECODE_SKIPPABLE_HEADER = 6;
    static final int STAGE_SKIP_FRAME = 7;

    static final int BLOCK_RAW = 0;
    static final int BLOCK_RLE = 1;
    static final int BLOCK_COMPRESSED = 2;
    static final int BLOCK_RESERVED = 3;

    private static final int WILDCOPY_VECLEN = 16;
    private static final int MIN_CBLOCK_SIZE = 2;
    private static final int MIN_LITERALS_FOR_FOUR_STREAMS = 6;
    private static final int LONG_SEQUENCE_COUNT = 0x7F00;
    private static final int MAX_LITERAL_LENGTH_SYMBOL = 35;
    private static final int MAX_MATCH_LENGTH_SYMBOL = 52;
    private static final int MAX_OFFSET_SYMBOL = 31;
    private static final int LITERAL_LENGTH_LOG = 9;
    private static final int MATCH_LENGTH_LOG = 9;
    private static final int OFFSET_LOG = 8;
    private static final int STREAM_ACCUMULATOR_MIN = 57;
    private static final int STORED_SEQUENCES = 8;
    private static final int LONG_OFFSET_SHARE_MIN = 7;
    private static final int LONG_OFFSET_ADDITIONAL_BITS = 22;
    private static final long PREFETCH_HISTORY = 1L << 24;
    private static final int PREFETCH_SEQUENCES = 8;
    private static final int[] REPEAT_START = {1, 4, 8};
    private static final int[] SPREAD_ADDED = {0, 1, 2, 1, 4, 4, 4, 4};
    private static final int[] SPREAD_SUBTRACTED = {8, 8, 8, 7, 8, 9, 10, 11};

    private static final int SET_BASIC = 0;
    private static final int SET_RLE = 1;
    private static final int SET_COMPRESSED = 2;
    private static final int SET_REPEAT = 3;

    private static final int NOT_IN_DESTINATION = 0;
    private static final int IN_DESTINATION = 1;
    private static final int SPLIT = 2;

    final ZstdFrameHeader frame = new ZstdFrameHeader();
    final byte[] headerBuffer = new byte[18];
    int stage;
    long expected;
    int headerSize;
    int blockType;
    int rleSize;
    long decodedSize;
    long processedSize;

    private final ZstdHuffmanDecoding huffman = new ZstdHuffmanDecoding();
    private final ZstdSequenceTable literalLengthSpace = new ZstdSequenceTable(LITERAL_LENGTH_LOG);
    private final ZstdSequenceTable offsetSpace = new ZstdSequenceTable(OFFSET_LOG);
    private final ZstdSequenceTable matchLengthSpace = new ZstdSequenceTable(MATCH_LENGTH_LOG);
    private final ZstdNormalizedCount normalizedCount = new ZstdNormalizedCount(MAX_MATCH_LENGTH_SYMBOL + 1);
    private ZstdSequenceTable literalLengths;
    private ZstdSequenceTable offsets;
    private ZstdSequenceTable matchLengths;
    private final int[] repeats = new int[3];
    private boolean hasLiteralEntropy;
    private boolean hasSequenceEntropy;
    private boolean isChecksumValidated;
    private final RunningXxHash64 checksum = new RunningXxHash64();

    private final byte[] literalExtra = new byte[LITERAL_EXTRA_SIZE + WILDCOPY_OVERLENGTH];
    private int literalLocation;
    private int literalBuffer;
    private byte[] literalArray;
    private int literalPointer;
    private int literalBufferEnd;
    private int literalSize;

    private byte[] previousEndArray;
    private int previousEnd;
    private int prefixStart;
    private long virtualStart;
    private byte[] dictionaryEndArray;
    private int dictionaryEnd;

    private final ZstdBitReader bits = new ZstdBitReader();
    private int literalLengthState;
    private int offsetState;
    private int matchLengthState;
    private final long[] previousOffsets = new long[3];
    private long sequenceLiteralLength;
    private long sequenceMatchLength;
    private long sequenceOffset;
    private final long[] storedLiteralLengths = new long[STORED_SEQUENCES];
    private final long[] storedMatchLengths = new long[STORED_SEQUENCES];
    private final long[] storedOffsets = new long[STORED_SEQUENCES];

    void begin() {
        expected = ZstdFrameHeader.PREFIX_SIZE;
        stage = STAGE_GET_FRAME_HEADER_SIZE;
        processedSize = 0;
        decodedSize = 0;
        previousEndArray = null;
        previousEnd = 0;
        prefixStart = 0;
        virtualStart = 0;
        dictionaryEndArray = null;
        dictionaryEnd = 0;
        hasLiteralEntropy = false;
        hasSequenceEntropy = false;
        blockType = BLOCK_RESERVED;
        System.arraycopy(REPEAT_START, 0, repeats, 0, 3);
        literalLengths = literalLengthSpace;
        matchLengths = matchLengthSpace;
        offsets = offsetSpace;
    }

    void decodeFrameHeader(byte[] source, int from, int size) {
        long result = frame.read(source, from, size);
        if (result > 0) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        if (frame.dictionaryId != 0) {
            throw ZstdError.DICTIONARY_WRONG.failure();
        }
        isChecksumValidated = frame.hasChecksum;
        if (isChecksumValidated) {
            checksum.reset();
        }
        processedSize += size;
    }

    void checkContinuity(byte[] destination, int at, long size) {
        if ((destination != previousEndArray || at != previousEnd) && size > 0) {
            dictionaryEndArray = previousEndArray;
            dictionaryEnd = previousEnd;
            long prefixLength = previousEndArray == null ? 0 : previousEnd - prefixStart;
            virtualStart = at - prefixLength;
            prefixStart = at;
            previousEndArray = destination;
            previousEnd = at;
        }
    }

    long nextSourceSizeWithInput(long inputSize) {
        if (!(stage == STAGE_DECOMPRESS_BLOCK || stage == STAGE_DECOMPRESS_LAST_BLOCK)) {
            return expected;
        }
        if (blockType != BLOCK_RAW) {
            return expected;
        }
        return Math.max(1, Math.min(inputSize, expected));
    }

    boolean isSkippingFrame() {
        return stage == STAGE_SKIP_FRAME;
    }

    long decompressContinue(byte[] destination, int at, int capacity, byte[] source, int from, long length) {
        if (length != nextSourceSizeWithInput(length)) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        checkContinuity(destination, at, capacity);
        processedSize += length;
        int size = (int) length;
        switch (stage) {
            case STAGE_GET_FRAME_HEADER_SIZE -> {
                if (ZstdFrameHeader.isSkippableMagic(ZstdOctets.readInt(source, from))) {
                    System.arraycopy(source, from, headerBuffer, 0, size);
                    expected = ZstdFrameHeader.SKIPPABLE_HEADER_SIZE - size;
                    stage = STAGE_DECODE_SKIPPABLE_HEADER;
                    return 0;
                }
                headerSize = ZstdFrameHeader.headerSizeAt(source, from, size);
                System.arraycopy(source, from, headerBuffer, 0, size);
                expected = headerSize - size;
                stage = STAGE_DECODE_FRAME_HEADER;
                return 0;
            }
            case STAGE_DECODE_FRAME_HEADER -> {
                System.arraycopy(source, from, headerBuffer, headerSize - size, size);
                decodeFrameHeader(headerBuffer, 0, headerSize);
                expected = BLOCK_HEADER_SIZE;
                stage = STAGE_DECODE_BLOCK_HEADER;
                return 0;
            }
            case STAGE_DECODE_BLOCK_HEADER -> {
                int header = ZstdOctets.read24(source, from);
                int blockSize = blockSizeOf(header);
                if (blockSize > frame.blockSizeMax) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                expected = blockSize;
                blockType = (header >>> 1) & 3;
                rleSize = header >>> 3;
                boolean isLast = (header & 1) != 0;
                if (blockSize != 0) {
                    stage = isLast ? STAGE_DECOMPRESS_LAST_BLOCK : STAGE_DECOMPRESS_BLOCK;
                    return 0;
                }
                if (isLast) {
                    if (frame.hasChecksum) {
                        expected = 4;
                        stage = STAGE_CHECK_CHECKSUM;
                    } else {
                        expected = 0;
                        stage = STAGE_GET_FRAME_HEADER_SIZE;
                    }
                } else {
                    expected = BLOCK_HEADER_SIZE;
                    stage = STAGE_DECODE_BLOCK_HEADER;
                }
                return 0;
            }
            case STAGE_DECOMPRESS_BLOCK, STAGE_DECOMPRESS_LAST_BLOCK -> {
                long produced;
                switch (blockType) {
                    case BLOCK_COMPRESSED -> {
                        produced = decompressBlock(destination, at, capacity, source, from, size, true);
                        expected = 0;
                    }
                    case BLOCK_RAW -> {
                        produced = copyRawBlock(destination, at, capacity, source, from, size);
                        expected -= produced;
                    }
                    case BLOCK_RLE -> {
                        produced = setRleBlock(destination, at, capacity, source[from], rleSize);
                        expected = 0;
                    }
                    default -> throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                if (produced > frame.blockSizeMax) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                decodedSize += produced;
                if (isChecksumValidated) {
                    checksum.update(destination, at, (int) produced);
                }
                previousEndArray = destination;
                previousEnd = at + (int) produced;
                if (expected > 0) {
                    return produced;
                }
                if (stage == STAGE_DECOMPRESS_LAST_BLOCK) {
                    if (frame.frameContentSize != ZstdFrameHeader.CONTENT_SIZE_UNKNOWN
                            && decodedSize != frame.frameContentSize) {
                        throw ZstdError.CORRUPTION_DETECTED.failure();
                    }
                    if (frame.hasChecksum) {
                        expected = 4;
                        stage = STAGE_CHECK_CHECKSUM;
                    } else {
                        expected = 0;
                        stage = STAGE_GET_FRAME_HEADER_SIZE;
                    }
                } else {
                    stage = STAGE_DECODE_BLOCK_HEADER;
                    expected = BLOCK_HEADER_SIZE;
                }
                return produced;
            }
            case STAGE_CHECK_CHECKSUM -> {
                if (isChecksumValidated && (int) checksum.digest() != ZstdOctets.readInt(source, from)) {
                    throw ZstdError.CHECKSUM_WRONG.failure();
                }
                expected = 0;
                stage = STAGE_GET_FRAME_HEADER_SIZE;
                return 0;
            }
            case STAGE_DECODE_SKIPPABLE_HEADER -> {
                System.arraycopy(source, from, headerBuffer, ZstdFrameHeader.SKIPPABLE_HEADER_SIZE - size, size);
                expected = ZstdOctets.readInt(headerBuffer, ZstdFrameHeader.FRAME_ID_SIZE) & 0xFFFFFFFFL;
                stage = STAGE_SKIP_FRAME;
                return 0;
            }
            case STAGE_SKIP_FRAME -> {
                expected = 0;
                stage = STAGE_GET_FRAME_HEADER_SIZE;
                return 0;
            }
            default -> throw ZstdError.GENERIC.failure();
        }
    }

    private static int blockSizeOf(int header) {
        int type = (header >>> 1) & 3;
        if (type == BLOCK_RLE) {
            return 1;
        }
        if (type == BLOCK_RESERVED) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        return header >>> 3;
    }

    static long readSkippableFrameSize(byte[] source, int from, int size) {
        if (size < ZstdFrameHeader.SKIPPABLE_HEADER_SIZE) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        long declared = ZstdOctets.readInt(source, from + ZstdFrameHeader.FRAME_ID_SIZE) & 0xFFFFFFFFL;
        if (((declared + ZstdFrameHeader.SKIPPABLE_HEADER_SIZE) & 0xFFFFFFFFL) < declared) {
            throw ZstdError.FRAME_PARAMETER_UNSUPPORTED.failure();
        }
        long skippable = ZstdFrameHeader.SKIPPABLE_HEADER_SIZE + declared;
        if (skippable > size) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        return skippable;
    }

    static long findFrameCompressedSize(byte[] source, int from, int size) {
        if (size >= ZstdFrameHeader.SKIPPABLE_HEADER_SIZE
                && ZstdFrameHeader.isSkippableMagic(ZstdOctets.readInt(source, from))) {
            return readSkippableFrameSize(source, from, size);
        }
        ZstdFrameHeader header = new ZstdFrameHeader();
        if (header.read(source, from, size) > 0) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        int at = from + header.headerSize;
        long remaining = size - header.headerSize;
        for (;;) {
            if (remaining < BLOCK_HEADER_SIZE) {
                throw ZstdError.SRC_SIZE_WRONG.failure();
            }
            int blockHeader = ZstdOctets.read24(source, at);
            int blockSize = blockSizeOf(blockHeader);
            if (BLOCK_HEADER_SIZE + (long) blockSize > remaining) {
                throw ZstdError.SRC_SIZE_WRONG.failure();
            }
            at += BLOCK_HEADER_SIZE + blockSize;
            remaining -= BLOCK_HEADER_SIZE + blockSize;
            if ((blockHeader & 1) != 0) {
                break;
            }
        }
        if (header.hasChecksum) {
            if (remaining < 4) {
                throw ZstdError.SRC_SIZE_WRONG.failure();
            }
            at += 4;
        }
        return at - from;
    }

    long decompressFrames(byte[] destination, int at, int capacity, byte[] source, int from, int size) {
        int output = at;
        long room = capacity;
        int input = from;
        int remaining = size;
        boolean isAfterFirstFrame = false;
        while (remaining >= ZstdFrameHeader.PREFIX_SIZE) {
            if (ZstdFrameHeader.isSkippableMagic(ZstdOctets.readInt(source, input))) {
                int skippable = (int) readSkippableFrameSize(source, input, remaining);
                input += skippable;
                remaining -= skippable;
                continue;
            }
            begin();
            checkContinuity(destination, output, room);
            int[] consumed = new int[1];
            long produced;
            try {
                produced = decompressFrame(destination, output, room, source, input, remaining, consumed);
            } catch (ZstdFailure failure) {
                if (failure.error() == ZstdError.PREFIX_UNKNOWN && isAfterFirstFrame) {
                    throw ZstdError.SRC_SIZE_WRONG.failure();
                }
                throw failure;
            }
            input += consumed[0];
            remaining -= consumed[0];
            output += (int) produced;
            room -= produced;
            isAfterFirstFrame = true;
        }
        if (remaining != 0) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        return output - at;
    }

    private long decompressFrame(byte[] destination, int at, long capacity, byte[] source, int from, int size,
                                 int[] consumed) {
        int input = from;
        long end = at + capacity;
        int output = at;
        long remaining = size;
        if (remaining < ZstdFrameHeader.PREFIX_SIZE + 1 + BLOCK_HEADER_SIZE) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        int frameHeaderSize = ZstdFrameHeader.headerSizeAt(source, input, ZstdFrameHeader.PREFIX_SIZE);
        if (remaining < frameHeaderSize + BLOCK_HEADER_SIZE) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        decodeFrameHeader(source, input, frameHeaderSize);
        input += frameHeaderSize;
        remaining -= frameHeaderSize;
        for (;;) {
            if (remaining < BLOCK_HEADER_SIZE) {
                throw ZstdError.SRC_SIZE_WRONG.failure();
            }
            int header = ZstdOctets.read24(source, input);
            int blockSize = blockSizeOf(header);
            int type = (header >>> 1) & 3;
            input += BLOCK_HEADER_SIZE;
            remaining -= BLOCK_HEADER_SIZE;
            if (blockSize > remaining) {
                throw ZstdError.SRC_SIZE_WRONG.failure();
            }
            long produced = switch (type) {
                case BLOCK_COMPRESSED -> decompressBlock(destination, output, end - output, source, input, blockSize,
                        false);
                case BLOCK_RAW -> copyRawBlock(destination, output, end - output, source, input, blockSize);
                case BLOCK_RLE -> setRleBlock(destination, output, end - output, source[input], header >>> 3);
                default -> throw ZstdError.CORRUPTION_DETECTED.failure();
            };
            if (isChecksumValidated) {
                checksum.update(destination, output, (int) produced);
            }
            output += (int) produced;
            input += blockSize;
            remaining -= blockSize;
            if ((header & 1) != 0) {
                break;
            }
        }
        if (frame.frameContentSize != ZstdFrameHeader.CONTENT_SIZE_UNKNOWN
                && output - at != frame.frameContentSize) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        if (frame.hasChecksum) {
            if (remaining < 4) {
                throw ZstdError.CHECKSUM_WRONG.failure();
            }
            if ((int) checksum.digest() != ZstdOctets.readInt(source, input)) {
                throw ZstdError.CHECKSUM_WRONG.failure();
            }
            input += 4;
        }
        consumed[0] = input - from;
        return output - at;
    }

    private static long copyRawBlock(byte[] destination, int at, long capacity, byte[] source, int from, int size) {
        if (size > capacity) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        System.arraycopy(source, from, destination, at, size);
        return size;
    }

    private static long setRleBlock(byte[] destination, int at, long capacity, byte value, int size) {
        if (size > capacity) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        Arrays.fill(destination, at, at + size, value);
        return size;
    }

    private int blockSizeMax() {
        return frame.blockSizeMax;
    }

    private void allocateLiteralsBuffer(byte[] destination, int at, long capacity, int size, boolean isInStream,
                                        int expectedWriteSize, boolean isSplitAtOnce) {
        int largest = blockSizeMax();
        if (!isInStream && capacity > (long) largest + WILDCOPY_OVERLENGTH + size + WILDCOPY_OVERLENGTH) {
            literalBuffer = at + largest + WILDCOPY_OVERLENGTH;
            literalArray = destination;
            literalBufferEnd = literalBuffer + size;
            literalLocation = IN_DESTINATION;
        } else if (size <= LITERAL_EXTRA_SIZE) {
            literalBuffer = 0;
            literalArray = literalExtra;
            literalBufferEnd = size;
            literalLocation = NOT_IN_DESTINATION;
        } else {
            literalArray = destination;
            if (isSplitAtOnce) {
                literalBuffer = at + expectedWriteSize - size + LITERAL_EXTRA_SIZE - WILDCOPY_OVERLENGTH;
                literalBufferEnd = literalBuffer + size - LITERAL_EXTRA_SIZE;
            } else {
                literalBuffer = at + expectedWriteSize - size;
                literalBufferEnd = at + expectedWriteSize;
            }
            literalLocation = SPLIT;
        }
    }

    private int decodeLiterals(byte[] destination, int at, long capacity, byte[] source, int from, int size,
                               boolean isInStream) {
        if (size < MIN_CBLOCK_SIZE) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        int first = source[from] & 0xFF;
        int encoding = first & 3;
        int largest = blockSizeMax();
        int expectedWriteSize = (int) Math.min(largest, capacity);
        int sizeFormat = (first >>> 2) & 3;
        switch (encoding) {
            case SET_REPEAT, SET_COMPRESSED -> {
                if (encoding == SET_REPEAT && !hasLiteralEntropy) {
                    throw ZstdError.DICTIONARY_CORRUPTED.failure();
                }
                if (size < 5) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                int headerOctets;
                int regenerated;
                int compressed;
                boolean isSingleStream = false;
                int packed = ZstdOctets.readInt(source, from);
                switch (sizeFormat) {
                    case 2 -> {
                        headerOctets = 4;
                        regenerated = (packed >>> 4) & 0x3FFF;
                        compressed = packed >>> 18;
                    }
                    case 3 -> {
                        headerOctets = 5;
                        regenerated = (packed >>> 4) & 0x3FFFF;
                        compressed = (packed >>> 22) + ((source[from + 4] & 0xFF) << 10);
                    }
                    default -> {
                        isSingleStream = sizeFormat == 0;
                        headerOctets = 3;
                        regenerated = (packed >>> 4) & 0x3FF;
                        compressed = (packed >>> 14) & 0x3FF;
                    }
                }
                if (regenerated > largest) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                if (!isSingleStream && regenerated < MIN_LITERALS_FOR_FOUR_STREAMS) {
                    throw ZstdError.LITERALS_HEADER_WRONG.failure();
                }
                if (compressed + headerOctets > size) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                if (expectedWriteSize < regenerated) {
                    throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                }
                allocateLiteralsBuffer(destination, at, capacity, regenerated, isInStream, expectedWriteSize, false);
                ZstdFailure huffmanFailure = null;
                try {
                    int sourceAt = from + headerOctets;
                    if (encoding == SET_REPEAT) {
                        if (isSingleStream) {
                            huffman.decompressSingleStream(literalArray, literalBuffer, regenerated, source, sourceAt,
                                    compressed);
                        } else {
                            huffman.decompressFourStreams(literalArray, literalBuffer, regenerated, source, sourceAt,
                                    compressed);
                        }
                    } else if (isSingleStream) {
                        huffman.decompressSingleStreamWithNewTable(literalArray, literalBuffer, regenerated, source,
                                sourceAt, compressed);
                    } else {
                        huffman.decompressFourStreamsWithNewTable(literalArray, literalBuffer, regenerated, source,
                                sourceAt, compressed);
                    }
                } catch (ZstdFailure failure) {
                    huffmanFailure = failure;
                }
                if (literalLocation == SPLIT) {
                    System.arraycopy(destination, literalBufferEnd - LITERAL_EXTRA_SIZE, literalExtra, 0,
                            LITERAL_EXTRA_SIZE);
                    System.arraycopy(destination, literalBuffer, destination,
                            literalBuffer + LITERAL_EXTRA_SIZE - WILDCOPY_OVERLENGTH, regenerated - LITERAL_EXTRA_SIZE);
                    literalBuffer += LITERAL_EXTRA_SIZE - WILDCOPY_OVERLENGTH;
                    literalBufferEnd -= WILDCOPY_OVERLENGTH;
                }
                if (huffmanFailure != null) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                literalPointer = literalBuffer;
                literalSize = regenerated;
                hasLiteralEntropy = true;
                return compressed + headerOctets;
            }
            case SET_BASIC -> {
                int headerOctets;
                int regenerated;
                switch (sizeFormat) {
                    case 1 -> {
                        headerOctets = 2;
                        regenerated = ZstdOctets.readShort(source, from) >>> 4;
                    }
                    case 3 -> {
                        headerOctets = 3;
                        if (size < 3) {
                            throw ZstdError.CORRUPTION_DETECTED.failure();
                        }
                        regenerated = ZstdOctets.read24(source, from) >>> 4;
                    }
                    default -> {
                        headerOctets = 1;
                        regenerated = first >>> 3;
                    }
                }
                if (regenerated > largest) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                if (expectedWriteSize < regenerated) {
                    throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                }
                allocateLiteralsBuffer(destination, at, capacity, regenerated, isInStream, expectedWriteSize, true);
                if ((long) headerOctets + regenerated + WILDCOPY_OVERLENGTH > size) {
                    if ((long) regenerated + headerOctets > size) {
                        throw ZstdError.CORRUPTION_DETECTED.failure();
                    }
                    if (literalLocation == SPLIT) {
                        System.arraycopy(source, from + headerOctets, literalArray, literalBuffer,
                                regenerated - LITERAL_EXTRA_SIZE);
                        System.arraycopy(source, from + headerOctets + regenerated - LITERAL_EXTRA_SIZE, literalExtra,
                                0, LITERAL_EXTRA_SIZE);
                    } else {
                        System.arraycopy(source, from + headerOctets, literalArray, literalBuffer, regenerated);
                    }
                    literalPointer = literalBuffer;
                    literalSize = regenerated;
                    return headerOctets + regenerated;
                }
                literalArray = source;
                literalPointer = from + headerOctets;
                literalSize = regenerated;
                literalBufferEnd = literalPointer + regenerated;
                literalLocation = NOT_IN_DESTINATION;
                return headerOctets + regenerated;
            }
            case SET_RLE -> {
                int headerOctets;
                int regenerated;
                switch (sizeFormat) {
                    case 1 -> {
                        headerOctets = 2;
                        if (size < 3) {
                            throw ZstdError.CORRUPTION_DETECTED.failure();
                        }
                        regenerated = ZstdOctets.readShort(source, from) >>> 4;
                    }
                    case 3 -> {
                        headerOctets = 3;
                        if (size < 4) {
                            throw ZstdError.CORRUPTION_DETECTED.failure();
                        }
                        regenerated = ZstdOctets.read24(source, from) >>> 4;
                    }
                    default -> {
                        headerOctets = 1;
                        regenerated = first >>> 3;
                    }
                }
                if (regenerated > largest) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                if (expectedWriteSize < regenerated) {
                    throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                }
                allocateLiteralsBuffer(destination, at, capacity, regenerated, isInStream, expectedWriteSize, true);
                byte value = source[from + headerOctets];
                if (literalLocation == SPLIT) {
                    Arrays.fill(literalArray, literalBuffer, literalBuffer + regenerated - LITERAL_EXTRA_SIZE, value);
                    Arrays.fill(literalExtra, 0, LITERAL_EXTRA_SIZE, value);
                } else {
                    Arrays.fill(literalArray, literalBuffer, literalBuffer + regenerated, value);
                }
                literalPointer = literalBuffer;
                literalSize = regenerated;
                return headerOctets + 1;
            }
            default -> throw ZstdError.CORRUPTION_DETECTED.failure();
        }
    }

    private ZstdSequenceTable buildSequenceTable(ZstdSequenceTable space, ZstdSequenceTable current, int type,
                                                 int max, int maxLog, byte[] source, int from, int size, int[] base,
                                                 int[] additional, ZstdSequenceTable defaultTable, int[] consumed) {
        switch (type) {
            case SET_RLE -> {
                if (size == 0) {
                    throw ZstdError.SRC_SIZE_WRONG.failure();
                }
                int symbol = source[from] & 0xFF;
                if (symbol > max) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                space.buildRle(base[symbol], additional[symbol]);
                consumed[0] = 1;
                return space;
            }
            case SET_BASIC -> {
                consumed[0] = 0;
                return defaultTable;
            }
            case SET_REPEAT -> {
                if (!hasSequenceEntropy) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                consumed[0] = 0;
                return current;
            }
            default -> {
                int headerOctets;
                try {
                    headerOctets = normalizedCount.read(max, source, from, size);
                } catch (ZstdFailure failure) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                if (normalizedCount.tableLog > maxLog) {
                    throw ZstdError.CORRUPTION_DETECTED.failure();
                }
                space.build(normalizedCount.counts, normalizedCount.maxSymbol, base, additional,
                        normalizedCount.tableLog);
                consumed[0] = headerOctets;
                return space;
            }
        }
    }

    private int decodeSequenceHeaders(byte[] source, int from, int size, int[] sequenceCount) {
        int end = from + size;
        int at = from;
        if (size < 1) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        int count = source[at++] & 0xFF;
        if (count > 0x7F) {
            if (count == 0xFF) {
                if (at + 2 > end) {
                    throw ZstdError.SRC_SIZE_WRONG.failure();
                }
                count = ZstdOctets.readShort(source, at) + LONG_SEQUENCE_COUNT;
                at += 2;
            } else {
                if (at >= end) {
                    throw ZstdError.SRC_SIZE_WRONG.failure();
                }
                count = ((count - 0x80) << 8) + (source[at++] & 0xFF);
            }
        }
        sequenceCount[0] = count;
        if (count == 0) {
            if (at != end) {
                throw ZstdError.CORRUPTION_DETECTED.failure();
            }
            return at - from;
        }
        if (at + 1 > end) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        int modes = source[at] & 0xFF;
        if ((modes & 3) != 0) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        at++;
        int[] consumed = new int[1];
        literalLengths = buildOrRefuse(literalLengthSpace, literalLengths, modes >>> 6, MAX_LITERAL_LENGTH_SYMBOL,
                LITERAL_LENGTH_LOG, source, at, end - at, ZstdSequenceTable.LITERAL_LENGTH_BASE,
                ZstdSequenceTable.LITERAL_LENGTH_BITS, ZstdSequenceTable.LITERAL_LENGTH_DEFAULT, consumed);
        at += consumed[0];
        offsets = buildOrRefuse(offsetSpace, offsets, (modes >>> 4) & 3, MAX_OFFSET_SYMBOL, OFFSET_LOG, source, at,
                end - at, ZstdSequenceTable.OFFSET_BASE, ZstdSequenceTable.OFFSET_BITS,
                ZstdSequenceTable.OFFSET_DEFAULT, consumed);
        at += consumed[0];
        matchLengths = buildOrRefuse(matchLengthSpace, matchLengths, (modes >>> 2) & 3, MAX_MATCH_LENGTH_SYMBOL,
                MATCH_LENGTH_LOG, source, at, end - at, ZstdSequenceTable.MATCH_LENGTH_BASE,
                ZstdSequenceTable.MATCH_LENGTH_BITS, ZstdSequenceTable.MATCH_LENGTH_DEFAULT, consumed);
        at += consumed[0];
        return at - from;
    }

    private ZstdSequenceTable buildOrRefuse(ZstdSequenceTable space, ZstdSequenceTable current, int type, int max,
                                            int maxLog, byte[] source, int from, int size, int[] base,
                                            int[] additional, ZstdSequenceTable defaultTable, int[] consumed) {
        try {
            return buildSequenceTable(space, current, type, max, maxLog, source, from, size, base, additional,
                    defaultTable, consumed);
        } catch (ZstdFailure failure) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
    }

    long decompressBlock(byte[] destination, int at, long capacity, byte[] source, int from, int size,
                         boolean isInStream) {
        if (size > blockSizeMax()) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        int literalOctets = decodeLiterals(destination, at, capacity, source, from, size, isInStream);
        int input = from + literalOctets;
        int remaining = size - literalOctets;
        long largest = Math.min(capacity, blockSizeMax());
        long totalHistory = at + largest - virtualStart;
        int[] sequenceCount = new int[1];
        int headerOctets = decodeSequenceHeaders(source, input, remaining, sequenceCount);
        input += headerOctets;
        remaining -= headerOctets;
        int count = sequenceCount[0];
        if (capacity == 0 && count > 0) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        boolean isPrefetching = false;
        if (totalHistory > PREFETCH_HISTORY && count > PREFETCH_SEQUENCES) {
            isPrefetching = longOffsetShare() >= LONG_OFFSET_SHARE_MIN;
        }
        if (isPrefetching) {
            return decompressSequencesLong(destination, at, capacity, source, input, remaining, count);
        }
        if (literalLocation == SPLIT) {
            return decompressSequencesSplitLiterals(destination, at, capacity, source, input, remaining, count);
        }
        return decompressSequences(destination, at, capacity, source, input, remaining, count);
    }

    private int longOffsetShare() {
        int tableSize = 1 << offsets.tableLog;
        int share = 0;
        for (int cell = 0; cell < tableSize; cell++) {
            if (offsets.additionalBits[cell] > LONG_OFFSET_ADDITIONAL_BITS) {
                share++;
            }
        }
        return share << (OFFSET_LOG - offsets.tableLog);
    }

    private void openSequenceStream(byte[] source, int from, int size) {
        hasSequenceEntropy = true;
        for (int each = 0; each < 3; each++) {
            previousOffsets[each] = repeats[each] & 0xFFFFFFFFL;
        }
        try {
            bits.open(source, from, size);
        } catch (ZstdFailure failure) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        literalLengthState = (int) bits.read(literalLengths.tableLog);
        bits.reload();
        offsetState = (int) bits.read(offsets.tableLog);
        bits.reload();
        matchLengthState = (int) bits.read(matchLengths.tableLog);
        bits.reload();
    }

    private void saveRepeats() {
        for (int each = 0; each < 3; each++) {
            repeats[each] = (int) previousOffsets[each];
        }
    }

    private void decodeSequence(boolean isLast) {
        int literalLengthCell = literalLengthState;
        int matchLengthCell = matchLengthState;
        int offsetCell = offsetState;
        long matchLength = matchLengths.baseValue[matchLengthCell] & 0xFFFFFFFFL;
        long literalLength = literalLengths.baseValue[literalLengthCell] & 0xFFFFFFFFL;
        long offsetBase = offsets.baseValue[offsetCell] & 0xFFFFFFFFL;
        int literalBits = literalLengths.additionalBits[literalLengthCell];
        int matchBits = matchLengths.additionalBits[matchLengthCell];
        int offsetBits = offsets.additionalBits[offsetCell];
        int totalBits = (literalBits + matchBits + offsetBits) & 0xFF;
        long offset;
        if (offsetBits > 1) {
            offset = offsetBase + bits.readFast(offsetBits);
            previousOffsets[2] = previousOffsets[1];
            previousOffsets[1] = previousOffsets[0];
            previousOffsets[0] = offset;
        } else {
            int isLiteralLengthZero = literalLengths.baseValue[literalLengthCell] == 0 ? 1 : 0;
            if (offsetBits == 0) {
                offset = previousOffsets[isLiteralLengthZero];
                previousOffsets[1] = previousOffsets[1 - isLiteralLengthZero];
                previousOffsets[0] = offset;
            } else {
                offset = offsetBase + isLiteralLengthZero + bits.readFast(1);
                long chosen = offset == 3 ? previousOffsets[0] - 1 : previousOffsets[(int) offset];
                if (chosen == 0) {
                    chosen = -1;
                }
                if (offset != 1) {
                    previousOffsets[2] = previousOffsets[1];
                }
                previousOffsets[1] = previousOffsets[0];
                previousOffsets[0] = chosen;
                offset = chosen;
            }
        }
        if (matchBits > 0) {
            matchLength += bits.readFast(matchBits);
        }
        if (totalBits >= STREAM_ACCUMULATOR_MIN - (LITERAL_LENGTH_LOG + MATCH_LENGTH_LOG + OFFSET_LOG)) {
            bits.reload();
        }
        if (literalBits > 0) {
            literalLength += bits.readFast(literalBits);
        }
        if (!isLast) {
            literalLengthState = literalLengths.nextState[literalLengthCell]
                    + (int) bits.read(literalLengths.stateBits[literalLengthCell]);
            matchLengthState = matchLengths.nextState[matchLengthCell]
                    + (int) bits.read(matchLengths.stateBits[matchLengthCell]);
            offsetState = offsets.nextState[offsetCell] + (int) bits.read(offsets.stateBits[offsetCell]);
            bits.reload();
        }
        sequenceLiteralLength = literalLength;
        sequenceMatchLength = matchLength;
        sequenceOffset = offset;
    }

    private static void copy8(byte[] destination, int to, byte[] source, int from) {
        System.arraycopy(source, from, destination, to, 8);
    }

    private static void copy16(byte[] destination, int to, byte[] source, int from) {
        System.arraycopy(source, from, destination, to, 16);
    }

    private static void wildcopy(byte[] destination, int to, byte[] source, int from, long length,
                                 boolean isOverlapping) {
        int output = to;
        int input = from;
        long end = to + length;
        long distance = destination == source ? (long) to - from : Long.MAX_VALUE;
        if (isOverlapping && distance < WILDCOPY_VECLEN) {
            do {
                copy8(destination, output, source, input);
                output += 8;
                input += 8;
            } while (output < end);
        } else {
            copy16(destination, output, source, input);
            if (16 >= length) {
                return;
            }
            output += 16;
            input += 16;
            do {
                copy16(destination, output, source, input);
                output += 16;
                input += 16;
                copy16(destination, output, source, input);
                output += 16;
                input += 16;
            } while (output < end);
        }
    }

    private static int overlapCopy8(byte[] array, int output, int[] match, long offset) {
        int input = match[0];
        if (offset < 8) {
            int subtracted = SPREAD_SUBTRACTED[(int) offset];
            array[output] = array[input];
            array[output + 1] = array[input + 1];
            array[output + 2] = array[input + 2];
            array[output + 3] = array[input + 3];
            input += SPREAD_ADDED[(int) offset];
            System.arraycopy(array, input, array, output + 4, 4);
            input -= subtracted;
        } else {
            copy8(array, output, array, input);
        }
        match[0] = input + 8;
        return output + 8;
    }

    private static void safecopy(byte[] destination, int to, int endOfWild, byte[] source, int from, long length,
                                 boolean isOverlapping) {
        int output = to;
        int input = from;
        long end = to + length;
        if (length < 8) {
            while (output < end) {
                destination[output++] = source[input++];
            }
            return;
        }
        if (isOverlapping) {
            int[] match = {input};
            output = overlapCopy8(destination, output, match, (long) output - input);
            input = match[0];
            length -= 8;
        }
        if (end <= endOfWild) {
            wildcopy(destination, output, source, input, length, isOverlapping);
            return;
        }
        if (output <= endOfWild) {
            wildcopy(destination, output, source, input, endOfWild - output, isOverlapping);
            input += endOfWild - output;
            output = endOfWild;
        }
        while (output < end) {
            destination[output++] = source[input++];
        }
    }

    private static void safecopyBeforeSource(byte[] destination, int to, byte[] source, int from, long length) {
        int output = to;
        int input = from;
        long end = to + length;
        long distance = destination == source ? (long) to - from : Long.MIN_VALUE;
        if (length < 8 || distance > -8) {
            while (output < end) {
                destination[output++] = source[input++];
            }
            return;
        }
        if (output <= end - WILDCOPY_OVERLENGTH && distance < -WILDCOPY_VECLEN) {
            int stretch = (int) (end - WILDCOPY_OVERLENGTH - output);
            wildcopy(destination, output, source, input, stretch, false);
            input += stretch;
            output += stretch;
        }
        while (output < end) {
            destination[output++] = source[input++];
        }
    }

    private long copyMatchFromHistory(byte[] destination, int literalEnd, long offset, long matchLength,
                                      int[] matchAt) {
        if (Long.compareUnsigned(offset, (long) literalEnd - prefixStart) > 0) {
            if (Long.compareUnsigned(offset, literalEnd - virtualStart) > 0) {
                throw ZstdError.CORRUPTION_DETECTED.failure();
            }
            long match = dictionaryEnd + (literalEnd - offset - prefixStart);
            if (match + matchLength <= dictionaryEnd) {
                System.arraycopy(dictionaryEndArray, (int) match, destination, literalEnd, (int) matchLength);
                return -1;
            }
            int firstPart = (int) (dictionaryEnd - match);
            System.arraycopy(dictionaryEndArray, (int) match, destination, literalEnd, firstPart);
            matchAt[0] = prefixStart;
            return matchLength - firstPart;
        }
        matchAt[0] = (int) (literalEnd - offset);
        return matchLength;
    }

    private long executeSequenceEnd(byte[] destination, int output, long end, long literalLength,
                                    long matchLength, long offset, byte[] literals, long literalLimit) {
        long sequenceLength = literalLength + matchLength;
        int literalEnd = (int) (output + literalLength);
        long endOfWild = end - WILDCOPY_OVERLENGTH;
        if (sequenceLength > end - output) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        if (literalLength > literalLimit - literalPointer) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        safecopy(destination, output, (int) endOfWild, literals, literalPointer, literalLength, false);
        literalPointer += (int) literalLength;
        int[] matchAt = new int[1];
        long remaining = copyMatchFromHistory(destination, literalEnd, offset, matchLength, matchAt);
        if (remaining < 0) {
            return sequenceLength;
        }
        int at = (int) (literalEnd + matchLength - remaining);
        safecopy(destination, at, (int) endOfWild, destination, matchAt[0], remaining, true);
        return sequenceLength;
    }

    private long executeSequenceEndOfSplit(byte[] destination, int output, long end, long endOfWild,
                                           long literalLength, long matchLength, long offset, long literalLimit) {
        long sequenceLength = literalLength + matchLength;
        int literalEnd = (int) (output + literalLength);
        if (sequenceLength > end - output) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        if (literalLength > literalLimit - literalPointer) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        if (output > literalPointer && output < literalPointer + literalLength) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        safecopyBeforeSource(destination, output, literalArray, literalPointer, literalLength);
        literalPointer += (int) literalLength;
        int[] matchAt = new int[1];
        long remaining = copyMatchFromHistory(destination, literalEnd, offset, matchLength, matchAt);
        if (remaining < 0) {
            return sequenceLength;
        }
        int at = (int) (literalEnd + matchLength - remaining);
        safecopy(destination, at, (int) endOfWild, destination, matchAt[0], remaining, true);
        return sequenceLength;
    }

    private long executeSequence(byte[] destination, int output, long end, long literalLength, long matchLength,
                                 long offset, long literalLimit) {
        long sequenceLength = literalLength + matchLength;
        long matchEnd = output + sequenceLength;
        long endOfWild = end - WILDCOPY_OVERLENGTH;
        if (literalPointer + literalLength > literalLimit || matchEnd > endOfWild) {
            return executeSequenceEnd(destination, output, end, literalLength, matchLength, offset, literalArray,
                    literalLimit);
        }
        return executeSequenceQuickly(destination, output, literalLength, matchLength, offset);
    }

    private long executeSequenceSplit(byte[] destination, int output, long end, long endOfWild, long literalLength,
                                      long matchLength, long offset, long literalLimit) {
        long sequenceLength = literalLength + matchLength;
        long matchEnd = output + sequenceLength;
        if (literalPointer + literalLength > literalLimit || matchEnd > endOfWild) {
            return executeSequenceEndOfSplit(destination, output, end, endOfWild, literalLength, matchLength, offset,
                    literalLimit);
        }
        return executeSequenceQuickly(destination, output, literalLength, matchLength, offset);
    }

    private long executeSequenceQuickly(byte[] destination, int output, long literalLength, long matchLength,
                                        long offset) {
        int literalEnd = (int) (output + literalLength);
        long sequenceLength = literalLength + matchLength;
        copy16(destination, output, literalArray, literalPointer);
        if (literalLength > 16) {
            wildcopy(destination, output + 16, literalArray, literalPointer + 16, literalLength - 16, false);
        }
        literalPointer += (int) literalLength;
        int[] matchAt = new int[1];
        long remaining = copyMatchFromHistory(destination, literalEnd, offset, matchLength, matchAt);
        if (remaining < 0) {
            return sequenceLength;
        }
        int at = (int) (literalEnd + matchLength - remaining);
        if (offset >= WILDCOPY_VECLEN) {
            wildcopy(destination, at, destination, matchAt[0], remaining, false);
            return sequenceLength;
        }
        int[] match = {matchAt[0]};
        int after = overlapCopy8(destination, at, match, offset);
        if (remaining > 8) {
            wildcopy(destination, after, destination, match[0], remaining - 8, true);
        }
        return sequenceLength;
    }

    private void requireEndOfSequences() {
        if (!bits.isEndOfStream()) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
    }

    private long decompressSequences(byte[] destination, int at, long capacity, byte[] source, int from, int size,
                                     int count) {
        long end = literalLocation == NOT_IN_DESTINATION ? at + capacity : literalBuffer;
        int output = at;
        long literalEnd = literalPointer + (long) literalSize;
        if (count > 0) {
            openSequenceStream(source, from, size);
            for (int remaining = count; remaining > 0; remaining--) {
                decodeSequence(remaining == 1);
                output += (int) executeSequence(destination, output, end, sequenceLiteralLength, sequenceMatchLength,
                        sequenceOffset, literalEnd);
            }
            requireEndOfSequences();
            saveRepeats();
        }
        long last = literalEnd - literalPointer;
        if (last > end - output) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        System.arraycopy(literalArray, literalPointer, destination, output, (int) last);
        output += (int) last;
        return output - at;
    }

    private long decompressSequencesSplitLiterals(byte[] destination, int at, long capacity, byte[] source,
                                                  int from, int size, int count) {
        long end = at + capacity;
        int output = at;
        long limit = literalBufferEnd;
        int remaining = count;
        if (remaining > 0) {
            openSequenceStream(source, from, size);
            for (; remaining > 0; remaining--) {
                decodeSequence(remaining == 1);
                if (literalPointer + sequenceLiteralLength > literalBufferEnd) {
                    break;
                }
                output += (int) executeSequenceSplit(destination, output, end,
                        literalPointer + sequenceLiteralLength - WILDCOPY_OVERLENGTH, sequenceLiteralLength,
                        sequenceMatchLength, sequenceOffset, limit);
            }
            if (remaining > 0) {
                int leftover = literalBufferEnd - literalPointer;
                if (leftover > 0) {
                    if (leftover > end - output) {
                        throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                    }
                    safecopyBeforeSource(destination, output, literalArray, literalPointer, leftover);
                    sequenceLiteralLength -= leftover;
                    output += leftover;
                }
                literalArray = literalExtra;
                literalPointer = 0;
                limit = LITERAL_EXTRA_SIZE;
                literalLocation = NOT_IN_DESTINATION;
                output += (int) executeSequence(destination, output, end, sequenceLiteralLength, sequenceMatchLength,
                        sequenceOffset, limit);
                remaining--;
            }
            for (; remaining > 0; remaining--) {
                decodeSequence(remaining == 1);
                output += (int) executeSequence(destination, output, end, sequenceLiteralLength, sequenceMatchLength,
                        sequenceOffset, limit);
            }
            requireEndOfSequences();
            saveRepeats();
        }
        if (literalLocation == SPLIT) {
            long last = limit - literalPointer;
            if (last > end - output) {
                throw ZstdError.DST_SIZE_TOO_SMALL.failure();
            }
            System.arraycopy(literalArray, literalPointer, destination, output, (int) last);
            output += (int) last;
            literalArray = literalExtra;
            literalPointer = 0;
            limit = LITERAL_EXTRA_SIZE;
            literalLocation = NOT_IN_DESTINATION;
        }
        long last = limit - literalPointer;
        if (last > end - output) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        System.arraycopy(literalArray, literalPointer, destination, output, (int) last);
        output += (int) last;
        return output - at;
    }

    private void storeSequence(int slot) {
        storedLiteralLengths[slot] = sequenceLiteralLength;
        storedMatchLengths[slot] = sequenceMatchLength;
        storedOffsets[slot] = sequenceOffset;
    }

    private long executeStored(byte[] destination, int output, long end, int slot, long[] limit) {
        if (literalLocation == SPLIT && literalPointer + storedLiteralLengths[slot] > literalBufferEnd) {
            int leftover = literalBufferEnd - literalPointer;
            int written = 0;
            if (leftover > 0) {
                if (leftover > end - output) {
                    throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                }
                safecopyBeforeSource(destination, output, literalArray, literalPointer, leftover);
                storedLiteralLengths[slot] -= leftover;
                written = leftover;
            }
            literalArray = literalExtra;
            literalPointer = 0;
            limit[0] = LITERAL_EXTRA_SIZE;
            literalLocation = NOT_IN_DESTINATION;
            return written + executeSequence(destination, output + written, end, storedLiteralLengths[slot],
                    storedMatchLengths[slot], storedOffsets[slot], limit[0]);
        }
        if (literalLocation == SPLIT) {
            return executeSequenceSplit(destination, output, end,
                    literalPointer + storedLiteralLengths[slot] - WILDCOPY_OVERLENGTH, storedLiteralLengths[slot],
                    storedMatchLengths[slot], storedOffsets[slot], limit[0]);
        }
        return executeSequence(destination, output, end, storedLiteralLengths[slot], storedMatchLengths[slot],
                storedOffsets[slot], limit[0]);
    }

    private long decompressSequencesLong(byte[] destination, int at, long capacity, byte[] source, int from,
                                         int size, int count) {
        long end = literalLocation == IN_DESTINATION ? literalBuffer : at + capacity;
        int output = at;
        long[] limit = {literalBufferEnd};
        if (count > 0) {
            int advance = Math.min(count, STORED_SEQUENCES);
            openSequenceStream(source, from, size);
            int number;
            for (number = 0; number < advance; number++) {
                decodeSequence(number == count - 1);
                storeSequence(number);
            }
            for (; number < count; number++) {
                decodeSequence(number == count - 1);
                int slot = (number - STORED_SEQUENCES) & (STORED_SEQUENCES - 1);
                output += (int) executeStored(destination, output, end, slot, limit);
                storeSequence(number & (STORED_SEQUENCES - 1));
            }
            requireEndOfSequences();
            for (number -= advance; number < count; number++) {
                output += (int) executeStored(destination, output, end, number & (STORED_SEQUENCES - 1), limit);
            }
            saveRepeats();
        }
        if (literalLocation == SPLIT) {
            long last = limit[0] - literalPointer;
            if (last > end - output) {
                throw ZstdError.DST_SIZE_TOO_SMALL.failure();
            }
            System.arraycopy(literalArray, literalPointer, destination, output, (int) last);
            output += (int) last;
            literalArray = literalExtra;
            literalPointer = 0;
            limit[0] = LITERAL_EXTRA_SIZE;
        }
        long last = limit[0] - literalPointer;
        if (last > end - output) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        System.arraycopy(literalArray, literalPointer, destination, output, (int) last);
        output += (int) last;
        return output - at;
    }
}
