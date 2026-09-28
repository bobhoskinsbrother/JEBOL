package org.jebol.domain.value;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

public interface ArithmeticOperation extends ValueOperation {

    @Override
    default boolean isBitwise() {
        return false;
    }

    Value onWholeNumbers(long left, long right);

    Value onFractions(double left, double right, boolean infinitiesAllowed);

    MoneyValue onAmounts(BigDecimal left, BigDecimal right);

    long onCodepoints(long codepoint, long other);

    long onOctets(long octet, double against, boolean fractional);

    boolean isCommutative();

    boolean multiplies();

    boolean divides();

    boolean keepsTheSignOfTheDividend();

    default boolean scalesRatherThanShifts() {
        return multiplies() || divides();
    }

    boolean subtractsOneFromTheOther();

    boolean needsANonZeroDivisor();

    default Value onFractions(double left, double right) {
        return onFractions(left, right, false);
    }

    default void requireANonZeroDivisor(double divisor) {
        if (divisor == 0.0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

    default int signWhenMoving() {
        return subtractsOneFromTheOther() ? -1 : 1;
    }

    Map<String, ArithmeticOperation> BY_SPELLING = Stream.of(
                    new Add(), new Subtract(), new Multiply(), new Divide(),
                    new Remainder(), new Modulo())
            .collect(toUnmodifiableMap(
                    ArithmeticOperation::spelling, identity()));

    static Optional<ArithmeticOperation> named(String spelling) {
        return Optional.ofNullable(BY_SPELLING.get(spelling));
    }

    static ArithmeticOperation findOperation(String spelling) {
        return named(spelling).orElseThrow();
    }
}
