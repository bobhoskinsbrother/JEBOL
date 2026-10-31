package org.jebol.domain.eval.blend2d;

final class FusedMultiplyAdd {

    private static final long LOW_BITS_BELOW_FLOAT = 0x1FFFFFFFL;

    private static final long HALFWAY_BETWEEN_FLOATS = 0x10000000L;

    private static final int EXPONENT_OF_NAN = 0x7FF;

    private FusedMultiplyAdd() {
    }

    static float multiplyThenAdd(float multiplier, float multiplicand, float addend) {
        double product = (double) multiplier * multiplicand;
        double sum = product + addend;
        long bits = Double.doubleToRawLongBits(sum);
        int exponent = (int) (bits >>> 52) & EXPONENT_OF_NAN;
        boolean isHalfway = (bits & LOW_BITS_BELOW_FLOAT) == HALFWAY_BETWEEN_FLOATS;
        boolean isExact = sum - product == addend && sum - addend == product;
        if (!isHalfway || exponent == EXPONENT_OF_NAN || isExact) {
            return (float) sum;
        }
        boolean isNegative = bits < 0;
        double error = isNegative == (addend > product) ? product - sum + addend : addend - sum + product;
        long nudged = isNegative == (error < 0) ? bits + 1 : bits - 1;
        return (float) Double.longBitsToDouble(nudged);
    }
}
