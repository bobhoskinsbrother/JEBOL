package org.jebol.application;

import org.jebol.domain.eval.compression.ExpandingBinary;
import org.jebol.domain.eval.compression.SeriesRoom;
import org.jebol.domain.eval.compression.zstd.ZstdCompressionStream;
import org.jebol.domain.eval.compression.zstd.ZstdDecompressionStream;
import org.jebol.domain.eval.compression.zstd.ZstdFailure;
import org.jebol.domain.eval.compression.zstd.ZstdStreamBuffer;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.Optional;

final class ZstdStream {

    static final String NO_COMPRESS = "Failed to compress using the Zstd encoder!";
    static final String NO_DECOMPRESS = "Failed to decompress using the Zstd decoder!";

    private final Optional<ZstdCompressionStream> encoder;
    private final Optional<ZstdDecompressionStream> decoder;
    private ExpandingBinary buffer;

    private ZstdStream(Optional<ZstdCompressionStream> encoder, Optional<ZstdDecompressionStream> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    static ZstdStream encoding(ZstdCompressionStream encoder) {
        return new ZstdStream(Optional.of(encoder), Optional.empty());
    }

    static ZstdStream decoding(ZstdDecompressionStream decoder) {
        return new ZstdStream(Optional.empty(), Optional.of(decoder));
    }

    private ZstdStreamBuffer outputAtTail(long size) {
        return new ZstdStreamBuffer(buffer.octets(), (int) size, buffer.tail());
    }

    Value write(Value handle, Optional<byte[]> data, boolean isFlushed, boolean isFinishing) {
        boolean isFinished = isFinishing;
        ZstdStreamBuffer input = new ZstdStreamBuffer(new byte[0], 0, 0);
        ZstdStreamBuffer output;
        if (data.isEmpty()) {
            if (buffer == null) {
                return NoneValue.none();
            }
            output = outputAtTail(buffer.rest());
            isFinished = true;
        } else {
            byte[] octets = data.get();
            input = new ZstdStreamBuffer(octets, octets.length, 0);
            if (buffer == null) {
                buffer = new ExpandingBinary(SeriesRoom.makeBinaryOf(octets.length));
            }
            int tail = buffer.tail();
            if (buffer.rest() - tail < octets.length) {
                buffer.expandAtTail(buffer.rest());
                buffer.setTail(tail);
            }
            output = outputAtTail(buffer.rest());
        }
        if (encoder.isPresent()) {
            output = encode(encoder.get(), input, output, isFlushed, isFinished);
        } else if (input.size > 0) {
            decode(decoder.orElseThrow(), input, output);
        }
        if (isFlushed || isFinished) {
            return BinaryValue.ofBytes(takeBuffered());
        }
        return handle;
    }

    private ZstdStreamBuffer encode(ZstdCompressionStream stream, ZstdStreamBuffer input, ZstdStreamBuffer start,
                                    boolean isFlushed, boolean isFinished) {
        ZstdStreamBuffer output = start;
        try {
            while (input.position < input.size) {
                int read = input.position;
                int written = output.position;
                stream.compress(output, input);
                buffer.setTail(output.position);
                if (input.position == read && output.position == written) {
                    throw new IllegalStateException(NO_COMPRESS);
                }
            }
            if (isFlushed) {
                output = drain(stream, output, false);
            }
            if (isFinished) {
                output = drain(stream, output, true);
            }
        } catch (ZstdFailure failure) {
            throw new IllegalStateException(NO_COMPRESS);
        }
        return output;
    }

    private ZstdStreamBuffer drain(ZstdCompressionStream stream, ZstdStreamBuffer start, boolean isEnding) {
        ZstdStreamBuffer output = start;
        for (;;) {
            long result = isEnding ? stream.end(output) : stream.flush(output);
            buffer.setTail(output.position);
            if (result == 0) {
                return output;
            }
            buffer.expandAtTail(result);
            buffer.setTail(output.position);
            output = new ZstdStreamBuffer(buffer.octets(), (int) buffer.rest(), output.position);
        }
    }

    private void decode(ZstdDecompressionStream stream, ZstdStreamBuffer input, ZstdStreamBuffer start) {
        ZstdStreamBuffer output = start;
        for (;;) {
            try {
                stream.decompress(output, input);
            } catch (ZstdFailure failure) {
                throw new IllegalStateException(NO_DECOMPRESS);
            }
            buffer.setTail(output.position);
            if (input.position == input.size) {
                return;
            }
            if (output.position == output.size) {
                buffer.expandAtTail(output.position);
                buffer.setTail(output.position);
                output = new ZstdStreamBuffer(buffer.octets(), (int) buffer.available(), output.position);
            }
        }
    }

    Value read() {
        if (buffer == null) {
            return NoneValue.none();
        }
        if (encoder.isPresent()) {
            ZstdStreamBuffer output = outputAtTail(buffer.rest());
            try {
                drain(encoder.get(), output, false);
            } catch (ZstdFailure failure) {
                throw new IllegalStateException(NO_COMPRESS);
            }
        }
        return BinaryValue.ofBytes(takeBuffered());
    }

    private byte[] takeBuffered() {
        byte[] output = Arrays.copyOf(buffer.octets(), buffer.tail());
        buffer.setTail(0);
        return output;
    }
}
