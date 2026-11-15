package org.jebol.domain.eval.mathpresso;

import org.jebol.domain.eval.mathpresso.SseDoubles.Predicate;

import static org.jebol.domain.eval.mathpresso.SseDoubles.MAGNITUDE;
import static org.jebol.domain.eval.mathpresso.SseDoubles.ONE;
import static org.jebol.domain.eval.mathpresso.SseDoubles.SIGN;
import static org.jebol.domain.eval.mathpresso.SseDoubles.add;
import static org.jebol.domain.eval.mathpresso.SseDoubles.mask;
import static org.jebol.domain.eval.mathpresso.SseDoubles.maximum;
import static org.jebol.domain.eval.mathpresso.SseDoubles.subtract;

final class Sse2Rounding {

    private static final long LARGEST_WITH_FRACTION = Double.doubleToRawLongBits(4503599627370496.0);
    private static final long SHIFTER = Double.doubleToRawLongBits(6755399441055744.0);
    private static final long SHIFTER_AND_ONE = Double.doubleToRawLongBits(6755399441055745.0);

    private Sse2Rounding() {
    }

    static long nearestEven(long source) {
        long shifted = subtract(add(source, SHIFTER), SHIFTER);
        long keep = mask(source, LARGEST_WITH_FRACTION, Predicate.NOT_LESS);
        return (source & keep) | (~keep & shifted);
    }

    static long halfUp(long source) {
        long nearest = subtract(add(source, SHIFTER), SHIFTER);
        long nearestAfterOne = subtract(add(source, SHIFTER_AND_ONE), SHIFTER_AND_ONE);
        long keep = mask(source, LARGEST_WITH_FRACTION, Predicate.NOT_LESS);
        return combine(source, keep, maximum(nearest, nearestAfterOne));
    }

    static long floor(long source) {
        long nearest = subtract(add(source, SHIFTER), SHIFTER);
        long keep = mask(source, LARGEST_WITH_FRACTION, Predicate.NOT_LESS);
        long overshot = mask(source, nearest, Predicate.LESS) & ONE;
        return combine(source, keep, subtract(nearest, overshot));
    }

    static long ceil(long source) {
        long nearest = subtract(add(source, SHIFTER), SHIFTER);
        long keep = mask(source, LARGEST_WITH_FRACTION, Predicate.NOT_LESS);
        long undershot = mask(source, nearest, Predicate.NOT_LESS_OR_EQUAL) & ONE;
        return combine(source, keep, add(nearest, undershot));
    }

    static long truncate(long source) {
        long magnitude = MAGNITUDE & source;
        long nearest = subtract(add(magnitude, SHIFTER), SHIFTER);
        long keep = mask(magnitude, LARGEST_WITH_FRACTION, Predicate.NOT_LESS) | SIGN;
        long overshot = mask(magnitude, nearest, Predicate.LESS) & ONE;
        return combine(source, keep, subtract(nearest, overshot));
    }

    private static long combine(long source, long keep, long rounded) {
        return (source & keep) | (~keep & rounded);
    }
}
