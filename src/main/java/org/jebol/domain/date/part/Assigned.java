package org.jebol.domain.date.part;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

record Assigned(Value value, AnyWordValue part) {

    boolean isNothing() {
        return value instanceof NoneValue;
    }

    int asWholeNumber() {
        return switch (value) {
            case IntegerValue number -> Math.toIntExact(number.magnitude());
            case AnyDecimalValue number -> (int) number.quantity();
            case NoneValue _ -> 0;
            default -> throw refusal();
        };
    }

    long asSecondsInNanoseconds() {
        return value instanceof AnyDecimalValue fraction
                ? (long) (fraction.quantity() * TimeValue.NANOSECONDS_PER_SECOND)
                : (long) asWholeNumber() * TimeValue.NANOSECONDS_PER_SECOND;
    }

    TimeValue asClock() {
        return TimeValue.ofNanoseconds(asSecondsInNanoseconds());
    }

    DateValue asDate() {
        if (value instanceof DateValue other) {
            return other;
        }
        throw refusal();
    }

    double asJulianDayCount() {
        if (value instanceof AnyDecimalValue counted) {
            return counted.quantity();
        }
        throw refusal();
    }

    int zoneOffsetInMinutes() {
        return withinReach(switch (value) {
            case IntegerValue bareNumberMeansHours ->
                    Math.toIntExact(bareNumberMeansHours.magnitude()) * 60;
            case AnyDecimalValue bareNumberMeansHours ->
                    (int) bareNumberMeansHours.quantity() * 60;
            case TimeValue clock -> (int) (clock.nanoseconds()
                    / (60L * TimeValue.NANOSECONDS_PER_SECOND));
            default -> throw refusal();
        });
    }

    private static final int MOST_ZONE_MAY_BE = 15 * 60 + 45;

    private static int withinReach(int offsetMinutes) {
        if (Math.abs(offsetMinutes) > MOST_ZONE_MAY_BE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    IntegerValue.of(offsetMinutes));
        }
        return offsetMinutes;
    }

    Raised refusal() {
        return Raised.of(EvaluationFailure.BAD_FIELD_SET, part, value.datatype());
    }
}
