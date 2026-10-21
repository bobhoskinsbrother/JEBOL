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

    private static final long NANOSECONDS_IN_SECOND = 1_000_000_000L;

    private static final long NANOSECONDS_IN_MINUTE = 60 * NANOSECONDS_IN_SECOND;

    private static final long NANOSECONDS_IN_HOUR = 60 * NANOSECONDS_IN_MINUTE;

    private static final int HOUR = 0;

    private static final int MINUTE = 1;

    private static final int SECOND = 2;

    TimeValue write(TimeValue time, Value selector, Value written) {
        int part = findPartNamedBy(selector);
        long whole = readWholeNumberIn(written);
        long unsigned = Math.abs(time.nanoseconds());
        long hours = unsigned / NANOSECONDS_IN_HOUR;
        long minutes = unsigned % NANOSECONDS_IN_HOUR / NANOSECONDS_IN_MINUTE;
        long seconds = unsigned % NANOSECONDS_IN_MINUTE / NANOSECONDS_IN_SECOND;
        long fraction = unsigned % NANOSECONDS_IN_SECOND;
        switch (part) {
            case HOUR -> hours = whole;
            case MINUTE -> minutes = whole;
            case SECOND -> {
                if (written instanceof AnyDecimalValue fractional) {
                    seconds = (long) fractional.quantity();
                    fraction = (long) ((fractional.quantity() - seconds) * NANOSECONDS_IN_SECOND);
                } else {
                    seconds = whole;
                    fraction = 0;
                }
            }
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH);
        }
        return TimeValue.ofNanoseconds(hours * NANOSECONDS_IN_HOUR
                + minutes * NANOSECONDS_IN_MINUTE
                + seconds * NANOSECONDS_IN_SECOND + fraction);
    }

    private int findPartNamedBy(Value selector) {
        return switch (selector) {
            case AnyWordValue name when name.canonical().equals("hour") -> HOUR;
            case AnyWordValue name when name.canonical().equals("minute") -> MINUTE;
            case AnyWordValue name when name.canonical().equals("second") -> SECOND;
            case IntegerValue(long position) -> (int) position - 1;
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH);
        };
    }

    private long readWholeNumberIn(Value written) {
        return switch (written) {
            case IntegerValue(long magnitude) -> checkWithinThirtyTwoBits(magnitude, written);
            case AnyDecimalValue fractional -> checkWithinThirtyTwoBits(
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

    private long checkWithinThirtyTwoBits(long whole, Value written) {
        if (whole > Integer.MAX_VALUE || whole < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, written);
        }
        return whole;
    }
}
