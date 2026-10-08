package org.jebol.domain.value;

import org.jebol.domain.eval.Comparison;

public final class TimeActions {

    private final TimeValue span;

    public TimeActions(TimeValue span) {
        this.span = span;
    }

    public Value combinedWith(Value right, ArithmeticOperation operation) {
        if (right instanceof TimeValue other) {
            return againstAnotherTime(other, operation);
        }
        if (right instanceof MoneyValue rate) {
            return billedAt(rate, operation);
        }
        if (right instanceof PercentValue portion) {
            return scaledBy(portion, operation);
        }
        if (!(right instanceof IntegerValue) && !(right instanceof AnyDecimalValue)) {
            throw notRelatedToATime(operation);
        }
        if (operation.keepsTheSignOfTheDividend()) {
            return theRestOfTheNanoseconds(right, operation);
        }
        if (operation.scalesRatherThanShifts()) {
            return scaledByAPlainNumber(right, operation);
        }
        return addedInWholeNanosecondsTo(right, operation);
    }

    public Value takenBy(Value left, ArithmeticOperation operation) {
        boolean allowed = operation.isCommutative()
                || (operation.subtractsOneFromTheOther() && left instanceof IntegerValue);
        if (!allowed) {
            throw notRelatedToATime(operation);
        }
        if (operation.subtractsOneFromTheOther()) {
            return TimeValue.ofNanoseconds(withinWhatADurationHolds(
                    wholeNanosecondsOf(left) - span.nanoseconds()));
        }
        return combinedWith(left, operation);
    }

    private Value againstAnotherTime(TimeValue other, ArithmeticOperation operation) {
        if (operation.divides()) {
            operation.requireANonZeroDivisor(other.nanoseconds());
            return DecimalValue.of(
                    (double) span.nanoseconds() / (double) other.nanoseconds());
        }
        if (operation.multiplies()) {
            throw notRelatedToATime(operation);
        }
        return addedInWholeNanosecondsTo(other, operation);
    }

    private Value billedAt(MoneyValue rate, ArithmeticOperation operation) {
        Deci hours = new MoneyActions.HoursBilled(span).asDeci();
        if (operation.multiplies()) {
            return new MoneyValue(hours.times(rate.asDeci()));
        }
        if (operation.divides()) {
            return new MoneyValue(rate.asDeci().dividedBy(hours));
        }
        throw notRelatedToATime(operation);
    }

    private Value scaledBy(AnyDecimalValue portion, ArithmeticOperation operation) {
        if (!operation.multiplies()) {
            throw notRelatedToATime(operation);
        }
        return TimeValue.ofNanoseconds((long) (span.nanoseconds() * portion.quantity()));
    }

    private Value theRestOfTheNanoseconds(Value right, ArithmeticOperation operation) {
        if (!(right instanceof IntegerValue(long divisor))) {
            throw notRelatedToATime(operation);
        }
        operation.requireANonZeroDivisor(divisor);
        return TimeValue.ofNanoseconds(span.nanoseconds() % divisor);
    }

    private Value scaledByAPlainNumber(Value right, ArithmeticOperation operation) {
        double by = Comparison.asDouble(right);
        if (operation.divides()) {
            operation.requireANonZeroDivisor(by);
            return TimeValue.ofNanoseconds((long) (span.nanoseconds() / by));
        }
        return TimeValue.ofNanoseconds((long) (span.nanoseconds() * by));
    }

    private Value addedInWholeNanosecondsTo(Value right, ArithmeticOperation operation) {
        long ours = span.nanoseconds();
        long theirs = wholeNanosecondsOf(right);
        return TimeValue.ofNanoseconds(
                withinWhatADurationHolds(nanosecondsCombined(ours, theirs, operation)));
    }

    private static long nanosecondsCombined(
            long ours, long theirs, ArithmeticOperation operation) {

        if (operation.subtractsOneFromTheOther()) {
            return ours - theirs;
        }
        if (!operation.needsANonZeroDivisor()) {
            return ours + theirs;
        }
        operation.requireANonZeroDivisor(theirs);
        return operation.keepsTheSignOfTheDividend()
                ? ours % theirs
                : Math.floorMod(ours, theirs);
    }

    private static Raised notRelatedToATime(ArithmeticOperation operation) {
        return Raised.of(EvaluationFailure.NOT_RELATED,
                WordValue.of(operation.spelling()),
                DatatypeValue.of(Datatype.TIME));
    }

    public static long wholeNanosecondsOf(Value value) {
        if (value instanceof TimeValue(long nanoseconds)) {
            return nanoseconds;
        }
        if (value instanceof IntegerValue(long magnitude)) {
            return magnitude * TimeValue.NANOSECONDS_PER_SECOND;
        }
        return Math.round(Comparison.asDouble(value) * TimeValue.NANOSECONDS_PER_SECOND);
    }

    public static long withinWhatADurationHolds(long nanoseconds) {
        if (nanoseconds < -TimeValue.LONGEST || nanoseconds > TimeValue.LONGEST) {
            throw Raised.of(EvaluationFailure.TYPE_LIMIT, DatatypeValue.of(Datatype.TIME));
        }
        return nanoseconds;
    }
}
