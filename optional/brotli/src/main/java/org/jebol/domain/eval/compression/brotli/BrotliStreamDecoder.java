package org.jebol.domain.eval.compression.brotli;

public final class BrotliStreamDecoder {

    private final BrotliDecoder.Stream stream = new BrotliDecoder.Stream();
    private IllegalArgumentException refusal;

    public byte[] decompress(byte[] input) {
        if (refusal != null) {
            throw refusal;
        }
        if (stream.isFinished()) {
            return new byte[0];
        }
        stream.give(input);
        try {
            stream.run();
        } catch (IllegalArgumentException refused) {
            refusal = refused;
            throw refused;
        }
        return stream.takeNewOutput();
    }

    public boolean isFinished() {
        return stream.isFinished();
    }
}
