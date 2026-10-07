package org.jebol.domain.value;

public interface ArithmeticOperation extends ValueOperation {

    @Override
    default boolean isBitwise() {
        return false;
    }

    Value onWholeNumbers(long left, long right);

    Value onFractions(double left, double right, boolean infinitiesAllowed);

    Deci onAmounts(Deci left, Deci right);

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

}
