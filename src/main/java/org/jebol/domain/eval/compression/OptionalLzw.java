package org.jebol.domain.eval.compression;

import org.jebol.domain.eval.CodecAsCompressionMethod;
import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.OptionalCompression;

public final class OptionalLzw implements OptionalCompression {

    private static final int BAD_DATA = 1;

    @Override
    public String name() {
        return "lzw";
    }

    @Override
    public int placeInRebolsOrder() {
        return 8;
    }

    @Override
    public CompressionMethod method() {
        Lzw lzw = new Lzw();
        return new CodecAsCompressionMethod(BAD_DATA) {
            @Override
            protected byte[] encode(byte[] octets, int level) {
                return lzw.compress(octets, level);
            }

            @Override
            protected byte[] decode(byte[] octets, int wanted) {
                return lzw.decompress(octets, wanted);
            }
        };
    }
}
