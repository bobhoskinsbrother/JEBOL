package org.jebol.application;

import org.jebol.domain.eval.compression.ExpandingBinary;
import org.jebol.domain.eval.compression.SeriesRoom;
import org.jebol.domain.eval.compression.zlibng.ZlibNgDeflater;
import org.jebol.domain.eval.compression.zlibng.ZlibNgInflater;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.Optional;

final class ZlibNgStream {

    static final String NO_COMPRESS = "Failed to compress using the Zlib encoder!";
    static final String NO_DECOMPRESS = "Failed to decompress using the Zlib decoder!";

    private static final long UNSIGNED_32 = 0xFFFFFFFFL;
    private static final byte[] NOTHING = new byte[0];

    private final Optional<ZlibNgDeflater> encoder;
    private final Optional<ZlibNgInflater> decoder;
    private ExpandingBinary buffer;

    private ZlibNgStream(Optional<ZlibNgDeflater> encoder, Optional<ZlibNgInflater> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    static ZlibNgStream encoding(ZlibNgDeflater encoder) {
        return new ZlibNgStream(Optional.of(encoder), Optional.empty());
    }

    static ZlibNgStream decoding(ZlibNgInflater decoder) {
        return new ZlibNgStream(Optional.empty(), Optional.of(decoder));
    }

    Value write(Value handle, Optional<byte[]> data, boolean isFlushed, boolean isFinished) {
        boolean isFinishing = isFinished;
        int flush = isFinishing ? ZlibNgDeflater.Z_FINISH : (isFlushed ? ZlibNgDeflater.Z_SYNC_FLUSH : ZlibNgDeflater.Z_NO_FLUSH);
        int availableIn = 0;
        if (data.isEmpty()) {
            if (buffer == null) {
                return NoneValue.none();
            }
            isFinishing = true;
            flush = ZlibNgDeflater.Z_FINISH;
            giveInput(NOTHING);
        } else {
            byte[] octets = data.get();
            availableIn = octets.length;
            giveInput(octets);
            if (buffer == null) {
                long size = encoder.map(stream -> stream.bound(octets.length)).orElse((long) octets.length);
                buffer = new ExpandingBinary(SeriesRoom.makeBinaryOf(size));
            }
        }
        giveRoom(buffer.tail());
        for (;;) {
            int result = run(flush);
            if (result < 0) {
                throw new IllegalStateException(NO_COMPRESS);
            }
            if (availOut() == 0) {
                expandOutput(encoder.isPresent() ? encodersGuessAtGrowth() : availableIn);
            } else if (result == ZlibNgDeflater.Z_OK || result == ZlibNgDeflater.Z_STREAM_END) {
                break;
            } else {
                throw new IllegalStateException(NO_DECOMPRESS);
            }
        }
        buffer.setTail((int) totalOut());
        if (isFlushed || isFinishing) {
            byte[] output = takeBuffered();
            if (isFinishing) {
                encoder.ifPresent(ZlibNgDeflater::reset);
                decoder.ifPresent(ZlibNgInflater::reset);
            }
            return BinaryValue.ofBytes(output);
        }
        return handle;
    }

    Value read() {
        if (buffer == null) {
            return NoneValue.none();
        }
        if (encoder.isPresent()) {
            giveInput(NOTHING);
            giveRoom(buffer.tail());
            for (;;) {
                int result = run(ZlibNgDeflater.Z_SYNC_FLUSH);
                if (result < 0) {
                    throw new IllegalStateException(NO_COMPRESS);
                }
                if (availOut() == 0) {
                    expandOutput(encodersGuessAtGrowth());
                } else if (result == ZlibNgDeflater.Z_OK || result == ZlibNgDeflater.Z_STREAM_END) {
                    break;
                }
            }
            buffer.setTail((int) totalOut());
        }
        return BinaryValue.ofBytes(takeBuffered());
    }

    private byte[] takeBuffered() {
        byte[] output = Arrays.copyOf(buffer.octets(), buffer.tail());
        buffer.setTail(0);
        encoder.ifPresent(ZlibNgDeflater::forgetTotalOut);
        decoder.ifPresent(ZlibNgInflater::forgetTotalOut);
        return output;
    }

    private long encodersGuessAtGrowth() {
        ZlibNgDeflater stream = encoder.orElseThrow();
        return Long.divideUnsigned(stream.totalIn() - stream.totalOut(), 3) & UNSIGNED_32;
    }

    private void expandOutput(long delta) {
        int written = (int) totalOut();
        buffer.setTail(written);
        buffer.expandAtTail(delta);
        buffer.setTail(written);
        giveRoom(written);
    }

    private void giveInput(byte[] octets) {
        encoder.ifPresent(stream -> stream.input(octets, 0, octets.length));
        decoder.ifPresent(stream -> stream.input(octets, 0, octets.length));
    }

    private void giveRoom(int from) {
        int room = (int) (buffer.rest() - from);
        encoder.ifPresent(stream -> stream.output(buffer.octets(), from, room));
        decoder.ifPresent(stream -> stream.output(buffer.octets(), from, room));
    }

    private int run(int flush) {
        return encoder.map(stream -> stream.deflate(flush))
                .orElseGet(() -> decoder.orElseThrow().inflate(flush));
    }

    private int availOut() {
        return encoder.map(ZlibNgDeflater::availOut).orElseGet(() -> decoder.orElseThrow().availOut());
    }

    private long totalOut() {
        return encoder.map(ZlibNgDeflater::totalOut).orElseGet(() -> decoder.orElseThrow().totalOut());
    }
}
