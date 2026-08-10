package org.jebol.domain.value;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Up to twelve octets, written {@code 100.150.150}, with zeros behind them.
 *
 * <p>Used for colours, version numbers and addresses. The count of dots is
 * what separates a tuple from a decimal: one dot is a decimal, two or more is
 * a tuple, and the digits between them play no part.
 *
 * <p>Two lengths, and nearly everything about a tuple follows from the gap
 * between them. The kept length is how many octets were written down, and
 * a tuple made from a short binary or a short block may keep fewer than
 * three. The shown length is never below three, so a tuple of one octet
 * still reads and molds as three.
 *
 * <p>The octets past the kept length are zeros rather than absent, which
 * is what makes {@code 1.2.3} equal to {@code 1.2.3.0}. The two are equal
 * and are not the same tuple, because only the length tells them apart.
 * That is the whole of the difference between {@code =} and {@code ==}
 * here, and it is why the length is kept rather than being padded away on
 * the way in.
 *
 * <p>This is {@code REBTUP} in {@code sys-value.h}: a length byte and
 * twelve octets. {@code Emit_Tuple} in {@code t-tuple.c} is the padding to
 * three, and {@code Cmp_Tuple} beside it is the comparison over the zeros.
 */
public record TupleValue(int[] segments) implements Value {

    /** What a tuple shows however few octets it keeps. */
    public static final int MINIMUM_SHOWN_SEGMENTS = 3;

    public static final int MAXIMUM_SEGMENTS = 12;

    public TupleValue {
        if (segments == null) {
            throw new IllegalArgumentException("a tuple must have segments");
        }
        if (segments.length > MAXIMUM_SEGMENTS) {
            throw new IllegalArgumentException(
                    "a tuple keeps at most " + MAXIMUM_SEGMENTS
                            + " segments, got " + segments.length);
        }
        for (int segment : segments) {
            if (segment < 0 || segment > 255) {
                throw new IllegalArgumentException(
                        "a tuple segment is an octet, got " + segment);
            }
        }
        segments = segments.clone();
    }

    public static TupleValue of(int... segments) {
        return new TupleValue(segments);
    }

    /** How many octets were written down, which may be fewer than three. */
    public int segmentCount() {
        return segments.length;
    }

    /** How many octets a tuple shows, which is never fewer than three. */
    public int shownCount() {
        return Math.max(segments.length, MINIMUM_SHOWN_SEGMENTS);
    }

    /** An octet by position, counting from one, and zero past the kept ones. */
    public int octetAt(int oneBasedPosition) {
        return oneBasedPosition >= 1 && oneBasedPosition <= segments.length
                ? segments[oneBasedPosition - 1]
                : 0;
    }

    /** The octets a comparison sees: what was written, then zeros. */
    public int[] octetsToTwelve() {
        int[] all = new int[MAXIMUM_SEGMENTS];
        System.arraycopy(segments, 0, all, 0, segments.length);
        return all;
    }

    @Override
    public int[] segments() {
        return segments.clone();
    }

    @Override
    public Datatype datatype() {
        return Datatype.TUPLE;
    }

    /**
     * Equal when the octets agree out to twelve, whatever the lengths.
     *
     * <p>{@code Cmp_Tuple} compares over the longer of the two lengths and
     * the octets behind each are zeros, so this is the same question as
     * comparing both padded to twelve. The length is asked about only by
     * {@code ==} and by SAME?, which is {@code CT_Tuple} mode 2 and above.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof TupleValue tuple
                && Arrays.equals(octetsToTwelve(), tuple.octetsToTwelve());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(octetsToTwelve());
    }

    /**
     * The octets with dots between them, padded out to three.
     *
     * <p>{@code Emit_Tuple} writes each kept octet and then keeps writing
     * "0." until it has written three, so a tuple keeping one octet shows
     * as 1.0.0 and one keeping none shows as 0.0.0.
     */
    @Override
    public String toString() {
        return IntStream.rangeClosed(1, shownCount())
                .mapToObj(position -> Integer.toString(octetAt(position)))
                .collect(Collectors.joining("."));
    }
}
