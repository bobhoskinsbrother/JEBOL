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

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        return switch (right) {
            case TupleValue theirs -> octetByOctetAgainst(theirs, operation);
            case IntegerValue(long magnitude) -> everyOctetAgainst(magnitude, operation);
            default -> throw Raised.notRelated(this, right);
        };
    }

    private Value everyOctetAgainst(long magnitude, BitwiseOperation operation) {
        int[] combined = new int[segments.length];
        for (int at = 0; at < combined.length; at++) {
            combined[at] = clampedToAnOctet(
                    operation.onWholeElements(segments[at], magnitude));
        }
        return TupleValue.of(combined);
    }

    private Value octetByOctetAgainst(TupleValue theirs, BitwiseOperation operation) {
        int[] yours = theirs.segments;
        int[] combined = new int[Math.max(segments.length, yours.length)];
        for (int at = 0; at < combined.length; at++) {
            long mine = at < segments.length ? segments[at] : 0;
            long theirsHere = at < yours.length ? yours[at] : 0;
            combined[at] = clampedToAnOctet(
                    operation.onWholeElements(mine, theirsHere));
        }
        return TupleValue.of(combined);
    }

    private int clampedToAnOctet(long combined) {
        return (int) Math.clamp(combined, 0L, 255L);
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

    @Override
    public boolean equals(Object other) {
        return other instanceof TupleValue tuple
                && Arrays.equals(octetsToTwelve(), tuple.octetsToTwelve());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(octetsToTwelve());
    }

    @Override
    public String toString() {
        return IntStream.rangeClosed(1, shownCount())
                .mapToObj(position -> Integer.toString(octetAt(position)))
                .collect(Collectors.joining("."));
    }
}
