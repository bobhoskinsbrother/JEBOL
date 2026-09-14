package org.jebol.domain.date;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

public final class DateArithmetic {

    private final DateValue moment;

    public DateArithmetic(DateValue moment) {
        this.moment = moment;
    }

    public Value combinedWith(Value right, Arithmetic.Operation operation) {
        if (right instanceof DateValue to) {
            return daysSince(to, operation);
        }
        return movedBy(right, operation);
    }

    public Value takenBy(Value left, Arithmetic.Operation operation) {
        if (operation == Arithmetic.Operation.SUBTRACT) {
            throw Raised.of(EvaluationFailure.NOT_RELATED,
                    WordValue.of(operation.name().toLowerCase(Locale.ROOT) + ":"),
                    DatatypeValue.of(left.datatype()));
        }
        return movedBy(left, operation);
    }

    private Value daysSince(DateValue to, Arithmetic.Operation operation) {
        if (operation != Arithmetic.Operation.SUBTRACT) {
            throw Raised.cannotUse(moment, "date arithmetic");
        }
        return IntegerValue.of(moment.dayNumber() - to.dayNumber());
    }

    private Value movedBy(Value span, Arithmetic.Operation operation) {
        int sign = operation == Arithmetic.Operation.SUBTRACT ? -1 : 1;
        return span.datatype() == Datatype.INTEGER
                ? movedByDays(sign * (long) Comparison.asDouble(span))
                : movedByTheClock(sign * clockShiftOf(span));
    }

    private DateValue movedByDays(long days) {
        LocalDate shifted = LocalDate.ofEpochDay(moment.dayNumber() + days);
        return new DateValue(shifted.getYear(), shifted.getMonthValue(),
                shifted.getDayOfMonth(), moment.timeOfDay(), moment.zoneMinutes());
    }

    private DateValue movedByTheClock(long nanoseconds) {
        long shifted = moment.timeOfDay().map(TimeValue::nanoseconds).orElse(0L)
                + nanoseconds;
        LocalDate day = LocalDate.ofEpochDay(moment.dayNumber()
                + Math.floorDiv(shifted, TimeValue.NANOSECONDS_PER_DAY));
        return new DateValue(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(
                        Math.floorMod(shifted, TimeValue.NANOSECONDS_PER_DAY))),
                moment.zoneMinutes());
    }

    private static long clockShiftOf(Value span) {
        return span instanceof TimeValue duration
                ? duration.nanoseconds()
                : (long) (Comparison.asDouble(span) * TimeValue.NANOSECONDS_PER_DAY);
    }

}
