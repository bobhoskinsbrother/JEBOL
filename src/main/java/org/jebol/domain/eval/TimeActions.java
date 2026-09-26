package org.jebol.domain.eval;

import org.jebol.domain.eval.arithmetic.ArithmeticOperation;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.math.BigDecimal;
import java.util.Locale;

public final class TimeActions {

    private final TimeValue span;

    public TimeActions(TimeValue span) {
        this.span = span;
    }

    Value combinedWith(Value right, ArithmeticOperation operation) {
        if (right instanceof TimeValue other) {
            return againstAnotherTime(other, operation);
        }
        if (right instanceof MoneyValue rate) {
            return billedAt(rate, operation);
        }
        if (right instanceof DecimalValue portion
                && portion.datatype() == Datatype.PERCENT) {
            return scaledBy(portion, operation);
        }
        if (!(right instanceof IntegerValue) && !(right instanceof DecimalValue)) {
            throw notRelatedToATime(operation);
        }
        if (operation.scalesRatherThanShifts()) {
            return scaledByAPlainNumber(right, operation);
        }
        return addedInWholeNanosecondsTo(right, operation);
    }

    Value takenBy(Value left, ArithmeticOperation operation) {
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
            Arithmetic.requireNonZero(other.nanoseconds());
            return DecimalValue.of(
                    (double) span.nanoseconds() / (double) other.nanoseconds());
        }
        if (operation.multiplies()) {
            throw notRelatedToATime(operation);
        }
        return addedInWholeNanosecondsTo(other, operation);
    }

    private Value billedAt(MoneyValue rate, ArithmeticOperation operation) {
        BigDecimal hours = BigDecimal.valueOf(
                (double) span.nanoseconds() / (double) TimeValue.NANOSECONDS_PER_HOUR);
        if (operation.multiplies()) {
            return MoneyActions.amountCombined(hours, rate.amount(), operation);
        }
        if (operation.divides()) {
            return MoneyActions.amountCombined(rate.amount(), hours, operation);
        }
        throw notRelatedToATime(operation);
    }

    private Value scaledBy(DecimalValue portion, ArithmeticOperation operation) {
        if (!operation.multiplies()) {
            throw notRelatedToATime(operation);
        }
        return TimeValue.ofNanoseconds((long) (span.nanoseconds() * portion.quantity()));
    }

    private Value scaledByAPlainNumber(Value right, ArithmeticOperation operation) {
        double by = Comparison.asDouble(right);
        if (operation.divides()) {
            Arithmetic.requireNonZero(by);
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
        Arithmetic.requireNonZero(theirs);
        return operation.keepsTheSignOfTheDividend()
                ? ours % theirs
                : Math.floorMod(ours, theirs);
    }

    private static Raised notRelatedToATime(ArithmeticOperation operation) {
        return Raised.of(EvaluationFailure.NOT_RELATED,
                WordValue.of(operation.spelling()),
                DatatypeValue.of(Datatype.TIME));
    }

    static long wholeNanosecondsOf(Value value) {
        if (value instanceof TimeValue time) {
            return time.nanoseconds();
        }
        if (value instanceof IntegerValue seconds) {
            return seconds.magnitude() * TimeValue.NANOSECONDS_PER_SECOND;
        }
        return Math.round(Comparison.asDouble(value) * TimeValue.NANOSECONDS_PER_SECOND);
    }

    static long withinWhatADurationHolds(long nanoseconds) {
        if (nanoseconds < -TimeValue.LONGEST || nanoseconds > TimeValue.LONGEST) {
            throw Raised.of(EvaluationFailure.TYPE_LIMIT, DatatypeValue.of(Datatype.TIME));
        }
        return nanoseconds;
    }
}
