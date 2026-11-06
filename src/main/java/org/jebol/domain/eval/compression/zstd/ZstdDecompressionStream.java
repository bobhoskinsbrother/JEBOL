package org.jebol.domain.eval.compression.zstd;

public final class ZstdDecompressionStream {

    private static final int STAGE_INIT = 0;
    private static final int STAGE_LOAD_HEADER = 1;
    private static final int STAGE_READ = 2;
    private static final int STAGE_LOAD = 3;
    private static final int STAGE_FLUSH = 4;

    private static final long MAX_WINDOW_SIZE = (1L << 27) + 1;
    private static final int WINDOW_LOG_ABSOLUTE_MIN = 10;
    private static final int FRAME_HEADER_SIZE_MIN = 6;
    private static final int NO_FORWARD_PROGRESS_MAX = 16;
    private static final int WORKSPACE_TOO_LARGE_FACTOR = 3;
    private static final int WORKSPACE_TOO_LARGE_DURATION = 128;
    private static final int CHECKSUM_SIZE = 4;

    private final ZstdDecompressionContext context = new ZstdDecompressionContext();
    private int streamStage = STAGE_INIT;
    private int headerLoaded;
    private long inPosition;
    private int outStart;
    private int outEnd;
    private boolean isHoldingHostage;
    private int noForwardProgress;
    private int oversizedDuration;
    private byte[] inBuffer = new byte[0];
    private byte[] outBuffer = new byte[0];

    public void reset() {
        streamStage = STAGE_INIT;
        noForwardProgress = 0;
    }

    public long decompress(ZstdStreamBuffer output, ZstdStreamBuffer input) {
        if (input.position > input.size) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        if (output.position > output.size) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        byte[] source = input.octets;
        int inputStart = input.position;
        int inputEnd = input.size;
        int read = inputStart;
        byte[] destination = output.octets;
        int outputStart = output.position;
        int outputEnd = output.size;
        int[] written = {outputStart};
        boolean isWorking = true;
        while (isWorking) {
            switch (streamStage) {
                case STAGE_INIT -> {
                    streamStage = STAGE_LOAD_HEADER;
                    headerLoaded = 0;
                    inPosition = 0;
                    outStart = 0;
                    outEnd = 0;
                    isHoldingHostage = false;
                }
                case STAGE_LOAD_HEADER -> {
                    long needed = context.frame.read(context.headerBuffer, 0, headerLoaded);
                    if (needed != 0) {
                        int toLoad = (int) needed - headerLoaded;
                        int remainingInput = inputEnd - read;
                        if (toLoad > remainingInput) {
                            if (remainingInput > 0) {
                                System.arraycopy(source, read, context.headerBuffer, headerLoaded, remainingInput);
                                headerLoaded += remainingInput;
                            }
                            input.position = input.size;
                            context.frame.read(context.headerBuffer, 0, headerLoaded);
                            return Math.max(FRAME_HEADER_SIZE_MIN, needed) - headerLoaded
                                    + ZstdDecompressionContext.BLOCK_HEADER_SIZE;
                        }
                        System.arraycopy(source, read, context.headerBuffer, headerLoaded, toLoad);
                        headerLoaded = (int) needed;
                        read += toLoad;
                        continue;
                    }
                    long consumed = decodeInOnePass(destination, written, outputEnd, source, inputStart, inputEnd);
                    if (consumed >= 0) {
                        read = inputStart + (int) consumed;
                        context.expected = 0;
                        streamStage = STAGE_INIT;
                        isWorking = false;
                        continue;
                    }
                    beginFrame();
                    streamStage = STAGE_READ;
                }
                case STAGE_READ -> {
                    long needed = context.nextSourceSizeWithInput(inputEnd - read);
                    if (needed == 0) {
                        streamStage = STAGE_INIT;
                        isWorking = false;
                        continue;
                    }
                    if (inputEnd - read >= needed) {
                        decompressContinueStream(source, read, needed);
                        read += (int) needed;
                        continue;
                    }
                    if (read == inputEnd) {
                        isWorking = false;
                        continue;
                    }
                    streamStage = STAGE_LOAD;
                }
                case STAGE_LOAD -> {
                    long needed = context.expected;
                    long toLoad = needed - inPosition;
                    long loaded;
                    if (context.isSkippingFrame()) {
                        loaded = Math.min(toLoad, inputEnd - read);
                    } else {
                        if (toLoad > inBuffer.length - inPosition) {
                            throw ZstdError.CORRUPTION_DETECTED.failure();
                        }
                        loaded = Math.min(toLoad, inputEnd - read);
                        System.arraycopy(source, read, inBuffer, (int) inPosition, (int) loaded);
                    }
                    if (loaded != 0) {
                        read += (int) loaded;
                        inPosition += loaded;
                    }
                    if (loaded < toLoad) {
                        isWorking = false;
                        continue;
                    }
                    inPosition = 0;
                    decompressContinueStream(inBuffer, 0, needed);
                }
                case STAGE_FLUSH -> {
                    int toFlush = outEnd - outStart;
                    int flushed = Math.min(outputEnd - written[0], toFlush);
                    if (flushed > 0) {
                        System.arraycopy(outBuffer, outStart, destination, written[0], flushed);
                    }
                    written[0] += flushed;
                    outStart += flushed;
                    if (flushed == toFlush) {
                        streamStage = STAGE_READ;
                        if (Long.compareUnsigned(outBuffer.length, context.frame.frameContentSize) < 0
                                && (long) outStart + context.frame.blockSizeMax > outBuffer.length) {
                            outStart = 0;
                            outEnd = 0;
                        }
                        continue;
                    }
                    isWorking = false;
                }
                default -> throw ZstdError.GENERIC.failure();
            }
        }
        input.position = read;
        output.position = written[0];
        if (read == inputStart && written[0] == outputStart) {
            noForwardProgress++;
            if (noForwardProgress >= NO_FORWARD_PROGRESS_MAX) {
                if (written[0] == outputEnd) {
                    throw ZstdError.NO_FORWARD_PROGRESS_DEST_FULL.failure();
                }
                if (read == inputEnd) {
                    throw ZstdError.NO_FORWARD_PROGRESS_INPUT_EMPTY.failure();
                }
            }
        } else {
            noForwardProgress = 0;
        }
        long hint = context.expected;
        if (hint == 0) {
            if (outEnd == outStart) {
                if (isHoldingHostage) {
                    if (input.position >= input.size) {
                        streamStage = STAGE_READ;
                        return 1;
                    }
                    input.position++;
                }
                return 0;
            }
            if (!isHoldingHostage) {
                input.position--;
                isHoldingHostage = true;
            }
            return 1;
        }
        if (context.stage == ZstdDecompressionContext.STAGE_DECOMPRESS_BLOCK) {
            hint += ZstdDecompressionContext.BLOCK_HEADER_SIZE;
        }
        return hint - inPosition;
    }

    private long decodeInOnePass(byte[] destination, int[] written, int outputEnd, byte[] source, int inputStart,
                                 int inputEnd) {
        ZstdFrameHeader frame = context.frame;
        if (frame.frameContentSize == ZstdFrameHeader.CONTENT_SIZE_UNKNOWN || frame.isSkippable
                || Long.compareUnsigned(outputEnd - written[0], frame.frameContentSize) < 0) {
            return -1;
        }
        long compressedSize;
        try {
            compressedSize = ZstdDecompressionContext.findFrameCompressedSize(source, inputStart,
                    inputEnd - inputStart);
        } catch (ZstdFailure notOneFrame) {
            return -1;
        }
        if (compressedSize > inputEnd - inputStart) {
            return -1;
        }
        long produced = context.decompressFrames(destination, written[0], outputEnd - written[0], source,
                inputStart, (int) compressedSize);
        written[0] += (int) produced;
        return compressedSize;
    }

    private void beginFrame() {
        context.begin();
        ZstdFrameHeader frame = context.frame;
        if (ZstdFrameHeader.isSkippableMagic(ZstdOctets.readInt(context.headerBuffer, 0))) {
            context.expected = ZstdOctets.readInt(context.headerBuffer, ZstdFrameHeader.FRAME_ID_SIZE) & 0xFFFFFFFFL;
            context.stage = ZstdDecompressionContext.STAGE_SKIP_FRAME;
        } else {
            context.decodeFrameHeader(context.headerBuffer, 0, headerLoaded);
            context.expected = ZstdDecompressionContext.BLOCK_HEADER_SIZE;
            context.stage = ZstdDecompressionContext.STAGE_DECODE_BLOCK_HEADER;
        }
        frame.windowSize = Math.max(frame.windowSize, 1L << WINDOW_LOG_ABSOLUTE_MIN);
        if (frame.windowSize > MAX_WINDOW_SIZE) {
            throw ZstdError.FRAME_PARAMETER_WINDOW_TOO_LARGE.failure();
        }
        int neededIn = Math.max(frame.blockSizeMax, CHECKSUM_SIZE);
        int neededOut = decodingBufferSize(frame.windowSize, frame.frameContentSize, frame.blockSizeMax);
        if ((long) inBuffer.length + outBuffer.length
                >= ((long) neededIn + neededOut) * WORKSPACE_TOO_LARGE_FACTOR) {
            oversizedDuration++;
        } else {
            oversizedDuration = 0;
        }
        boolean isTooSmall = inBuffer.length < neededIn || outBuffer.length < neededOut;
        boolean isTooLarge = oversizedDuration >= WORKSPACE_TOO_LARGE_DURATION;
        if (isTooSmall || isTooLarge) {
            inBuffer = new byte[neededIn];
            outBuffer = new byte[neededOut];
        }
    }

    private static int decodingBufferSize(long windowSize, long frameContentSize, int blockSizeMax) {
        long blockSize = Math.min(Math.min(windowSize, ZstdFrameHeader.BLOCK_SIZE_MAX), blockSizeMax);
        long ringSize = windowSize + blockSize * 2 + ZstdDecompressionContext.WILDCOPY_OVERLENGTH * 2L;
        return (int) (Long.compareUnsigned(frameContentSize, ringSize) < 0 ? frameContentSize : ringSize);
    }

    private void decompressContinueStream(byte[] source, int from, long size) {
        boolean isSkipping = context.isSkippingFrame();
        int room = isSkipping ? 0 : outBuffer.length - outStart;
        long decoded = context.decompressContinue(outBuffer, outStart, room, source, from, size);
        if (decoded == 0 && !isSkipping) {
            streamStage = STAGE_READ;
        } else {
            outEnd = outStart + (int) decoded;
            streamStage = STAGE_FLUSH;
        }
    }
}
