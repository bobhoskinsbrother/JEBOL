package org.jebol.domain.eval.arithmetic;

import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.Value;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

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

    default int signWhenMoving() {
        return subtractsOneFromTheOther() ? -1 : 1;
    }

    Map<String, ArithmeticOperation> BY_SPELLING = TheArithmeticOperations.bySpelling();

    static Optional<ArithmeticOperation> named(String spelling) {
        return Optional.ofNullable(BY_SPELLING.get(spelling));
    }

    static ArithmeticOperation theOneCalled(String spelling) {
        return named(spelling).orElseThrow();
    }
}
