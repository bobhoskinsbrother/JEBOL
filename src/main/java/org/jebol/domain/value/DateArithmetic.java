package org.jebol.domain.value;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;

public final class DateArithmetic {

    private final DateValue moment;

    public DateArithmetic(DateValue moment) {
        this.moment = moment;
    }

    public Value combineWith(Value right, ArithmeticOperation operation) {
        if (right instanceof DateValue to) {
            return countDaysSince(to, operation);
        }
        return moveBy(right, operation);
    }

    public Value takeFrom(Value left, ArithmeticOperation operation) {
        if (operation.canSubtractOneFromOther()) {
            throw Raised.of(EvaluationFailure.NOT_RELATED,
                    WordValue.of(operation.spelling() + ":"),
                    left.datatype());
        }
        return moveBy(left, operation);
    }

    private Value countDaysSince(DateValue to, ArithmeticOperation operation) {
        if (!operation.canSubtractOneFromOther()) {
            throw Raised.cannotUse(moment, "date arithmetic");
        }
        return IntegerValue.of(moment.dayNumber() - to.dayNumber());
    }

    private Value moveBy(Value span, ArithmeticOperation operation) {
        refuseSpanDateCannotMoveBy(span, operation);
        int sign = operation.signWhenMoving();
        return span instanceof IntegerValue
                ? moveByDays(sign * (long) quantityFrom(span))
                : moveByClock(sign * clockShiftNanoseconds(span));
    }

    private void refuseSpanDateCannotMoveBy(Value span, ArithmeticOperation operation) {
        boolean movesDate = operation.isCommutative()
                ? !operation.multiplies()
                : operation.canSubtractOneFromOther();
        if (!movesDate
                || !(span instanceof IntegerValue
                        || span instanceof DecimalValue
                        || span instanceof TimeValue)) {
            throw Raised.cannotUse(moment, "date arithmetic");
        }
    }

    private DateValue moveByDays(long days) {
        try {
            LocalDate shifted = LocalDate.ofEpochDay(
                    Math.addExact(moment.dayNumber(), days));
            requireSupportedYear(shifted);
            return new DateValue(shifted.getYear(), shifted.getMonthValue(),
                    shifted.getDayOfMonth(), moment.timeOfDay(), moment.zoneMinutes());
        } catch (ArithmeticException | DateTimeException outsideSupportedYears) {
            throw typeLimit();
        }
    }

    private DateValue moveByClock(long nanoseconds) {
        try {
            long shifted = Math.addExact(
                    moment.timeOfDay().map(TimeValue::nanoseconds).orElse(0L), nanoseconds);
            LocalDate day = LocalDate.ofEpochDay(Math.addExact(moment.dayNumber(),
                    Math.floorDiv(shifted, TimeValue.NANOSECONDS_PER_DAY)));
            requireSupportedYear(day);
            return new DateValue(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                    Optional.of(TimeValue.ofNanoseconds(
                            Math.floorMod(shifted, TimeValue.NANOSECONDS_PER_DAY))),
                    moment.zoneMinutes());
        } catch (ArithmeticException | DateTimeException outsideSupportedYears) {
            throw typeLimit();
        }
    }

    private static void requireSupportedYear(LocalDate day) {
        if (day.getYear() < DateValue.EARLIEST_YEAR
                || day.getYear() > DateValue.LATEST_YEAR) {
            throw typeLimit();
        }
    }

    private static Raised typeLimit() {
        return Raised.of(EvaluationFailure.TYPE_LIMIT, DateValue.TYPE);
    }

    private static double quantityFrom(Value span) {
        return switch (span) {
            case IntegerValue(long magnitude) -> magnitude;
            case AnyDecimalValue fraction -> fraction.quantity();
            default -> throw Raised.cannotUse(span, "date arithmetic");
        };
    }

    private static long clockShiftNanoseconds(Value span) {
        return span instanceof TimeValue(long nanoseconds)
                ? nanoseconds
                : (long) (quantityFrom(span) * TimeValue.NANOSECONDS_PER_DAY);
    }

}
