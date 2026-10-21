package org.jebol.domain.value;

import org.jebol.domain.eval.Comparison;

public final class TimeActions {

    private final TimeValue span;

    public TimeActions(TimeValue span) {
        this.span = span;
    }

    public Value combineWith(Value right, ArithmeticOperation operation) {
        if (right instanceof TimeValue other) {
            return againstAnotherTime(other, operation);
        }
        if (right instanceof MoneyValue rate) {
            return billAt(rate, operation);
        }
        if (right instanceof PercentValue portion) {
            return scaleBy(portion, operation);
        }
        if (!(right instanceof IntegerValue) && !(right instanceof AnyDecimalValue)) {
            throw refuseAsNotRelatedToTime(operation);
        }
        if (operation.keepsDividendSign()) {
            return nanosecondsRemainder(right, operation);
        }
        if (operation.scalesRatherThanShifts()) {
            return scaleByPlainNumber(right, operation);
        }
        return addInWholeNanosecondsTo(right, operation);
    }

    public Value combineFromLeft(Value left, ArithmeticOperation operation) {
        boolean allowed = operation.isCommutative()
                || (operation.canSubtractOneFromOther() && left instanceof IntegerValue);
        if (!allowed) {
            throw refuseAsNotRelatedToTime(operation);
        }
        if (operation.canSubtractOneFromOther()) {
            return TimeValue.ofNanoseconds(withinWhatADurationHolds(
                    wholeNanoseconds(left) - span.nanoseconds()));
        }
        return combineWith(left, operation);
    }

    private Value againstAnotherTime(TimeValue other, ArithmeticOperation operation) {
        if (operation.divides()) {
            operation.requireNonZeroDivisor(other.nanoseconds());
            return DecimalValue.of(
                    (double) span.nanoseconds() / (double) other.nanoseconds());
        }
        if (operation.multiplies()) {
            throw refuseAsNotRelatedToTime(operation);
        }
        return addInWholeNanosecondsTo(other, operation);
    }

    private Value billAt(MoneyValue rate, ArithmeticOperation operation) {
        Deci hours = new MoneyActions.HoursBilled(span).asDeci();
        if (operation.multiplies()) {
            return new MoneyValue(hours.times(rate.asDeci()));
        }
        if (operation.divides()) {
            return new MoneyValue(rate.asDeci().divideBy(hours));
        }
        throw refuseAsNotRelatedToTime(operation);
    }

    private Value scaleBy(AnyDecimalValue portion, ArithmeticOperation operation) {
        if (!operation.multiplies()) {
            throw refuseAsNotRelatedToTime(operation);
        }
        return TimeValue.ofNanoseconds((long) (span.nanoseconds() * portion.quantity()));
    }

    private Value nanosecondsRemainder(Value right, ArithmeticOperation operation) {
        if (!(right instanceof IntegerValue(long divisor))) {
            throw refuseAsNotRelatedToTime(operation);
        }
        operation.requireNonZeroDivisor(divisor);
        return TimeValue.ofNanoseconds(span.nanoseconds() % divisor);
    }

    private Value scaleByPlainNumber(Value right, ArithmeticOperation operation) {
        double by = Comparison.asDouble(right);
        if (operation.divides()) {
            operation.requireNonZeroDivisor(by);
            return TimeValue.ofNanoseconds((long) (span.nanoseconds() / by));
        }
        return TimeValue.ofNanoseconds((long) (span.nanoseconds() * by));
    }

    private Value addInWholeNanosecondsTo(Value right, ArithmeticOperation operation) {
        long ours = span.nanoseconds();
        long theirs = wholeNanoseconds(right);
        return TimeValue.ofNanoseconds(
                withinWhatADurationHolds(combineTheNanoseconds(ours, theirs, operation)));
    }

    private static long combineTheNanoseconds(
            long ours, long theirs, ArithmeticOperation operation) {

        if (operation.canSubtractOneFromOther()) {
            return ours - theirs;
        }
        if (!operation.needsNonZeroDivisor()) {
            return ours + theirs;
        }
        operation.requireNonZeroDivisor(theirs);
        return operation.keepsDividendSign()
                ? ours % theirs
                : Math.floorMod(ours, theirs);
    }

    private static Raised refuseAsNotRelatedToTime(ArithmeticOperation operation) {
        return Raised.of(EvaluationFailure.NOT_RELATED,
                WordValue.of(operation.spelling()),
                TimeValue.TYPE);
    }

    public static long wholeNanoseconds(Value value) {
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
            throw Raised.of(EvaluationFailure.TYPE_LIMIT, TimeValue.TYPE);
        }
        return nanoseconds;
    }
}
