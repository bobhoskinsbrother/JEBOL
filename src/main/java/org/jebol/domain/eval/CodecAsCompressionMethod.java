package org.jebol.domain.eval;

import java.util.Arrays;

public abstract class CodecAsCompressionMethod implements CompressionMethod {

    private static final int AS_MUCH_AS_IT_HOLDS = 0;

    private final int codeForUnreadableData;

    protected CodecAsCompressionMethod(int codeForUnreadableData) {
        this.codeForUnreadableData = codeForUnreadableData;
    }

    protected abstract byte[] encode(byte[] octets, int level);

    protected abstract byte[] decode(byte[] octets, int wanted);

    protected boolean cutsToSizeAsked() {
        return true;
    }

    @Override
    public final byte[] compress(byte[] octets, long level) {
        return encode(octets, (int) level);
    }

    @Override
    public final byte[] decompress(byte[] octets, long limit) {
        int wanted = limit == NOT_ASKED ? AS_MUCH_AS_IT_HOLDS : (int) limit;
        byte[] whole;
        try {
            whole = decode(octets, wanted);
        } catch (IllegalArgumentException unreadable) {
            throw new CompressionRefused(codeForUnreadableData);
        }
        return cutsToSizeAsked() && wanted > AS_MUCH_AS_IT_HOLDS && whole.length > wanted
                ? Arrays.copyOf(whole, wanted)
                : whole;
    }
}
