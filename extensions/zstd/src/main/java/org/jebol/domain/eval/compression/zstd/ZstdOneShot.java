package org.jebol.domain.eval.compression.zstd;

import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.compression.ExpandingBinary;
import org.jebol.domain.eval.compression.SeriesRoom;

import java.util.Arrays;

public final class ZstdOneShot {

    private static final long NO_LIMIT = 0xFFFFFFFFL;
    private static final long LARGEST_SERIES = Integer.MAX_VALUE;

    private final ZstdDecompressionStream decoder = new ZstdDecompressionStream();

    public static byte[] compress(byte[] input, long level) {
        int quality = (int) Math.min(ZstdCompressionParameters.MAX_LEVEL, level & 0xFFFFFFFFL);
        long bound = ZstdCompressionContext.compressBound(input.length);
        if (bound > LARGEST_SERIES) {
            throw new CompressionRefused(0);
        }
        try {
            return ZstdCompressionContext.compressOnce(input, quality);
        } catch (ZstdFailure failure) {
            throw new CompressionRefused(-failure.error().code());
        }
    }

    public byte[] decompress(byte[] input, long limit) {
        long asked = limit & 0xFFFFFFFFL;
        decoder.reset();
        long outputLength = asked != NO_LIMIT ? asked : ((long) input.length << 1) & 0xFFFFFFFFL;
        if (outputLength == 0) {
            return new byte[0];
        }
        outputLength = Math.min(outputLength, LARGEST_SERIES);
        ExpandingBinary output = new ExpandingBinary(SeriesRoom.makeBinaryOf(outputLength));
        ZstdStreamBuffer in = new ZstdStreamBuffer(input, input.length, 0);
        ZstdStreamBuffer out = new ZstdStreamBuffer(output.octets(), (int) output.available(), 0);
        for (;;) {
            long result;
            try {
                result = decoder.decompress(out, in);
            } catch (ZstdFailure failure) {
                throw new CompressionRefused(-failure.error().code());
            }
            if (result == 0 || (asked != NO_LIMIT && out.position > asked)) {
                break;
            }
            if (out.position == out.size) {
                output.setTail(out.position);
                output.expandAtTail(output.rest());
                output.setTail(out.position);
                out = new ZstdStreamBuffer(output.octets(), (int) output.available(), out.position);
            }
        }
        int kept = asked != NO_LIMIT && out.position > asked ? (int) asked : out.position;
        return Arrays.copyOf(out.octets, kept);
    }

    public CompressionMethod method() {
        return new CompressionMethod() {
            @Override
            public byte[] compress(byte[] octets, long level) {
                return ZstdOneShot.compress(octets, level);
            }

            @Override
            public byte[] decompress(byte[] octets, long limit) {
                return ZstdOneShot.this.decompress(octets, limit);
            }
        };
    }
}
