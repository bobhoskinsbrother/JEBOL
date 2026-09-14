package org.jebol.domain.eval;

import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

import java.math.BigDecimal;

public final class MoneyActions {

    private final MoneyValue amount;

    public MoneyActions(MoneyValue amount) {
        this.amount = amount;
    }

    Value combinedWith(Value other, Arithmetic.Operation operation) {
        return withinTheDeciRange(
                amountCombined(amount.amount(), widenedToMeet(other, operation), operation));
    }

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

    public Value heldBetween(MoneyValue lowest, MoneyValue highest) {
        if (amount.amount().compareTo(lowest.amount()) <= 0) {
            return lowest;
        }
        if (highest.amount().compareTo(amount.amount()) <= 0) {
            return highest;
        }
        return amount;
    }

    public boolean isNoAmountAtAll() {
        return amount.amount().signum() == 0;
    }

    static BigDecimal asBigDecimal(Value value) {
        return switch (value) {
            case MoneyValue money -> money.amount();
            case IntegerValue integer -> BigDecimal.valueOf(integer.magnitude());
            case DecimalValue decimal -> BigDecimal.valueOf(decimal.quantity());
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
    }

    static MoneyValue withinTheDeciRange(MoneyValue built) {
        if (!built.isWithinTheDeciRange()) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "a money holds twenty-six digits and a power of ten from -128 to 127");
        }
        return built;
    }
}
