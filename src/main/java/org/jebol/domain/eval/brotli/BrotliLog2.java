package org.jebol.domain.eval.brotli;

/**
 * Logarithm to base two of a whole number, rounded the way the C rounds it.
 *
 * <p>This exists because Java has no {@code log2} and the obvious substitutes
 * are not close enough. Brotli's encoder prices every choice it makes in bits,
 * and two candidates often cost within a hair of each other, so a result that
 * differs from the C's in its last bit picks a different match and writes
 * different bytes. Measured over the three million whole numbers from two
 * hundred and fifty six upward: {@code log(v) / log(2)} disagrees with the C
 * on twenty-three in a hundred, and even reducing the argument to the range one
 * to two first still disagrees on one in a hundred.
 *
 * <p>The C's own {@code log2} is correctly rounded on the platforms this was
 * checked against -- confirmed against exact decimal arithmetic on four
 * thousand values -- so the answer is to compute the correctly rounded result
 * rather than to imitate any particular library.
 *
 * <p>The method is the usual one. A number is split into a power of two and a
 * mantissa between one and two; the mantissa is divided by the nearest
 * sixteenth, whose logarithm is in a table; and what is left, a number within
 * one part in thirty-three of one, goes through a short series. All of it is
 * carried in pairs of doubles, which hold about a hundred and six bits between
 * them -- far more than the fifty-three needed to round correctly.
 */
final class BrotliLog2 {

    private BrotliLog2() {
    }

    private static final double INV_LN2_HIGH = 1.4426950408889634;
    private static final double INV_LN2_LOW = 2.0355273740931033e-17;

    private static final double[] LOG2_OF_SIXTEENTHS_HIGH = {
            0.0, 0.0874628412503394, 0.16992500144231237, 0.2479275134435855,
            0.32192809488736235, 0.3923174227787603, 0.45943161863729726, 0.5235619560570128,
            0.5849625007211562, 0.6438561897747247, 0.7004397181410922, 0.7548875021634686,
            0.8073549220576041, 0.8579809951275721, 0.9068905956085185, 0.9541963103868752,
    };
    private static final double[] LOG2_OF_SIXTEENTHS_LOW = {
            0.0, 6.765321226991275e-18, -1.0448980122780218e-17, 3.8662183541602335e-18,
            -3.717019964142682e-19, -1.6328502208352762e-17, -3.8053583859449705e-19, 3.838472289082233e-17,
            -5.224490061390109e-18, -7.434039928285364e-19, -2.2038346320583612e-17, -1.5673470184170328e-17,
            4.4407139084295174e-17, 3.2653869625311436e-17, 4.991495917345345e-17, -3.7239566747188146e-17,
    };

    /**
     * The odd reciprocals the series needs, as far as it goes.
     *
     * <p>The series is twice the sum of {@code s} to an odd power over that
     * power. With {@code s} never larger than one thirty-third, the twelfth
     * term is already smaller than anything a pair of doubles can hold.
     */
    private static final int HOW_MANY_TERMS = 12;

    static double of(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "a logarithm wants a positive number, not " + value);
        }
        int whichPowerOfTwo = 63 - Long.numberOfLeadingZeros(value);
        double mantissa = (double) value / (double) (1L << whichPowerOfTwo);
        if (mantissa == 1.0) {
            return whichPowerOfTwo;
        }

        int sixteenth = (int) ((mantissa - 1.0) * 16.0);
        double nearby = 1.0 + sixteenth / 16.0;

        double[] ratio = divide(mantissa, 0.0, nearby, 0.0);
        double[] logarithmOfTheRatio = naturalLogNearOne(ratio[0], ratio[1]);
        double[] inBitsRatio = multiply(logarithmOfTheRatio[0], logarithmOfTheRatio[1],
                INV_LN2_HIGH, INV_LN2_LOW);
        double[] answer = add(LOG2_OF_SIXTEENTHS_HIGH[sixteenth],
                LOG2_OF_SIXTEENTHS_LOW[sixteenth], inBitsRatio[0], inBitsRatio[1]);
        answer = add(answer[0], answer[1], whichPowerOfTwo, 0.0);
        return answer[0];
    }

    /**
     * The natural logarithm of something very close to one.
     *
     * <p>Written as twice the inverse hyperbolic tangent of {@code (x-1)/(x+1)},
     * which is the series that converges fastest here because that quantity is
     * tiny and only its odd powers appear.
     */
    private static double[] naturalLogNearOne(double high, double low) {
        double[] above = add(high, low, -1.0, 0.0);
        double[] below = add(high, low, 1.0, 0.0);
        double[] ratio = divide(above[0], above[1], below[0], below[1]);
        double[] squared = multiply(ratio[0], ratio[1], ratio[0], ratio[1]);

        double[] sum = {1.0 / (2 * HOW_MANY_TERMS - 1), 0.0};
        for (int term = HOW_MANY_TERMS - 1; term >= 1; term--) {
            sum = multiply(sum[0], sum[1], squared[0], squared[1]);
            sum = add(sum[0], sum[1], 1.0 / (2 * term - 1), 0.0);
        }
        double[] whole = multiply(sum[0], sum[1], ratio[0], ratio[1]);
        return add(whole[0], whole[1], whole[0], whole[1]);
    }

    private static double[] add(double firstHigh, double firstLow,
            double secondHigh, double secondLow) {

        double high = firstHigh + secondHigh;
        double spilled = high - firstHigh;
        double error = (firstHigh - (high - spilled)) + (secondHigh - spilled);
        double low = error + firstLow + secondLow;
        double tightened = high + low;
        return new double[]{tightened, low - (tightened - high)};
    }

    private static double[] multiply(double firstHigh, double firstLow,
            double secondHigh, double secondLow) {

        double high = firstHigh * secondHigh;
        double error = Math.fma(firstHigh, secondHigh, -high);
        double low = error + firstHigh * secondLow + firstLow * secondHigh;
        double tightened = high + low;
        return new double[]{tightened, low - (tightened - high)};
    }

    /**
     * Division, corrected twice.
     *
     * <p>One correction leaves the answer a fraction of a bit short, which
     * showed up as a hundred and sixty results out of three million rounding
     * the wrong way. The second correction costs three more multiplications and
     * makes them all agree.
     */
    private static double[] divide(double firstHigh, double firstLow,
            double secondHigh, double secondLow) {

        double roughly = firstHigh / secondHigh;
        double[] back = multiply(roughly, 0.0, secondHigh, secondLow);
        double[] left = add(firstHigh, firstLow, -back[0], -back[1]);
        double correction = left[0] / secondHigh;
        double[] backAgain = multiply(correction, 0.0, secondHigh, secondLow);
        double[] leftAgain = add(left[0], left[1], -backAgain[0], -backAgain[1]);
        double secondCorrection = leftAgain[0] / secondHigh;
        double[] answer = add(roughly, 0.0, correction, 0.0);
        return add(answer[0], answer[1], secondCorrection, 0.0);
    }
}
