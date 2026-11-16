package org.jebol.domain.eval.compression;

import org.jebol.domain.eval.CodecAsCompressionMethod;
import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.OptionalCompression;

public final class OptionalLzma implements OptionalCompression {

    private static final int INPUT_ENDS_EARLY = 6;

    @Override
    public String name() {
        return "lzma";
    }

    @Override
    public int placeInRebolsOrder() {
        return 7;
    }

    @Override
    public CompressionMethod method() {
        return new CodecAsCompressionMethod(INPUT_ENDS_EARLY) {
            @Override
            protected byte[] encode(byte[] octets, int level) {
                return Lzma.compress(octets, level);
            }

            @Override
            protected byte[] decode(byte[] octets, int wanted) {
                return Lzma.decompress(octets, wanted);
            }
        };
    }
}
