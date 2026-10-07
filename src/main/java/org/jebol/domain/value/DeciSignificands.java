package org.jebol.domain.value;

import java.math.BigDecimal;
import java.math.BigInteger;

final class DeciSignificands {

    private static final BigInteger TWO_TO_THE_32 = BigInteger.ONE.shiftLeft(32);

    private static final double TWO_TO_THE_32_AS_A_DOUBLE = 4294967296.0;

    private static final BigInteger TEN_TO_THE_26 = BigInteger.TEN.pow(26);

    private static final BigInteger FIVE = BigInteger.valueOf(5);

    static final int THE_SMALLEST_EXPONENT = -128;

    static final int THE_LARGEST_EXPONENT = 127;

    static final int EXACT = 0;

    static final int LESS_THAN_HALF = 1;

    static final int EXACTLY_HALF = 2;

    static final int MORE_THAN_HALF = 3;

    private static final int AN_EXPONENT_PAST_ANY_REACH = 281;

    private static final int WHERE_AN_UNREACHABLE_EXPONENT_IS_PUT = -282;

    private static final int BELOW_THIS_NOTHING_IS_LEFT = -154;

    private static final int AT_THIS_NOTHING_FITS = 153;

    record Shifted(BigInteger value, int truncation) {
    }

    record Scaled(BigInteger significand, int exponent) {
    }

    Shifted shiftedRight(BigInteger value, int shift, int truncationSoFar) {
        if (shift <= 0) {
            return new Shifted(value, truncationSoFar);
        }
        BigInteger divisor = BigInteger.TEN.pow(shift);
        BigInteger[] quotientAndRemainder = value.divideAndRemainder(divisor);
        int compared = quotientAndRemainder[1].compareTo(divisor.shiftRight(1));
        if (compared < 0) {
            return new Shifted(quotientAndRemainder[0],
                    quotientAndRemainder[1].signum() != 0 || truncationSoFar != EXACT
                            ? LESS_THAN_HALF
                            : truncationSoFar);
        }
        return new Shifted(quotientAndRemainder[0],
                compared > 0 || truncationSoFar != EXACT ? MORE_THAN_HALF : EXACTLY_HALF);
    }

    boolean theTruncationRoundsUp(int truncation, BigInteger kept) {
        return truncation == MORE_THAN_HALF || (truncation == EXACTLY_HALF && kept.testBit(0));
    }

    BigInteger roundedUpWhenTheTruncationSays(Shifted shifted) {
        return theTruncationRoundsUp(shifted.truncation(), shifted.value())
                ? shifted.value().add(BigInteger.ONE)
                : shifted.value();
    }

    double asTheCDoubleOf(BigInteger value, int words) {
        double built = 0;
        for (int word = words - 1; word >= 0; word--) {
            built = built * TWO_TO_THE_32_AS_A_DOUBLE
                    + value.shiftRight(32 * word).mod(TWO_TO_THE_32).doubleValue();
        }
        return built;
    }

    int maxShiftLeft(BigInteger value) {
        int digits = (int) (Math.log10(asTheCDoubleOf(value, 3)) + 0.5);
        return BigInteger.TEN.pow(digits).compareTo(value) <= 0 ? 25 - digits : 26 - digits;
    }

    int minShiftRight(BigInteger value) {
        if (value.compareTo(TEN_TO_THE_26) < 0) {
            return 0;
        }
        int digits = (int) (Math.log10(asTheCDoubleOf(value, 6)) + 0.5);
        if (digits == 26) {
            return 1;
        }
        int past = digits - 27;
        BigInteger limit = BigInteger.TEN.pow(27 + past)
                .subtract(FIVE.multiply(BigInteger.TEN.pow(past)));
        return limit.compareTo(value) <= 0 ? digits - 25 : digits - 26;
    }

    Scaled timesTenToThe(BigInteger significand, int exponent, int power, int truncation) {
        if (significand.signum() == 0) {
            return new Scaled(BigInteger.ZERO, 0);
        }
        if (power >= AN_EXPONENT_PAST_ANY_REACH) {
            throw Raised.of(EvaluationFailure.OVERFLOW);
        }
        int raised = exponent + (power < -AN_EXPONENT_PAST_ANY_REACH
                ? WHERE_AN_UNREACHABLE_EXPONENT_IS_PUT
                : power);
        if (raised < THE_SMALLEST_EXPONENT) {
            if (raised < BELOW_THIS_NOTHING_IS_LEFT) {
                return new Scaled(BigInteger.ZERO, 0);
            }
            return new Scaled(roundedUpWhenTheTruncationSays(
                    shiftedRight(significand, THE_SMALLEST_EXPONENT - raised, truncation)),
                    THE_SMALLEST_EXPONENT);
        }
        if (raised > THE_LARGEST_EXPONENT) {
            if (raised >= AT_THIS_NOTHING_FITS
                    || BigInteger.TEN.pow(AT_THIS_NOTHING_FITS - raised).compareTo(significand) <= 0) {
                throw Raised.of(EvaluationFailure.OVERFLOW);
            }
            return new Scaled(significand.multiply(BigInteger.TEN.pow(raised - THE_LARGEST_EXPONENT)),
                    THE_LARGEST_EXPONENT);
        }
        return new Scaled(significand, raised);
    }

    Deci theShortestDigitsOf(double quantity) {
        boolean signed = quantity < 0 || (quantity == 0 && 1 / quantity < 0);
        if (Double.isNaN(quantity) || Double.isInfinite(quantity) || quantity == 0) {
            return new Deci(BigInteger.ZERO, 0, signed);
        }
        BigDecimal stripped = new BigDecimal(Double.toString(Math.abs(quantity))).stripTrailingZeros();
        Scaled scaled = timesTenToThe(stripped.unscaledValue(), 0, -stripped.scale(), EXACT);
        return new Deci(scaled.significand(), scaled.exponent(), signed);
    }
}
