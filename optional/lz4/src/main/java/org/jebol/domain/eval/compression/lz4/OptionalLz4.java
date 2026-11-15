package org.jebol.domain.eval.compression.lz4;

import org.jebol.domain.eval.CodecAsCompressionMethod;
import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.OptionalCompression;

public final class OptionalLz4 implements OptionalCompression {

    private static final int REPORTS_NOTHING = 0;

    @Override
    public String name() {
        return "lz4";
    }

    @Override
    public int placeInRebolsOrder() {
        return 5;
    }

    @Override
    public CompressionMethod method() {
        return new CodecAsCompressionMethod(REPORTS_NOTHING) {
            @Override
            protected byte[] encode(byte[] octets, int level) {
                return Lz4.compress(octets, level);
            }

            @Override
            protected byte[] decode(byte[] octets, int wanted) {
                return Lz4.decompress(octets, wanted);
            }

            @Override
            protected boolean cutsToSizeAsked() {
                return false;
            }
        };
    }
}
