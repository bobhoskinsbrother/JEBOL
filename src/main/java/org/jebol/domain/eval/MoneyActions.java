package org.jebol.domain.eval;

import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

import java.math.BigDecimal;

/**
 * What an amount of money does when an action is performed on it, which is
 * what {@code REBTYPE(Money)} answers in {@code t-money.c}.
 *
 * <p>Money decides for itself what it will meet. A number widens to an amount,
 * a time on the right of a multiplication is read as hours because that is
 * what billing an hourly rate means, and everything else is refused -- the
 * same judgement `t-money.c` makes inside its own arm rather than having it
 * made for it outside.
 *
 * <p>Every answer is checked against the range a money holds before it leaves,
 * because the twenty-six digits and the power of ten are what separates
 * {@code money!} from a decimal and an answer outside them is not one.
 */
public final class MoneyActions {

    private final MoneyValue amount;

    public MoneyActions(MoneyValue amount) {
        this.amount = amount;
    }

    /** ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER and MODULO against anything. */
    Value combinedWith(Value other, Arithmetic.Operation operation) {
        return withinTheDeciRange(
                amountCombined(amount.amount(), widenedToMeet(other, operation), operation));
    }

    /**
     * What money will take on the right, which is money's own business.
     *
     * <p>A time is only ever multiplied, and reads as a count of hours: an
     * hourly rate times two hours is twice the rate. Numbers widen. Anything
     * else has no meaning beside an amount and is refused rather than guessed.
     */
    private static BigDecimal widenedToMeet(Value other, Arithmetic.Operation operation) {
        if (other instanceof TimeValue span) {
            if (operation != Arithmetic.Operation.MULTIPLY) {
                throw Raised.of(EvaluationFailure.NOT_RELATED,
                        "only multiplication takes a time on the right of a money");
            }
            return BigDecimal.valueOf(
                    (double) span.nanoseconds() / TimeValue.NANOSECONDS_PER_HOUR);
        }
        if (other instanceof MoneyValue
                || other instanceof IntegerValue
                || other instanceof DecimalValue) {
            return asBigDecimal(other);
        }
        throw Raised.of(EvaluationFailure.NOT_RELATED,
                other.datatype().literalSpelling() + " does not go with money arithmetic");
    }

    static MoneyValue amountCombined(
            BigDecimal left, BigDecimal right, Arithmetic.Operation operation) {

        return switch (operation) {
            case ADD -> MoneyValue.of(left.add(right));
            case SUBTRACT -> MoneyValue.of(left.subtract(right));
            case MULTIPLY -> MoneyValue.of(left.multiply(right, MoneyValue.ARITHMETIC));
            case DIVIDE -> {
                Arithmetic.requireNonZero(right.doubleValue());
                yield MoneyValue.of(left.divide(right, MoneyValue.ARITHMETIC));
            }
            case REMAINDER -> {
                Arithmetic.requireNonZero(right.doubleValue());
                yield MoneyValue.of(left.remainder(right, MoneyValue.ARITHMETIC));
            }
            case MODULO -> {
                Arithmetic.requireNonZero(right.doubleValue());
                BigDecimal rest = left.remainder(right, MoneyValue.ARITHMETIC);
                yield MoneyValue.of(rest.signum() < 0 ? rest.add(right.abs()) : rest);
            }
        };
    }

    /** CLAMP, which answers whichever end of the range the amount has run past. */
    public Value heldBetween(MoneyValue lowest, MoneyValue highest) {
        if (amount.amount().compareTo(lowest.amount()) <= 0) {
            return lowest;
        }
        if (highest.amount().compareTo(amount.amount()) <= 0) {
            return highest;
        }
        return amount;
    }

    /** ZERO?, which a money answers on its amount rather than its currency. */
    public boolean isNoAmountAtAll() {
        return amount.amount().signum() == 0;
    }

    /**
     * An amount as a plain number, which several other datatypes need when
     * money turns up on their right and they are willing to take it.
     */
    static BigDecimal asBigDecimal(Value value) {
        return switch (value) {
            case MoneyValue money -> money.amount();
            case IntegerValue integer -> BigDecimal.valueOf(integer.magnitude());
            case DecimalValue decimal -> BigDecimal.valueOf(decimal.quantity());
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
    }

    /**
     * The range a money holds, which is twenty-six digits and a power of ten
     * from -128 to 127. An answer outside it is an overflow rather than a
     * money, and saying so here means no arm has to remember to ask.
     */
    static MoneyValue withinTheDeciRange(MoneyValue built) {
        if (!built.isWithinTheDeciRange()) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "a money holds twenty-six digits and a power of ten from -128 to 127");
        }
        return built;
    }
}
