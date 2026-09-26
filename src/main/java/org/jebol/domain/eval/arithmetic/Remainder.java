package org.jebol.domain.eval.arithmetic;

import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.Value;

import java.math.BigDecimal;

final class Remainder implements ArithmeticOperation {

    @Override
    public String spelling() {
        return "remainder";
    }

    @Override
    public Value onWholeNumbers(long left, long right) {
        Overflowing.refuseAZeroDivisor(right);
        return IntegerValue.of(left % right);
    }

    @Override
    public Value onFractions(double left, double right, boolean infinitiesAllowed) {
        Overflowing.refuseAZeroDivisor(right);
        return DecimalValue.of(left % right);
    }

    @Override
    public MoneyValue onAmounts(BigDecimal left, BigDecimal right) {
        Overflowing.refuseAZeroDivisor(right.doubleValue());
        return MoneyValue.of(left.remainder(right, MoneyValue.ARITHMETIC));
    }

    @Override
    public long onCodepoints(long codepoint, long other) {
        Overflowing.refuseAZeroDivisor(other);
        return codepoint % other;
    }

    @Override
    public long onOctets(long octet, double against, boolean fractional) {
        Overflowing.refuseAZeroOctetDivisor((long) against);
        return octet % (long) against;
    }

    @Override
    public boolean isCommutative() {
        return false;
    }

    @Override
    public boolean worksOnVectors() {
        return true;
    }

    @Override
    public long onWholeElements(long ours, long theirs) {
        return ours % theirs;
    }

    @Override
    public double onMeasuredElements(double ours, double theirs) {
        return ours % theirs;
    }

    @Override
    public boolean multiplies() {
        return false;
    }

    @Override
    public boolean divides() {
        return false;
    }

    @Override
    public boolean keepsTheSignOfTheDividend() {
        return true;
    }

    @Override
    public boolean subtractsOneFromTheOther() {
        return false;
    }

    @Override
    public boolean needsANonZeroDivisor() {
        return true;
    }
}
