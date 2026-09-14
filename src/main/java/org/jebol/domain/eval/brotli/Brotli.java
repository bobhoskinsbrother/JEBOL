package org.jebol.domain.eval.brotli;


public final class Brotli {

    private Brotli() {
    }

    public static byte[] compressed(byte[] octets, int level) {
        return BrotliEncoder.encoded(octets, level);
    }

    public static byte[] decompressed(byte[] octets, int wanted) {
        return BrotliDecoder.decodedStoppingOnceTheAnswerPassesTheLimit(octets,
                wanted > 0 ? wanted : BrotliDecoder.NO_LIMIT);
    }
}
