package org.jebol.application;

import org.jebol.domain.eval.compression.SeriesRoom;
import org.jebol.domain.eval.compression.zlibng.ZlibNgDeflater;
import org.jebol.domain.eval.compression.zlibng.ZlibNgInflater;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.Optional;

final class ZlibNgStream {

    static final String NO_COMPRESS = "Failed to compress using the Zlib encoder!";
    static final String NO_DECOMPRESS = "Failed to decompress using the Zlib decoder!";

    private static final long HIGH_BIT_REFUSED_AS_PAST_END = 0x80000000L;
    private static final long UNSIGNED_32 = 0xFFFFFFFFL;
    private static final int POWER_OF_TWO_BELOW = 512 * 1024;
    private static final long MOST_ROOM_GIVEN = 1L << 28;
    private static final byte[] NOTHING = new byte[0];

    private final Optional<ZlibNgDeflater> encoder;
    private final Optional<ZlibNgInflater> decoder;
    private byte[] buffer;
    private long rest;
    private int tail;
    private boolean isRecentlyExpanded;

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
                rest = SeriesRoom.makeBinaryOf(size);
                buffer = new byte[(int) rest];
                tail = 0;
            }
        }
        giveRoom(tail);
        for (;;) {
            int result = run(flush);
            if (result < 0) {
                throw new IllegalStateException(NO_COMPRESS);
            }
            if (availOut() == 0) {
                tail = (int) totalOut();
                expand(encoder.isPresent() ? encodersGuessAtGrowth() : availableIn);
                giveRoom((int) totalOut());
            } else if (result == ZlibNgDeflater.Z_OK || result == ZlibNgDeflater.Z_STREAM_END) {
                break;
            } else {
                throw new IllegalStateException(NO_DECOMPRESS);
            }
        }
        tail = (int) totalOut();
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
            giveRoom(tail);
            for (;;) {
                int result = run(ZlibNgDeflater.Z_SYNC_FLUSH);
                if (result < 0) {
                    throw new IllegalStateException(NO_COMPRESS);
                }
                if (availOut() == 0) {
                    tail = (int) totalOut();
                    expand(encodersGuessAtGrowth());
                    giveRoom((int) totalOut());
                } else if (result == ZlibNgDeflater.Z_OK || result == ZlibNgDeflater.Z_STREAM_END) {
                    break;
                }
            }
            tail = (int) totalOut();
        }
        return BinaryValue.ofBytes(takeBuffered());
    }

    private byte[] takeBuffered() {
        byte[] output = Arrays.copyOf(buffer, tail);
        tail = 0;
        encoder.ifPresent(ZlibNgDeflater::forgetTotalOut);
        decoder.ifPresent(ZlibNgInflater::forgetTotalOut);
        return output;
    }

    private long encodersGuessAtGrowth() {
        ZlibNgDeflater stream = encoder.orElseThrow();
        return Long.divideUnsigned(stream.totalIn() - stream.totalOut(), 3) & UNSIGNED_32;
    }

    private void expand(long delta) {
        if (delta == 0) {
            return;
        }
        if ((delta & HIGH_BIT_REFUSED_AS_PAST_END) != 0) {
            throw Raised.of(EvaluationFailure.PAST_END);
        }
        long size = tail + 1L;
        if (size + delta > rest) {
            long extra = isRecentlyExpanded ? tail + delta + 1 : 1;
            long newSize = tail + delta + extra;
            if (newSize > UNSIGNED_32) {
                throw Raised.of(EvaluationFailure.PAST_END);
            }
            rest = SeriesRoom.makeSeriesOf(newSize, newSize < POWER_OF_TWO_BELOW);
            if (rest > MOST_ROOM_GIVEN) {
                throw Raised.of(EvaluationFailure.NO_MEMORY);
            }
            buffer = Arrays.copyOf(buffer, (int) rest);
            isRecentlyExpanded = true;
        }
    }

    private void giveInput(byte[] octets) {
        encoder.ifPresent(stream -> stream.input(octets, 0, octets.length));
        decoder.ifPresent(stream -> stream.input(octets, 0, octets.length));
    }

    private void giveRoom(int from) {
        int room = (int) (rest - from);
        encoder.ifPresent(stream -> stream.output(buffer, from, room));
        decoder.ifPresent(stream -> stream.output(buffer, from, room));
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
