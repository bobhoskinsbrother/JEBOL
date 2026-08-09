package org.jebol.domain.value;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Three to twelve octets, written {@code 100.150.150}.
 *
 * <p>Used for colours, version numbers and addresses. The count of dots is
 * what separates a tuple from a decimal: one dot is a decimal, two or more is
 * a tuple, and the digits between them play no part.
 *
 * <p>Lexical rather than validated, as decided in {@code docs/decisions.md}
 * item 7, so a segment must be an octet but nothing checks that
 * {@code 999.1.1} means anything.
 */
public record TupleValue(int[] segments) implements Value {

    public static final int MINIMUM_SEGMENTS = 3;
    public static final int MAXIMUM_SEGMENTS = 12;

    public TupleValue {
        if (segments == null) {
            throw new IllegalArgumentException("a tuple must have segments");
        }
        if (segments.length < MINIMUM_SEGMENTS || segments.length > MAXIMUM_SEGMENTS) {
            throw new IllegalArgumentException(
                    "a tuple has " + MINIMUM_SEGMENTS + " to " + MAXIMUM_SEGMENTS
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

    public int segmentCount() {
        return segments.length;
    }

    public int segmentAt(int oneBasedPosition) {
        return segments[oneBasedPosition - 1];
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
        return other instanceof TupleValue tuple && Arrays.equals(segments, tuple.segments);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(segments);
    }

    @Override
    public String toString() {
        return IntStream.of(segments)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining("."));
    }
}
