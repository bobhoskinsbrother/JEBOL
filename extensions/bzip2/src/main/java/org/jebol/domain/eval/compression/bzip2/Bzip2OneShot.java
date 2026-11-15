package org.jebol.domain.eval.compression.bzip2;

import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.compression.SeriesRoom;

import java.util.Arrays;

public final class Bzip2OneShot {

    public static final long NO_LIMIT = 0xFFFFFFFFL;
    public static final long NO_CEILING = 0;
    public static final long LARGEST_OUTPUT = Integer.MAX_VALUE;
    private static final int LARGEST_BLOCK_SIZE = 9;
    private static final int MOST_ATTEMPTS = 32;
    private static final int BOUND_FLOOR = 600;
    private static final int GROWTH_SHIFT = 2;

    private Bzip2OneShot() {
    }

    public static byte[] compress(byte[] input, long level) {
        long bound = (input.length + (long) input.length / 100 + BOUND_FLOOR) & NO_LIMIT;
        if (bound > LARGEST_OUTPUT) {
            throw new CompressionRefused(0);
        }
        int blockSize = level == 0 ? LARGEST_BLOCK_SIZE : Math.max(1, Math.min(LARGEST_BLOCK_SIZE, (int) level));
        Bzip2Buffers buffers = new Bzip2Buffers();
        buffers.input = input;
        buffers.availableIn = input.length;
        buffers.output = new byte[(int) SeriesRoom.makeBinaryOf(bound)];
        buffers.availableOut = buffers.output.length;
        int answer = new Bzip2CompressionStream(blockSize).compress(buffers, Bzip2Codes.FINISH);
        if (answer == Bzip2Codes.FINISH_OK) {
            throw new CompressionRefused(Bzip2Codes.OUTBUFF_FULL);
        }
        if (answer != Bzip2Codes.STREAM_END) {
            throw new CompressionRefused(answer);
        }
        return Arrays.copyOf(buffers.output, buffers.nextOut);
    }

    public static byte[] decompress(byte[] input, long limit, long ceiling) {
        long cap = ceiling == NO_CEILING ? LARGEST_OUTPUT : Math.min(ceiling, LARGEST_OUTPUT);
        long room = limit != NO_LIMIT ? Math.min(limit, cap) : Math.min((long) input.length << GROWTH_SHIFT, cap);
        if (room == 0) {
            return new byte[0];
        }
        for (int attempt = 0; attempt < MOST_ATTEMPTS; attempt++) {
            Bzip2Buffers buffers = new Bzip2Buffers();
            buffers.input = input;
            buffers.availableIn = input.length;
            buffers.output = new byte[(int) SeriesRoom.makeBinaryOf(room)];
            buffers.availableOut = buffers.output.length;
            int answer = new Bzip2DecompressionStream().decompress(buffers);
            if (answer == Bzip2Codes.STREAM_END) {
                return Arrays.copyOf(buffers.output, clampToLimit(buffers.nextOut, limit));
            }
            if (answer != Bzip2Codes.OK) {
                throw new CompressionRefused(answer);
            }
            if (buffers.availableOut > 0) {
                throw new CompressionRefused(Bzip2Codes.UNEXPECTED_EOF);
            }
            if (limit != NO_LIMIT) {
                return Arrays.copyOf(buffers.output, (int) room);
            }
            long next = Math.min(room << GROWTH_SHIFT, cap);
            if (next <= room) {
                throw new CompressionRefused(Bzip2Codes.OUTBUFF_FULL);
            }
            room = next;
        }
        throw new CompressionRefused(Bzip2Codes.OUTBUFF_FULL);
    }

    private static int clampToLimit(int written, long limit) {
        return limit != NO_LIMIT && written > limit ? (int) limit : written;
    }

    public static CompressionMethod method() {
        return new CompressionMethod() {
            @Override
            public byte[] compress(byte[] octets, long level) {
                return Bzip2OneShot.compress(octets, level);
            }

            @Override
            public byte[] decompress(byte[] octets, long limit) {
                return Bzip2OneShot.decompress(octets, limit, NO_CEILING);
            }
        };
    }
}
