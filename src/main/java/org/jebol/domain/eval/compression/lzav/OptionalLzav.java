package org.jebol.domain.eval.compression.lzav;

import org.jebol.domain.eval.CodecAsCompressionMethod;
import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.OptionalCompression;

public final class OptionalLzav implements OptionalCompression {

    private static final int REPORTS_NOTHING = 0;

    @Override
    public String name() {
        return "lzav";
    }

    @Override
    public int placeInRebolsOrder() {
        return 6;
    }

    @Override
    public CompressionMethod method() {
        return new CodecAsCompressionMethod(REPORTS_NOTHING) {
            @Override
            protected byte[] encode(byte[] octets, int level) {
                return Lzav.compress(octets);
            }

            @Override
            protected byte[] decode(byte[] octets, int wanted) {
                return Lzav.decompress(octets, wanted);
            }

            @Override
            protected boolean cutsToSizeAsked() {
                return false;
            }
        };
    }
}
