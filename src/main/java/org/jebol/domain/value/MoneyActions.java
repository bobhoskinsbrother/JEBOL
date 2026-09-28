package org.jebol.domain.value;

import java.math.BigDecimal;

public final class MoneyActions {

    private final MoneyValue amount;

    public MoneyActions(MoneyValue amount) {
        this.amount = amount;
    }

    public Value combinedWith(Value other, ArithmeticOperation operation) {
        MoneyValue answered = withinTheDeciRange(
                amountCombined(amount.amount(), widenedToMeet(other, operation), operation));
        return answered.amount().signum() != 0
                ? answered
                : answered.signed(theSignAZeroKeeps(other, operation));
    }

    private boolean theSignAZeroKeeps(Value other, ArithmeticOperation operation) {
        if (!operation.scalesRatherThanShifts()) {
            return amount.negative();
        }
        return amount.negative() != isNegative(other);
    }

    private static boolean isNegative(Value other) {
        return other instanceof MoneyValue money
                ? money.negative()
                : asBigDecimal(other).signum() < 0;
    }

    private static BigDecimal widenedToMeet(Value other, ArithmeticOperation operation) {
        if (other instanceof TimeValue(long nanoseconds)) {
            if (!operation.multiplies()) {
                throw Raised.of(EvaluationFailure.NOT_RELATED,
                        "only multiplication takes a time on the right of a money");
            }
            return BigDecimal.valueOf(
                    (double) nanoseconds / TimeValue.NANOSECONDS_PER_HOUR);
        }
        if (other instanceof MoneyValue
                || other instanceof IntegerValue
                || other instanceof DecimalValue) {
            return asBigDecimal(other);
        }
        throw Raised.of(EvaluationFailure.NOT_RELATED,
                other.datatype().literalSpelling() + " does not go with money arithmetic");
    }

    public static MoneyValue amountCombined(
            BigDecimal left, BigDecimal right, ArithmeticOperation operation) {

        return operation.onAmounts(left, right);
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

    private static BigDecimal withoutATrailingZero(BigDecimal quantity) {
        BigDecimal stripped = quantity.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    public static BigDecimal asBigDecimal(Value value) {
        return switch (value) {
            case MoneyValue money -> money.amount();
            case IntegerValue integer -> BigDecimal.valueOf(integer.magnitude());
            case DecimalValue decimal -> withoutATrailingZero(
                    BigDecimal.valueOf(decimal.quantity()));
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
    }

    public static MoneyValue withinTheDeciRange(MoneyValue built) {
        if (!built.isWithinTheDeciRange()) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "a money holds twenty-six digits and a power of ten from -128 to 127");
        }
        return built;
    }
}
