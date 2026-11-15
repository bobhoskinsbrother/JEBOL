package org.jebol.domain.eval.compression;

import org.jebol.domain.eval.CodecAsCompressionMethod;
import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.OptionalCompression;

public final class OptionalCrush implements OptionalCompression {

    private static final int COPIES_FROM_BEFORE_START = 0;

    private static final int LENGTH_FIELD = 4;

    @Override
    public String name() {
        return "crush";
    }

    @Override
    public int placeInRebolsOrder() {
        return 4;
    }

    @Override
    public CompressionMethod method() {
        return new CodecAsCompressionMethod(COPIES_FROM_BEFORE_START) {
            @Override
            protected byte[] encode(byte[] octets, int level) {
                return Crush.compress(octets, level);
            }

            @Override
            protected byte[] decode(byte[] octets, int wanted) {
                if (octets.length < LENGTH_FIELD) {
                    throw CompressionRefused.namingMethod();
                }
                return Crush.decompress(octets, wanted);
            }
        };
    }
}
