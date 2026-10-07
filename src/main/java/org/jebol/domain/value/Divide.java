package org.jebol.domain.value;

public final class Divide implements ArithmeticOperation {

    @Override
    public String spelling() {
        return "divide";
    }

    @Override
    public Value onWholeNumbers(long left, long right) {
        Overflowing.refuseAZeroDivisor(right);
        if (left == Long.MIN_VALUE && right == -1) {
            throw Raised.of(EvaluationFailure.OVERFLOW);
        }
        return left % right == 0
                ? IntegerValue.of(left / right)
                : DecimalValue.of((double) left / right);
    }

    @Override
    public Value onFractions(double left, double right, boolean infinitiesAllowed) {
        if (!infinitiesAllowed) {
            Overflowing.refuseAZeroDivisor(right);
        }
        return DecimalValue.of(left / right);
    }

    @Override
    public Deci onAmounts(Deci left, Deci right) {
        return left.dividedBy(right);
    }

    @Override
    public long onCodepoints(long codepoint, long other) {
        Overflowing.refuseAZeroDivisor(other);
        return codepoint / other;
    }

    @Override
    public long onOctets(long octet, double against, boolean fractional) {
        Overflowing.refuseAZeroOctetDivisor(against);
        return fractional
                ? (long) Overflowing.roundedHalfAwayFromZero(octet / against)
                : octet / (long) against;
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
        return ours / theirs;
    }

    @Override
    public double onMeasuredElements(double ours, double theirs) {
        return ours / theirs;
    }

    @Override
    public boolean multiplies() {
        return false;
    }

    @Override
    public boolean divides() {
        return true;
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
