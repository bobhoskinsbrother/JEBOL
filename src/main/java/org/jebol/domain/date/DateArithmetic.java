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

/**
 * What a date does when an action is performed on it, which is what
 * {@code REBTYPE(Date)} answers in {@code t-date.c}.
 *
 * <p>A date decides for itself what it will meet, and only two things ever
 * come of it. Another date subtracted gives the whole days between them, and
 * nothing else two dates can be asked means anything. Otherwise a date is
 * moved: a whole number moves it in days, and anything else moves it on the
 * clock, carrying into the next day or back into the last one when the time
 * runs past either end.
 */
public final class DateArithmetic {

    private final DateValue moment;

    public DateArithmetic(DateValue moment) {
        this.moment = moment;
    }

    /** ADD and SUBTRACT, which is all a date's arithmetic amounts to. */
    public Value combinedWith(Value right, Arithmetic.Operation operation) {
        if (right instanceof DateValue to) {
            return daysSince(to, operation);
        }
        return movedBy(right, operation);
    }

    /**
     * A value on the left of the operator with this date on the right.
     *
     * <p>Only adding reads that way round: three days from now is a date, but
     * three days minus now is not anything, and REBOL says so by naming the
     * operator and the left-hand datatype rather than the date.
     */
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
        return IntegerValue.of(dayNumberOf(moment) - dayNumberOf(to));
    }

    private Value movedBy(Value span, Arithmetic.Operation operation) {
        int sign = operation == Arithmetic.Operation.SUBTRACT ? -1 : 1;
        return span.datatype() == Datatype.INTEGER
                ? movedByDays(sign * (long) Comparison.asDouble(span))
                : movedByTheClock(sign * clockShiftOf(span));
    }

    private DateValue movedByDays(long days) {
        LocalDate shifted = LocalDate.ofEpochDay(dayNumberOf(moment) + days);
        return new DateValue(shifted.getYear(), shifted.getMonthValue(),
                shifted.getDayOfMonth(), moment.timeOfDay(), moment.zoneMinutes());
    }

    private DateValue movedByTheClock(long nanoseconds) {
        long shifted = moment.timeOfDay().map(TimeValue::nanoseconds).orElse(0L)
                + nanoseconds;
        LocalDate day = LocalDate.ofEpochDay(dayNumberOf(moment)
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

    /** Days since the epoch, which is how two dates are compared and subtracted. */
    public static long dayNumberOf(DateValue date) {
        return LocalDate.of(date.year(), date.month(), date.day()).toEpochDay();
    }
}
