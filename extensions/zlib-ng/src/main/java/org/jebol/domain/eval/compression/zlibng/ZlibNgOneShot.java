package org.jebol.domain.eval.compression.zlibng;

import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.compression.SeriesRoom;

import java.util.Arrays;

/**
 * Rebol-Zlib-ng's {@code CompressCommon} and {@code DecompressCommon}: one whole buffer in, one out, as its
 * COMPRESS and DECOMPRESS commands and the methods it registers all do it. Failures throw
 * {@link CompressionRefused} with zlib-ng's result code.
 */
public final class ZlibNgOneShot {

    /** The three wrappers, by the window bits each opens zlib-ng with. */
    public enum Wrapping {
        DEFLATE(-15), ZLIB(15), GZIP(31);

        private final int windowBits;

        Wrapping(int windowBits) {
            this.windowBits = windowBits;
        }

        public int windowBits() {
            return windowBits;
        }
    }

    private static final int BEST_COMPRESSION = 9;
    private static final int MOST_HANDED_OUT_AT_ONCE = 1 << 20;
    private static final int GUESSED_GROWTH_PER_INPUT_OCTET = 3;

    private ZlibNgOneShot() {
    }

    /** Compresses as {@code CompressCommon} does, with the level unsigned as R3 passes it. */
    public static byte[] compress(Wrapping wrapping, byte[] input, long level, int operatingSystem) {
        int asked;
        if (level == CompressionMethod.NOT_ASKED) {
            asked = ZlibNgDeflater.Z_DEFAULT_COMPRESSION;
        } else {
            asked = (int) Math.min(level, BEST_COMPRESSION);
        }
        ZlibNgDeflater stream = ZlibNgDeflater.open(asked, wrapping.windowBits(), ZlibNgDeflater.DEFAULT_MEM_LEVEL,
                        ZlibNgDeflater.Z_DEFAULT_STRATEGY, operatingSystem)
                .orElseThrow(() -> new CompressionRefused(ZlibNgDeflater.Z_STREAM_ERROR));
        long room = SeriesRoom.makeBinaryOf(1 + stream.bound(input.length)) - 1;
        byte[] output = new byte[(int) room];
        stream.input(input, 0, input.length);
        stream.output(output, 0, output.length);
        for (;;) {
            int result = stream.deflate(ZlibNgDeflater.Z_FINISH);
            if (result == ZlibNgDeflater.Z_STREAM_END) {
                break;
            }
            if (result != ZlibNgDeflater.Z_OK) {
                throw new CompressionRefused(result);
            }
            if (stream.availOut() == 0) {
                int written = (int) stream.totalOut();
                output = Arrays.copyOf(output, written + Math.max(input.length, 1));
                stream.output(output, written, output.length - written);
            }
        }
        return Arrays.copyOf(output, (int) stream.totalOut());
    }

    /**
     * Decompresses as {@code DecompressCommon} does. A limit of {@link CompressionMethod#NOT_ASKED} decodes it all;
     * any other limit decodes into the room R3 gives a binary of that size and keeps that many octets, and a limit
     * of nought, which is false in the C's {@code limit &&}, decodes it all too.
     */
    public static byte[] decompress(Wrapping wrapping, byte[] input, long limit) {
        long size = limit != CompressionMethod.NOT_ASKED ? limit : (long) input.length * GUESSED_GROWTH_PER_INPUT_OCTET;
        long room = SeriesRoom.makeBinaryOf(size) - 1;
        ZlibNgInflater stream = ZlibNgInflater.open(wrapping.windowBits())
                .orElseThrow(() -> new CompressionRefused(ZlibNgInflater.Z_STREAM_ERROR));
        stream.input(input, 0, input.length);
        byte[] output = new byte[0];
        boolean isLimited = limit != 0 && limit != CompressionMethod.NOT_ASKED;
        for (;;) {
            int result;
            do {
                int written = (int) stream.totalOut();
                int handedOut = (int) Math.min(room, MOST_HANDED_OUT_AT_ONCE);
                if (output.length < written + handedOut) {
                    output = Arrays.copyOf(output, written + handedOut);
                }
                stream.output(output, written, handedOut);
                result = stream.inflate(ZlibNgInflater.Z_NO_FLUSH);
                room -= stream.totalOut() - written;
            } while (result == ZlibNgInflater.Z_OK && stream.availOut() == 0 && room > 0);
            if (result == ZlibNgInflater.Z_STREAM_END || (isLimited && stream.totalOut() >= limit)) {
                break;
            }
            if (result != ZlibNgInflater.Z_OK) {
                throw new CompressionRefused(result);
            }
            if (room == 0) {
                room = Math.max(input.length, 1);
            }
        }
        long kept = isLimited ? Math.min(stream.totalOut(), limit) : stream.totalOut();
        return Arrays.copyOf(output, (int) kept);
    }

    /** The method Rebol-Zlib-ng registers under each of its names. */
    public static CompressionMethod method(Wrapping wrapping, int operatingSystem) {
        return new CompressionMethod() {
            @Override
            public byte[] compress(byte[] octets, long level) {
                return ZlibNgOneShot.compress(wrapping, octets, level, operatingSystem);
            }

            @Override
            public byte[] decompress(byte[] octets, long limit) {
                return ZlibNgOneShot.decompress(wrapping, octets, limit);
            }
        };
    }
}
