package org.jebol.domain.eval.compression.brotli;

public final class BrotliStreamEncoder {

    public enum Operation { PROCESS, FLUSH, FINISH }

    private enum State { PROCESSING, FLUSH_REQUESTED, FINISHED }

    private static final int HIGHEST_QUALITY = 11;
    private static final int TWO_PASS_QUALITY = 1;
    private static final int FONT_MODE = 2;
    private static final long UNSIGNED_32 = 0xFFFFFFFFL;

    private final int quality;
    private int mode;
    private long sizeHint;
    private boolean started;
    private State state = State.PROCESSING;
    private BrotliGenericEncoder generic;
    private BrotliEncoder.FastStream fast;

    public BrotliStreamEncoder(int quality) {
        this.quality = Math.max(0, Math.min(HIGHEST_QUALITY, quality));
    }

    public boolean useSizeHint(long hint) {
        if (started) {
            return false;
        }
        sizeHint = hint & UNSIGNED_32;
        return true;
    }

    public boolean useMode(int mode) {
        if (started) {
            return false;
        }
        this.mode = mode;
        return true;
    }

    public boolean isFinished() {
        return state == State.FINISHED;
    }

    public boolean canTake(byte[] input) {
        return state != State.FINISHED || input.length == 0;
    }

    public byte[] compress(Operation operation, byte[] input) {
        if (!canTake(input)) {
            throw new IllegalStateException("a finished Brotli stream takes no more input");
        }
        startOnce();
        byte[] out;
        if (fast != null) {
            compressFast(operation, input);
            out = fast.takeCompleteBytes();
        } else {
            compressGeneric(operation, input);
            out = generic.takeCompleteBytes();
        }
        if (state == State.FLUSH_REQUESTED) {
            state = State.PROCESSING;
        }
        return out;
    }

    private void startOnce() {
        if (started) {
            return;
        }
        started = true;
        if (quality <= TWO_PASS_QUALITY) {
            fast = new BrotliEncoder.FastStream(quality);
        } else {
            generic = new BrotliGenericEncoder(quality);
            if (mode == FONT_MODE) {
                generic.encodeAsFont();
            }
            if (sizeHint != 0) {
                generic.useSizeHint(sizeHint);
            }
        }
    }

    private void compressGeneric(Operation operation, byte[] input) {
        int at = 0;
        while (true) {
            int room = generic.measureRoomLeftInBlock();
            if (room != 0 && at < input.length) {
                int howMany = Math.min(room, input.length - at);
                generic.copyInput(input, at, howMany);
                at += howMany;
                continue;
            }
            if (state == State.FLUSH_REQUESTED && generic.hasBitsWaitingForAByte()) {
                generic.padToByteWithEmptyMetadata();
                continue;
            }
            if (state == State.PROCESSING && (room == 0 || operation != Operation.PROCESS)) {
                int stillToCome = input.length - at;
                boolean isLast = stillToCome == 0 && operation == Operation.FINISH;
                boolean forceFlush = stillToCome == 0 && operation == Operation.FLUSH;
                generic.guessSizeHintFrom(stillToCome);
                generic.encodeData(isLast, forceFlush);
                if (forceFlush) {
                    state = State.FLUSH_REQUESTED;
                }
                if (isLast) {
                    state = State.FINISHED;
                }
                continue;
            }
            return;
        }
    }

    private void compressFast(Operation operation, byte[] input) {
        int at = 0;
        while (true) {
            if (state == State.FLUSH_REQUESTED && fast.hasBitsWaitingForAByte()) {
                fast.padToByteWithEmptyMetadata();
                continue;
            }
            int left = input.length - at;
            if (state == State.PROCESSING && (left != 0 || operation != Operation.PROCESS)) {
                int blockSize = Math.min(BrotliEncoder.FastStream.largestBlock(), left);
                boolean isLast = left == blockSize && operation == Operation.FINISH;
                boolean forceFlush = left == blockSize && operation == Operation.FLUSH;
                if (forceFlush && blockSize == 0) {
                    state = State.FLUSH_REQUESTED;
                    continue;
                }
                fast.compressBlock(input, at, blockSize, isLast);
                at += blockSize;
                if (forceFlush) {
                    state = State.FLUSH_REQUESTED;
                }
                if (isLast) {
                    state = State.FINISHED;
                }
                continue;
            }
            return;
        }
    }
}
