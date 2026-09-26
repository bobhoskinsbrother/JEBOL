package org.jebol.domain.eval.arithmetic;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.Value;

import java.math.BigDecimal;

final class Modulo implements ArithmeticOperation {

    @Override
    public String spelling() {
        return "modulo";
    }

    @Override
    public Value onWholeNumbers(long left, long right) {
        Overflowing.refuseAZeroDivisor(right);
        long rest = left % right;
        return IntegerValue.of(rest < 0 ? rest + Math.abs(right) : rest);
    }

    @Override
    public Value onFractions(double left, double right, boolean infinitiesAllowed) {
        Overflowing.refuseAZeroDivisor(right);
        double rest = left % right;
        return DecimalValue.of(rest < 0 ? rest + Math.abs(right) : rest);
    }

    @Override
    public MoneyValue onAmounts(BigDecimal left, BigDecimal right) {
        Overflowing.refuseAZeroDivisor(right.doubleValue());
        BigDecimal rest = left.remainder(right, MoneyValue.ARITHMETIC);
        return MoneyValue.of(rest.signum() < 0 ? rest.add(right.abs()) : rest);
    }

    @Override
    public long onCodepoints(long codepoint, long other) {
        throw Raised.of(EvaluationFailure.CANNOT_USE,
                "cannot use that on a character");
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
        return false;
    }

    @Override
    public long onWholeElements(long ours, long theirs) {
        return Math.floorMod(ours, theirs);
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
        return false;
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
