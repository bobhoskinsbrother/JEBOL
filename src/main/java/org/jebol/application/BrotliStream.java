package org.jebol.application;

import org.jebol.domain.eval.compression.brotli.BrotliStreamDecoder;
import org.jebol.domain.eval.compression.brotli.BrotliStreamEncoder;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.HandleFields;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MoldedInsideHandle;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.Optional;

final class BrotliStream implements HandleFields, MoldedInsideHandle {

    static final String ENCODER_FINISHED = "Brotli encoder is finished!";

    private static final String FINISHED = "finished";
    private static final String MODE = "mode";
    private static final String SIZE_HINT = "size-hint";

    private final Optional<BrotliStreamEncoder> encoder;
    private final Optional<BrotliStreamDecoder> decoder;
    private byte[] pending;
    private int pendingLength;

    private BrotliStream(Optional<BrotliStreamEncoder> encoder, Optional<BrotliStreamDecoder> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    static BrotliStream encoding(BrotliStreamEncoder encoder) {
        return new BrotliStream(Optional.of(encoder), Optional.empty());
    }

    static BrotliStream decoding(BrotliStreamDecoder decoder) {
        return new BrotliStream(Optional.empty(), Optional.of(decoder));
    }

    Value write(Value handle, Optional<byte[]> data, boolean isFlushed, boolean isFinishing) {
        if (data.isEmpty() && pending == null) {
            return NoneValue.none();
        }
        if (pending == null) {
            pending = new byte[data.get().length];
        }
        byte[] input = data.orElse(new byte[0]);
        boolean isFinished = isFinishing || data.isEmpty();
        if (encoder.isPresent()) {
            refuseFinishedEncoder();
            BrotliStreamEncoder.Operation operation = isFinished ? BrotliStreamEncoder.Operation.FINISH
                    : isFlushed ? BrotliStreamEncoder.Operation.FLUSH
                    : BrotliStreamEncoder.Operation.PROCESS;
            keep(encoder.get().compress(operation, input));
        } else {
            keepDecodedIgnoringDamage(decoder.orElseThrow(), input);
        }
        if (isFlushed || isFinished) {
            return BinaryValue.ofBytes(takePending());
        }
        return handle;
    }

    Value read() {
        if (pending == null) {
            return NoneValue.none();
        }
        if (encoder.isPresent()) {
            refuseFinishedEncoder();
            keep(encoder.get().compress(BrotliStreamEncoder.Operation.FLUSH, new byte[0]));
        }
        return BinaryValue.ofBytes(takePending());
    }

    private void refuseFinishedEncoder() {
        if (encoder.orElseThrow().isFinished()) {
            throw new IllegalStateException(ENCODER_FINISHED);
        }
    }

    private void keepDecodedIgnoringDamage(BrotliStreamDecoder stream, byte[] input) {
        keep(decodeOrNothing(stream, input));
    }

    private static byte[] decodeOrNothing(BrotliStreamDecoder stream, byte[] input) {
        try {
            return stream.decompress(input);
        } catch (IllegalArgumentException damaged) {
            return new byte[0];
        }
    }

    private void keep(byte[] octets) {
        if (pendingLength + octets.length > pending.length) {
            pending = Arrays.copyOf(pending, Math.max(pendingLength + octets.length, 2 * pending.length));
        }
        System.arraycopy(octets, 0, pending, pendingLength, octets.length);
        pendingLength += octets.length;
    }

    private byte[] takePending() {
        byte[] taken = Arrays.copyOf(pending, pendingLength);
        pendingLength = 0;
        return taken;
    }

    private boolean isFinished() {
        return encoder.map(BrotliStreamEncoder::isFinished)
                .orElseGet(() -> decoder.orElseThrow().isFinished());
    }

    @Override
    public Optional<Value> readField(String field) {
        return FINISHED.equals(field) ? Optional.of(LogicValue.of(isFinished())) : Optional.empty();
    }

    @Override
    public FieldWriting writeField(String field, Value written) {
        if (!MODE.equals(field) && !SIZE_HINT.equals(field)) {
            return FieldWriting.NO_SUCH_FIELD;
        }
        if (!(written instanceof IntegerValue(long magnitude))) {
            return FieldWriting.WRONG_TYPE;
        }
        encoder.ifPresent(stream -> {
            if (MODE.equals(field)) {
                stream.useMode((int) magnitude);
            } else {
                stream.useSizeHint(magnitude);
            }
        });
        return FieldWriting.WRITTEN;
    }

    @Override
    public String moldInsideHandle() {
        return "0#" + Integer.toHexString(System.identityHashCode(this));
    }
}
