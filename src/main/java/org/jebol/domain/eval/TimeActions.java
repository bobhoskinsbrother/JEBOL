package org.jebol.domain.eval;

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

/**
 * What a span of time does when an action is performed on it, which is what
 * {@code REBTYPE(Time)} answers in {@code t-time.c}.
 *
 * <p>A time decides for itself what it will meet, and the answers are not
 * symmetrical. Another time divides into it and answers a plain number,
 * because how many half-hours fit in an hour is a count rather than a
 * duration. A money multiplies it and answers money, reading the span as
 * hours. A percent scales it. A plain number scales it too, or is taken as
 * that many seconds when it is being added. Multiplying two times means
 * nothing and is refused.
 */
public final class TimeActions {

    private final TimeValue span;

    public TimeActions(TimeValue span) {
        this.span = span;
    }

    /** This time on the left of the operator, meeting whatever is on the right. */
    Value combinedWith(Value right, Arithmetic.Operation operation) {
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
        if (operation == Arithmetic.Operation.MULTIPLY
                || operation == Arithmetic.Operation.DIVIDE) {
            return scaledByAPlainNumber(right, operation);
        }
        return addedInWholeNanosecondsTo(right, operation);
    }

    /**
     * A plain number on the left of the operator taking this time.
     *
     * <p>Adding and multiplying turn round and read the same either way.
     * Subtracting does not -- a whole number of seconds less a span is a span,
     * but a fraction on the left is refused rather than rounded. Dividing a
     * number by a time, and both remainders, mean nothing.
     */
    Value takenBy(Value left, Arithmetic.Operation operation) {
        boolean allowed = switch (operation) {
            case ADD, MULTIPLY -> true;
            case SUBTRACT -> left instanceof IntegerValue;
            case DIVIDE, REMAINDER, MODULO -> false;
        };
        if (!allowed) {
            throw notRelatedToATime(operation);
        }
        if (operation == Arithmetic.Operation.SUBTRACT) {
            return TimeValue.ofNanoseconds(withinWhatADurationHolds(
                    wholeNanosecondsOf(left) - span.nanoseconds()));
        }
        return combinedWith(left, operation);
    }

    private Value againstAnotherTime(TimeValue other, Arithmetic.Operation operation) {
        if (operation == Arithmetic.Operation.DIVIDE) {
            Arithmetic.requireNonZero(other.nanoseconds());
            return DecimalValue.of(
                    (double) span.nanoseconds() / (double) other.nanoseconds());
        }
        if (operation == Arithmetic.Operation.MULTIPLY) {
            throw notRelatedToATime(operation);
        }
        return addedInWholeNanosecondsTo(other, operation);
    }

    private Value billedAt(MoneyValue rate, Arithmetic.Operation operation) {
        BigDecimal hours = BigDecimal.valueOf(
                (double) span.nanoseconds() / (double) TimeValue.NANOSECONDS_PER_HOUR);
        return switch (operation) {
            case MULTIPLY -> MoneyActions.amountCombined(hours, rate.amount(), operation);
            case DIVIDE -> MoneyActions.amountCombined(rate.amount(), hours, operation);
            default -> throw notRelatedToATime(operation);
        };
    }

    private Value scaledBy(DecimalValue portion, Arithmetic.Operation operation) {
        if (operation != Arithmetic.Operation.MULTIPLY) {
            throw notRelatedToATime(operation);
        }
        return TimeValue.ofNanoseconds((long) (span.nanoseconds() * portion.quantity()));
    }

    private Value scaledByAPlainNumber(Value right, Arithmetic.Operation operation) {
        double by = Comparison.asDouble(right);
        if (operation == Arithmetic.Operation.DIVIDE) {
            Arithmetic.requireNonZero(by);
            return TimeValue.ofNanoseconds((long) (span.nanoseconds() / by));
        }
        return TimeValue.ofNanoseconds((long) (span.nanoseconds() * by));
    }

    private Value addedInWholeNanosecondsTo(Value right, Arithmetic.Operation operation) {
        long ours = span.nanoseconds();
        long theirs = wholeNanosecondsOf(right);
        return TimeValue.ofNanoseconds(withinWhatADurationHolds(switch (operation) {
            case ADD -> ours + theirs;
            case SUBTRACT -> ours - theirs;
            default -> {
                Arithmetic.requireNonZero(theirs);
                yield operation == Arithmetic.Operation.REMAINDER
                        ? ours % theirs
                        : Math.floorMod(ours, theirs);
            }
        }));
    }

    private static Raised notRelatedToATime(Arithmetic.Operation operation) {
        return Raised.of(EvaluationFailure.NOT_RELATED,
                WordValue.of(operation.name().toLowerCase(Locale.ROOT)),
                DatatypeValue.of(Datatype.TIME));
    }

    /**
     * What a value is worth in whole nanoseconds when a time is adding it: a
     * time is itself, and anything else is read as that many seconds.
     */
    static long wholeNanosecondsOf(Value value) {
        if (value instanceof TimeValue time) {
            return time.nanoseconds();
        }
        if (value instanceof IntegerValue seconds) {
            return seconds.magnitude() * TimeValue.NANOSECONDS_PER_SECOND;
        }
        return Math.round(Comparison.asDouble(value) * TimeValue.NANOSECONDS_PER_SECOND);
    }

    /**
     * The longest span a time holds, which is a whole number of hours rather
     * than of nanoseconds. Running past it is a type limit rather than a
     * wrapped-round answer.
     */
    static long withinWhatADurationHolds(long nanoseconds) {
        if (nanoseconds < -TimeValue.LONGEST || nanoseconds > TimeValue.LONGEST) {
            throw Raised.of(EvaluationFailure.TYPE_LIMIT, DatatypeValue.of(Datatype.TIME));
        }
        return nanoseconds;
    }
}
