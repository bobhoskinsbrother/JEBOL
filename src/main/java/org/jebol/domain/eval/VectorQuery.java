package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What a vector answers about itself: its shape and the statistics of what it
 * holds.
 *
 * <p>All of it reads the whole storage rather than what is left from the
 * current position. {@code Query_Vector_Statictics} and
 * {@code Find_Minimum_Of_Vector} both start at {@code SERIES_DATA} and run to
 * {@code SERIES_TAIL}, so the sum of a vector is the sum of the vector however
 * far into it the value happens to point. LENGTH? is the exception and does
 * not come through here: the generic series action answers it before the
 * vector's own arm is reached, so it counts from the position, while
 * {@code v/length} counts the storage.
 */
public final class VectorQuery {

    /** The fields {@code query v none} lists, in the order it lists them. */
    public static final List<String> FIELDS = List.of(
            "signed", "type", "size", "length", "minimum", "maximum", "range",
            "sum", "mean", "median", "variance", "sample-variance",
            "population-deviation", "sample-deviation");

    private VectorQuery() {
    }

    /** One field, or nothing at all when the name is not one of them. */
    public static Optional<Value> field(VectorValue vector, String named) {
        VectorKind kind = vector.kind();
        return switch (named) {
            case "signed" -> Optional.of(LogicValue.of(kind.isSigned()));
            case "type" -> Optional.of(
                    WordValue.of(kind.elementDatatype().literalSpelling()));
            case "size" -> Optional.of(IntegerValue.of(kind.bits()));
            case "length" -> Optional.of(IntegerValue.of(vector.storageLength()));
            case "min", "minimum" -> Optional.of(extreme(vector, true));
            case "max", "maximum" -> Optional.of(extreme(vector, false));
            default -> statistic(vector, named);
        };
    }

    /**
     * The spec a vector reflects, which is how it would be written down again.
     *
     * <p>{@code [unsigned integer! 32 2]}, with the first word left out when
     * the kind is signed, because that is the default the older spelling
     * assumes.
     */
    public static BlockValue specOf(VectorValue vector) {
        VectorKind kind = vector.kind();
        java.util.List<Value> written = new java.util.ArrayList<>();
        if (!kind.isSigned()) {
            written.add(WordValue.of("unsigned"));
        }
        written.add(WordValue.of(kind.elementDatatype().literalSpelling()));
        written.add(IntegerValue.of(kind.bits()));
        written.add(IntegerValue.of(vector.storageLength()));
        return BlockValue.block(written);
    }

    private static Value extreme(VectorValue vector, boolean wantingTheLeast) {
        int held = vector.storageLength();
        if (held == 0) {
            return NoneValue.none();
        }
        VectorKind kind = vector.kind();
        long best = vector.storage().at(1);
        for (int at = 2; at <= held; at++) {
            long candidate = vector.storage().at(at);
            boolean better = wantingTheLeast
                    ? ordersBefore(kind, candidate, best)
                    : ordersBefore(kind, best, candidate);
            if (better) {
                best = candidate;
            }
        }
        return kind.read(best);
    }

    /**
     * Whether one stored element sorts before another.
     *
     * <p>The widest unsigned kind is compared as unsigned here, which is what
     * the C's minimum and maximum do and what its statistics do not: they read
     * every integer kind as signed. The difference only shows on a
     * {@code uint64!} holding more than a signed long can.
     */
    static boolean ordersBefore(VectorKind kind, long left, long right) {
        if (kind.measures()) {
            return kind.asDecimal(left) < kind.asDecimal(right);
        }
        return kind.isSigned()
                ? left < right
                : Long.compareUnsigned(left, right) < 0;
    }

    private static Optional<Value> statistic(VectorValue vector, String named) {
        if (!FIELDS.contains(named) && !"average".equals(named)) {
            return Optional.empty();
        }
        Spread spread = Spread.of(vector);
        if (spread.count == 0) {
            return Optional.of(NoneValue.none());
        }
        VectorKind kind = vector.kind();
        return Optional.of(switch (named) {
            case "sum" -> asTheVectorCounts(kind, spread.sum);
            case "range" -> asTheVectorCounts(kind,
                    spread.largest - spread.smallest);
            case "mean", "average" -> DecimalValue.of(spread.mean);
            case "median" -> DecimalValue.of(medianOf(vector));
            case "variance" -> DecimalValue.of(spread.variance());
            case "population-deviation" -> DecimalValue.of(Math.sqrt(spread.variance()));
            case "sample-variance" -> spread.count <= 1
                    ? NoneValue.none()
                    : DecimalValue.of(spread.sampleVariance());
            case "sample-deviation" -> spread.count <= 1
                    ? NoneValue.none()
                    : DecimalValue.of(Math.sqrt(spread.sampleVariance()));
            default -> NoneValue.none();
        });
    }

    /**
     * A total or a spread given back in the vector's own terms.
     *
     * <p>The C computes every statistic as a double and then, at its
     * {@code return_number} label, turns the answer back into an integer when
     * the vector counts. Only the sum and the range take that path; a mean is
     * a decimal whatever the vector holds.
     */
    private static Value asTheVectorCounts(VectorKind kind, double answer) {
        return kind.measures() ? DecimalValue.of(answer) : IntegerValue.of((long) answer);
    }

    private static double medianOf(VectorValue vector) {
        VectorKind kind = vector.kind();
        long[] sorted = vector.storage().snapshot();
        sortAscending(kind, sorted);
        int middle = sorted.length / 2;
        double above = kind.asDecimal(sorted[middle]);
        if (sorted.length % 2 != 0) {
            return above;
        }
        return (kind.asDecimal(sorted[middle - 1]) + above) / 2.0;
    }

    /** Ascending by the kind's own ordering, which is not the ordering of the bits. */
    public static void sortAscending(VectorKind kind, long[] elements) {
        Long[] boxed = Arrays.stream(elements).boxed().toArray(Long[]::new);
        Arrays.sort(boxed, (left, right) -> {
            if (ordersBefore(kind, left, right)) {
                return -1;
            }
            return ordersBefore(kind, right, left) ? 1 : 0;
        });
        for (int at = 0; at < elements.length; at++) {
            elements[at] = boxed[at];
        }
    }

    /**
     * What one pass over a vector learns about the spread of its numbers.
     *
     * <p>Welford's method, which the C uses to get a mean and a variance in
     * one pass without the sum of squares growing until it loses the small
     * differences it is supposed to be measuring.
     */
    private record Spread(int count, double smallest, double largest, double sum,
            double mean, double sumOfSquaredDeviations) {

        static Spread of(VectorValue vector) {
            int held = vector.storageLength();
            if (held == 0) {
                return new Spread(0, 0, 0, 0, 0, 0);
            }
            VectorKind kind = vector.kind();
            double first = kind.asDecimal(vector.storage().at(1));
            double smallest = first;
            double largest = first;
            double sum = first;
            double mean = first;
            double squared = 0;
            for (int at = 2; at <= held; at++) {
                double number = kind.asDecimal(vector.storage().at(at));
                if (number < smallest) {
                    smallest = number;
                } else if (number > largest) {
                    largest = number;
                }
                sum += number;
                double before = number - mean;
                mean += before / at;
                squared += before * (number - mean);
            }
            return new Spread(held, smallest, largest, sum, mean, squared);
        }

        double variance() {
            return sumOfSquaredDeviations / count;
        }

        double sampleVariance() {
            return sumOfSquaredDeviations / (count - 1);
        }
    }
}
