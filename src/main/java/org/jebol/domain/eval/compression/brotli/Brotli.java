package org.jebol.domain.eval.compression.brotli;

public final class Brotli {

    private Brotli() {
    }

    public static byte[] compress(byte[] octets, int level) {
        return BrotliEncoder.encode(octets, level);
    }

    public static byte[] decompress(byte[] octets, int wanted) {
        return BrotliDecoder.decodeUpToLimit(octets,
                wanted > 0 ? wanted : BrotliDecoder.NO_LIMIT);
    }
}
