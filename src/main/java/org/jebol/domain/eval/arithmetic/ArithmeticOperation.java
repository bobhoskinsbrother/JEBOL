package org.jebol.domain.eval.arithmetic;

import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.Value;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * One of REBOL's arithmetic operations, asked to combine a pair.
 *
 * <p>The same operation meets five widths of number, and the datatype decides
 * which of them to hand it: whole numbers, fractions, the BigDecimal an amount
 * of money carries, the codepoint behind a character, and one octet of a
 * tuple. It answers three questions besides, so that no caller has to test
 * which operation it was handed.
 */
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

    /** Whether the pair may be taken either way around and answer the same. */
    boolean isCommutative();

    /** Whether this multiplies, which several datatypes read to decide a shape. */
    boolean multiplies();

    /** Whether this divides, which turns two durations into a plain number. */
    boolean divides();

    /** What separates a remainder from a modulo: whose sign the rest follows. */
    boolean keepsTheSignOfTheDividend();

    /** Whether this makes a quantity larger or smaller rather than moving it. */
    default boolean scalesRatherThanShifts() {
        return multiplies() || divides();
    }

    /** Whether this takes the right from the left, which several datatypes read. */
    boolean subtractsOneFromTheOther();

    /** Whether a zero on the right is a zero-divide rather than an ordinary number. */
    boolean needsANonZeroDivisor();

    default Value onFractions(double left, double right) {
        return onFractions(left, right, false);
    }

    /** Which way a date moves when this operation is applied to a span. */
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
