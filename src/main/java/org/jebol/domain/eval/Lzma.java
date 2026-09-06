package org.jebol.domain.eval;

/**
 * How Rebol frames an LZMA stream, which is not how anybody else does.
 *
 * <p>{@code CompressLzma} in u-compress.c writes the five property bytes the
 * SDK produces, then the stream, then the uncompressed length in four bytes
 * little endian:
 *
 * <pre>
 * *error = LzmaEncode(dest + headerSize, &amp;size, input, len, &amp;props, dest, &amp;headerSize, 0, ...);
 * SERIES_TAIL(*output) = size;
 * REBCNT_To_Bytes(out_size, (REBCNT)len); // Tag the size to the end.
 * Append_Series(*output, (REBYTE*)out_size, sizeof(REBCNT));
 * </pre>
 *
 * <p>A file written by 7-Zip has an eight-byte length in the header instead
 * and nothing at the end, so the two are not interchangeable even though the
 * bytes between are the same. The trailer is what DECOMPRESS reads to know how
 * much to make, unless DECOMPRESS/SIZE said so first.
 *
 * <p>Nine bytes is the floor, and a shorter binary is past-end rather than
 * bad-press: five for the properties and four for the length leaves nothing
 * for a stream, and the C checks that before it looks at anything.
 */
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

    /**
     * The one refusal here that is not bad-press.
     *
     * <p>{@code if (len < 9) Trap0(RE_PAST_END);} comes before anything else
     * in {@code DecompressLzma}, so nine bytes is a question about how much
     * data there is rather than about whether it is LZMA, and it is raised
     * from here because nothing above can tell the two apart.
     */
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
