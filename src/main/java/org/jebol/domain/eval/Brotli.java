package org.jebol.domain.eval;

/**
 * How Rebol calls Brotli, which is the plain stream with nothing round it.
 *
 * <p>{@code CompressBrotli} and {@code DecompressBrotli} in u-compress.c add
 * no header and no trailer, unlike the LZMA pair: what COMPRESS answers is
 * exactly what {@code BrotliEncoderCompressStream} wrote, and any other
 * Brotli reader will read it.
 *
 * <p>DECOMPRESS with no /SIZE therefore does not know how long the answer is
 * before it starts, which is why the C guesses twice the input and grows. Here
 * the answer grows the same way and the guess does not matter.
 *
 * <p>With /SIZE the C stops as soon as it has more than was asked for and cuts
 * there, so a stream damaged past that point is never looked at. That is
 * followed rather than corrected: reading the front of something is what the
 * refinement is for.
 */
final class Brotli {

    private Brotli() {
    }

    static byte[] compressed(byte[] octets) {
        return BrotliEncoder.encoded(octets);
    }

    static byte[] decompressed(byte[] octets, int wanted) {
        return BrotliDecoder.decoded(octets,
                wanted > 0 ? wanted : BrotliDecoder.NO_LIMIT);
    }
}
