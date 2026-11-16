package org.jebol.domain.eval.compression.brotli;

import org.jebol.domain.eval.CodecAsCompressionMethod;
import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.OptionalCompression;

public final class OptionalBrotli implements OptionalCompression {

    private static final int NEEDS_MORE_INPUT = 2;

    @Override
    public String name() {
        return "br";
    }

    @Override
    public int placeInRebolsOrder() {
        return 3;
    }

    @Override
    public CompressionMethod method() {
        return new CodecAsCompressionMethod(NEEDS_MORE_INPUT) {
            @Override
            protected byte[] encode(byte[] octets, int level) {
                return Brotli.compress(octets, level);
            }

            @Override
            protected byte[] decode(byte[] octets, int wanted) {
                return Brotli.decompress(octets, wanted);
            }
        };
    }
}
