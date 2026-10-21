package org.jebol.domain.value;

public final class Remainder implements ArithmeticOperation {

    @Override
    public String spelling() {
        return "remainder";
    }

    @Override
    public Value onWholeNumbers(long left, long right) {
        Overflowing.refuseZeroDivisor(right);
        return IntegerValue.of(left % right);
    }

    @Override
    public Value onFractions(double left, double right, boolean infinitiesAllowed) {
        return DecimalValue.of(left % right);
    }

    @Override
    public Deci onAmounts(Deci left, Deci right) {
        return left.takeRemainderAfterDividingBy(right);
    }

    @Override
    public long onCodepoints(long codepoint, long other) {
        Overflowing.refuseZeroDivisor(other);
        return codepoint % other;
    }

    @Override
    public long onOctets(long octet, double against, boolean fractional) {
        Overflowing.refuseZeroOctetDivisor((long) against);
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
    public boolean canSubtractOneFromOther() {
        return false;
    }

    @Override
    public boolean needsNonZeroDivisor() {
        return true;
    }
}
