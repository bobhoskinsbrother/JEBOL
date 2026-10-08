package org.jebol.domain.eval;

import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

final class WritingIntoATime {

    private static final long NANOSECONDS_IN_A_SECOND = 1_000_000_000L;

    private static final long NANOSECONDS_IN_A_MINUTE = 60 * NANOSECONDS_IN_A_SECOND;

    private static final long NANOSECONDS_IN_AN_HOUR = 60 * NANOSECONDS_IN_A_MINUTE;

    private static final int THE_HOUR = 0;

    private static final int THE_MINUTE = 1;

    private static final int THE_SECOND = 2;

    TimeValue written(TimeValue time, Value selector, Value written) {
        int part = thePartNamedBy(selector);
        long whole = theWholeNumberIn(written);
        long unsigned = Math.abs(time.nanoseconds());
        long hours = unsigned / NANOSECONDS_IN_AN_HOUR;
        long minutes = unsigned % NANOSECONDS_IN_AN_HOUR / NANOSECONDS_IN_A_MINUTE;
        long seconds = unsigned % NANOSECONDS_IN_A_MINUTE / NANOSECONDS_IN_A_SECOND;
        long fraction = unsigned % NANOSECONDS_IN_A_SECOND;
        switch (part) {
            case THE_HOUR -> hours = whole;
            case THE_MINUTE -> minutes = whole;
            case THE_SECOND -> {
                if (written instanceof AnyDecimalValue fractional) {
                    seconds = (long) fractional.quantity();
                    fraction = (long) ((fractional.quantity() - seconds) * NANOSECONDS_IN_A_SECOND);
                } else {
                    seconds = whole;
                    fraction = 0;
                }
            }
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH);
        }
        return TimeValue.ofNanoseconds(hours * NANOSECONDS_IN_AN_HOUR
                + minutes * NANOSECONDS_IN_A_MINUTE
                + seconds * NANOSECONDS_IN_A_SECOND + fraction);
    }

    private int thePartNamedBy(Value selector) {
        return switch (selector) {
            case AnyWordValue name when name.canonical().equals("hour") -> THE_HOUR;
            case AnyWordValue name when name.canonical().equals("minute") -> THE_MINUTE;
            case AnyWordValue name when name.canonical().equals("second") -> THE_SECOND;
            case IntegerValue(long position) -> (int) position - 1;
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH);
        };
    }

    private long theWholeNumberIn(Value written) {
        return switch (written) {
            case IntegerValue(long magnitude) -> notNegativeWithinThirtyTwoBits(magnitude, written);
            case AnyDecimalValue fractional -> notNegativeWithinThirtyTwoBits(
                    withinThirtyTwoBits(fractional.quantity(), written), written);
            case NoneValue _ -> 0;
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        };
    }

    private long withinThirtyTwoBits(double quantity, Value written) {
        if (quantity > Integer.MAX_VALUE || quantity < Integer.MIN_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, written);
        }
        return (long) quantity;
    }

    private long notNegativeWithinThirtyTwoBits(long whole, Value written) {
        if (whole > Integer.MAX_VALUE || whole < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, written);
        }
        return whole;
    }
}
