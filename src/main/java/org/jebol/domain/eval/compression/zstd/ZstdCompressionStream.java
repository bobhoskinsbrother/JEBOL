package org.jebol.domain.eval.compression.zstd;

public final class ZstdCompressionStream {

    private static final int STAGE_INIT = 0;
    private static final int STAGE_LOAD = 1;
    private static final int STAGE_FLUSH = 2;
    private static final int CONTINUE = 0;
    private static final int FLUSH = 1;
    private static final int END = 2;

    private final ZstdCompressionContext context = new ZstdCompressionContext();
    private final int level;
    private int streamStage = STAGE_INIT;
    private int inToCompress;
    private int inBufferPosition;
    private int inBufferTarget;
    private int outBufferContentSize;
    private int outBufferFlushedSize;
    private boolean isFrameEnded;

    public ZstdCompressionStream(int level) {
        this.level = level;
    }

    public static int clampLevel(int level) {
        return Math.max(ZstdCompressionParameters.MIN_LEVEL, Math.min(ZstdCompressionParameters.MAX_LEVEL, level));
    }

    public long compress(ZstdStreamBuffer output, ZstdStreamBuffer input) {
        compressStream(output, input, CONTINUE);
        int hint = inBufferTarget - inBufferPosition;
        return hint == 0 ? context.blockSizeMax : hint;
    }

    public long flush(ZstdStreamBuffer output) {
        return compressStream(output, new ZstdStreamBuffer(new byte[0], 0, 0), FLUSH);
    }

    public long end(ZstdStreamBuffer output) {
        long remaining = compressStream(output, new ZstdStreamBuffer(new byte[0], 0, 0), END);
        int lastBlockSize = isFrameEnded ? 0 : ZstdCompressionContext.BLOCK_HEADER_SIZE;
        int checksumSize = isFrameEnded ? 0 : (context.isChecksumWritten ? 4 : 0);
        return remaining + lastBlockSize + checksumSize;
    }

    private void resetSession() {
        streamStage = STAGE_INIT;
        context.pledgedSourceSizePlusOne = 0;
    }

    private void initialize(int endDirective, long inputSize) {
        if (endDirective == END) {
            context.pledgedSourceSizePlusOne = inputSize + 1;
        }
        long pledged = context.pledgedSourceSizePlusOne - 1;
        ZstdCompressionParameters parameters = ZstdCompressionParameters.forLevel(level, pledged);
        parameters.adjust(pledged, true);
        context.applyParameters(parameters, level);
        context.reset(parameters, pledged, true);
        inToCompress = 0;
        inBufferPosition = 0;
        inBufferTarget = context.blockSizeMax + (context.blockSizeMax == pledged ? 1 : 0);
        outBufferContentSize = 0;
        outBufferFlushedSize = 0;
        streamStage = STAGE_LOAD;
        isFrameEnded = false;
    }

    private long compressStream(ZstdStreamBuffer output, ZstdStreamBuffer input, int endDirective) {
        if (output.position > output.size) {
            throw ZstdError.DST_SIZE_TOO_SMALL.failure();
        }
        if (input.position > input.size) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        if (streamStage == STAGE_INIT) {
            initialize(endDirective, input.size - input.position);
        }
        compressGeneric(output, input, endDirective);
        return outBufferContentSize - outBufferFlushedSize;
    }

    private void compressGeneric(ZstdStreamBuffer output, ZstdStreamBuffer input, int endDirective) {
        byte[] source = input.octets;
        int inputEnd = input.size;
        int ip = input.position;
        byte[] destination = output.octets;
        int outputEnd = output.size;
        int op = output.position;
        boolean isWorking = true;
        while (isWorking) {
            if (streamStage == STAGE_INIT) {
                throw ZstdError.STAGE_WRONG.failure();
            }
            if (streamStage == STAGE_LOAD) {
                if (endDirective == END
                        && outputEnd - op >= ZstdCompressionContext.compressBound(inputEnd - ip)
                        && inBufferPosition == 0) {
                    int compressed = context.compressEnd(destination, op, outputEnd - op, source, ip, inputEnd - ip);
                    ip = inputEnd;
                    op += compressed;
                    isFrameEnded = true;
                    resetSession();
                    break;
                }
                int toLoad = inBufferTarget - inBufferPosition;
                int loaded = Math.min(toLoad, inputEnd - ip);
                if (loaded > 0) {
                    System.arraycopy(source, ip, context.inBuffer, inBufferPosition, loaded);
                }
                inBufferPosition += loaded;
                ip += loaded;
                if (endDirective == CONTINUE && inBufferPosition < inBufferTarget) {
                    break;
                }
                if (endDirective == FLUSH && inBufferPosition == inToCompress) {
                    break;
                }
                int room = outputEnd - op;
                int toCompress = inBufferPosition - inToCompress;
                byte[] compressDestination;
                int compressAt;
                if (room >= ZstdCompressionContext.compressBound(toCompress)) {
                    compressDestination = destination;
                    compressAt = op;
                } else {
                    compressDestination = context.outBuffer;
                    compressAt = 0;
                    room = context.outBuffer.length;
                }
                boolean isLastBlock = endDirective == END && ip == inputEnd;
                int compressed = isLastBlock
                        ? context.compressEnd(compressDestination, compressAt, room, context.inBuffer, inToCompress,
                        toCompress)
                        : context.compressContinue(compressDestination, compressAt, room, context.inBuffer,
                        inToCompress, toCompress, false);
                isFrameEnded = isLastBlock;
                inBufferTarget = inBufferPosition + context.blockSizeMax;
                if (inBufferTarget > context.inBuffer.length) {
                    inBufferPosition = 0;
                    inBufferTarget = context.blockSizeMax;
                }
                inToCompress = inBufferPosition;
                if (compressDestination == destination) {
                    op += compressed;
                    if (isFrameEnded) {
                        isWorking = false;
                        resetSession();
                    }
                    continue;
                }
                outBufferContentSize = compressed;
                outBufferFlushedSize = 0;
                streamStage = STAGE_FLUSH;
            }
            int toFlush = outBufferContentSize - outBufferFlushedSize;
            int flushed = Math.min(outputEnd - op, toFlush);
            if (flushed > 0) {
                System.arraycopy(context.outBuffer, outBufferFlushedSize, destination, op, flushed);
            }
            op += flushed;
            outBufferFlushedSize += flushed;
            if (toFlush != flushed) {
                break;
            }
            outBufferContentSize = 0;
            outBufferFlushedSize = 0;
            if (isFrameEnded) {
                resetSession();
                break;
            }
            streamStage = STAGE_LOAD;
        }
        input.position = ip;
        output.position = op;
    }
}
