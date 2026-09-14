package org.jebol.domain.date.part;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

record Assigned(Value value) {

    boolean isNothing() {
        return value instanceof NoneValue;
    }

    int asWholeNumber() {
        return switch (value) {
            case IntegerValue number -> Math.toIntExact(number.magnitude());
            case DecimalValue number -> (int) number.quantity();
            case NoneValue _ -> 0;
            default -> throw refusal();
        };
    }

    long asSecondsInNanoseconds() {
        return value instanceof DecimalValue fraction
                ? (long) (fraction.quantity() * TimeValue.NANOSECONDS_PER_SECOND)
                : (long) asWholeNumber() * TimeValue.NANOSECONDS_PER_SECOND;
    }

    TimeValue asAClock() {
        return TimeValue.ofNanoseconds(asSecondsInNanoseconds());
    }

    DateValue asADate() {
        if (value instanceof DateValue other) {
            return other;
        }
        throw refusal();
    }

    double asAJulianDayCount() {
        if (value instanceof DecimalValue counted) {
            return counted.quantity();
        }
        throw refusal();
    }

    int asAZoneOffsetInMinutes() {
        return withinReach(switch (value) {
            case IntegerValue aBareNumberMeansHours ->
                    Math.toIntExact(aBareNumberMeansHours.magnitude()) * 60;
            case DecimalValue aBareNumberMeansHours ->
                    (int) aBareNumberMeansHours.quantity() * 60;
            case TimeValue clock -> (int) (clock.nanoseconds()
                    / (60L * TimeValue.NANOSECONDS_PER_SECOND));
            default -> throw Raised.of(
                    EvaluationFailure.BAD_FIELD_SET, Molder.mold(value));
        });
    }

    private static final int MOST_A_ZONE_MAY_BE = 15 * 60 + 45;

    private static int withinReach(int offsetMinutes) {
        if (Math.abs(offsetMinutes) > MOST_A_ZONE_MAY_BE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    IntegerValue.of(offsetMinutes));
        }
        return offsetMinutes;
    }

    Raised refusal() {
        return Raised.of(EvaluationFailure.BAD_FIELD_SET, value);
    }
}
