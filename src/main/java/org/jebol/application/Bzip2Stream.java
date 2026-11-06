package org.jebol.application;

import org.jebol.domain.eval.compression.ExpandingBinary;
import org.jebol.domain.eval.compression.SeriesRoom;
import org.jebol.domain.eval.compression.bzip2.Bzip2Buffers;
import org.jebol.domain.eval.compression.bzip2.Bzip2Codes;
import org.jebol.domain.eval.compression.bzip2.Bzip2CompressionStream;
import org.jebol.domain.eval.compression.bzip2.Bzip2DecompressionStream;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.MoldedInsideHandle;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.Optional;

final class Bzip2Stream implements MoldedInsideHandle {

    static final String NO_COMPRESS = "Bzip2 compression failed.";
    static final String NO_DECOMPRESS = "Bzip2 decompression failed.";
    private static final String STREAM_CLOSED = "Bzip2 stream is already finished.";
    private static final int FIRST_BUFFER = 65536;
    private static final int LEAST_ROOM = 4096;
    private static final int ROOM_BEYOND_INPUT = 256;
    private static final int MOST_READ_FLUSHES = 1000;

    private final Optional<Bzip2CompressionStream> encoder;
    private final Optional<Bzip2DecompressionStream> decoder;
    private final Bzip2Buffers io = new Bzip2Buffers();
    private ExpandingBinary buffer;
    private boolean isClosed;

    private Bzip2Stream(Optional<Bzip2CompressionStream> encoder, Optional<Bzip2DecompressionStream> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    static Bzip2Stream encoding(int blockSize) {
        return new Bzip2Stream(Optional.of(new Bzip2CompressionStream(blockSize)), Optional.empty());
    }

    static Bzip2Stream decoding() {
        return new Bzip2Stream(Optional.empty(), Optional.of(new Bzip2DecompressionStream()));
    }

    @Override
    public String moldInsideHandle() {
        return "bzip2:" + (encoder.isPresent() ? "enc" : "dec") + ":0x" + Integer.toHexString(System.identityHashCode(this));
    }

    private void ensureRoom(int leastFree) {
        int tail = buffer.tail();
        while (buffer.rest() - tail < leastFree) {
            buffer.expandAtTail(buffer.rest());
            buffer.setTail(tail);
        }
    }

    private void pointOutputAtTail() {
        ensureRoom(LEAST_ROOM);
        io.output = buffer.octets();
        io.nextOut = buffer.tail();
        io.availableOut = (int) (buffer.rest() - buffer.tail());
    }

    private void takeInput(byte[] octets) {
        io.input = octets;
        io.nextIn = 0;
        io.availableIn = octets.length;
        if (buffer == null) {
            buffer = new ExpandingBinary(SeriesRoom.makeBinaryOf(Math.max(FIRST_BUFFER, octets.length * 2 + ROOM_BEYOND_INPUT)));
        }
        ensureRoom(Math.max(LEAST_ROOM, octets.length + ROOM_BEYOND_INPUT));
    }

    Value write(Value handle, Optional<byte[]> data, boolean isFlushed, boolean isFinishing) {
        if (isClosed) {
            throw new IllegalStateException(STREAM_CLOSED);
        }
        boolean isFinished = isFinishing;
        if (data.isEmpty()) {
            if (buffer == null || decoder.isPresent()) {
                return NoneValue.none();
            }
            isFinished = true;
        } else {
            takeInput(data.get());
        }
        if (encoder.isPresent()) {
            encode(encoder.get(), data.isPresent(), isFlushed, isFinished);
        } else if (io.availableIn > 0) {
            decode(decoder.orElseThrow());
        }
        return isFlushed || isFinished ? BinaryValue.ofBytes(takeBuffered()) : handle;
    }

    private int compressAtTail(Bzip2CompressionStream stream, int action) {
        pointOutputAtTail();
        int answer = stream.compress(io, action);
        buffer.setTail(io.nextOut);
        return answer;
    }

    private void encode(Bzip2CompressionStream stream, boolean hasData, boolean isFlushed, boolean isFinished) {
        while (hasData && io.availableIn > 0) {
            int answer = compressAtTail(stream, Bzip2Codes.RUN);
            if (answer != Bzip2Codes.OK && answer != Bzip2Codes.RUN_OK) {
                throw new IllegalStateException(NO_COMPRESS);
            }
        }
        if (isFlushed && compressAtTail(stream, Bzip2Codes.FLUSH) != Bzip2Codes.FLUSH_OK) {
            throw new IllegalStateException(NO_COMPRESS);
        }
        if (isFinished) {
            finish(stream);
        }
    }

    private void finish(Bzip2CompressionStream stream) {
        for (;;) {
            int answer = compressAtTail(stream, Bzip2Codes.FINISH);
            if (answer == Bzip2Codes.STREAM_END) {
                isClosed = true;
                return;
            }
            if (answer != Bzip2Codes.FINISH_OK && answer != Bzip2Codes.OK && answer != Bzip2Codes.RUN_OK) {
                throw new IllegalStateException(NO_COMPRESS);
            }
        }
    }

    private void decode(Bzip2DecompressionStream stream) {
        for (;;) {
            pointOutputAtTail();
            int answer = stream.decompress(io);
            buffer.setTail(io.nextOut);
            if (answer == Bzip2Codes.STREAM_END) {
                isClosed = true;
                return;
            }
            if (answer != Bzip2Codes.OK) {
                throw new IllegalStateException(NO_DECOMPRESS);
            }
            if (io.availableIn == 0) {
                return;
            }
        }
    }

    Value read() {
        if (buffer == null) {
            return NoneValue.none();
        }
        if (encoder.isPresent() && !isClosed) {
            flushForReading(encoder.get());
        }
        return BinaryValue.ofBytes(takeBuffered());
    }

    private void flushForReading(Bzip2CompressionStream stream) {
        for (int flush = 0; flush < MOST_READ_FLUSHES; flush++) {
            ensureRoom(LEAST_ROOM);
            int tail = buffer.tail();
            int answer = compressAtTail(stream, Bzip2Codes.FLUSH);
            if (answer == Bzip2Codes.FLUSH_OK || (buffer.tail() == tail && answer == Bzip2Codes.RUN_OK)) {
                return;
            }
            if (answer < 0) {
                throw new IllegalStateException(NO_COMPRESS);
            }
        }
    }

    private byte[] takeBuffered() {
        byte[] output = Arrays.copyOf(buffer.octets(), buffer.tail());
        buffer.setTail(0);
        return output;
    }
}
