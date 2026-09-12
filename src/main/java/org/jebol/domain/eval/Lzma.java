package org.jebol.domain.eval;

final class Lzma {

    private Lzma() {
    }

    private static final int PROPERTIES_LENGTH = 5;
    private static final int LENGTH_TRAILER = 4;
    private static final int SHORTEST_STREAM = PROPERTIES_LENGTH + LENGTH_TRAILER;

    static byte[] compressed(byte[] octets, int level) {
        byte[] properties = LzmaEncoder.properties(level);
        byte[] body = LzmaEncoder.encoded(octets, level);
        byte[] whole =
                new byte[properties.length + body.length + LENGTH_TRAILER];
        System.arraycopy(properties, 0, whole, 0, properties.length);
        System.arraycopy(body, 0, whole, properties.length, body.length);
        int at = properties.length + body.length;
        for (int each = 0; each < LENGTH_TRAILER; each++) {
            whole[at + each] = (byte) (octets.length >>> (8 * each));
        }
        return whole;
    }

    static byte[] decompressed(byte[] octets, int wanted) {
        if (octets.length < SHORTEST_STREAM) {
            throw Raised.of(EvaluationFailure.PAST_END);
        }
        byte[] properties = new byte[PROPERTIES_LENGTH];
        System.arraycopy(octets, 0, properties, 0, PROPERTIES_LENGTH);
        int howLong = wanted > 0 ? wanted : lengthTaggedOnTheEnd(octets);
        return LzmaDecoder.decoded(properties, octets, PROPERTIES_LENGTH,
                octets.length - PROPERTIES_LENGTH, howLong);
    }

    private static int lengthTaggedOnTheEnd(byte[] octets) {
        int at = octets.length - LENGTH_TRAILER;
        int length = 0;
        for (int each = 0; each < LENGTH_TRAILER; each++) {
            length |= (octets[at + each] & 0xFF) << (8 * each);
        }
        if (length < 0) {
            throw new IllegalArgumentException(
                    "LZMA length trailer asks for more than two gigabytes");
        }
        return length;
    }
}
