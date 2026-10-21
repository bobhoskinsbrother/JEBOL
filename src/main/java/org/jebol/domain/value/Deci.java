package org.jebol.domain.value;

import org.jebol.domain.value.DeciSignificands.Scaled;
import org.jebol.domain.value.DeciSignificands.Shifted;

import java.math.BigInteger;

import static org.jebol.domain.value.DeciSignificands.EXACT;
import static org.jebol.domain.value.DeciSignificands.EXACTLY_HALF;
import static org.jebol.domain.value.DeciSignificands.LESS_THAN_HALF;
import static org.jebol.domain.value.DeciSignificands.MORE_THAN_HALF;
import static org.jebol.domain.value.DeciSignificands.THE_LARGEST_EXPONENT;
import static org.jebol.domain.value.DeciSignificands.THE_SMALLEST_EXPONENT;

public record Deci(BigInteger significand, int exponent, boolean negative) {

    private static final DeciSignificands SIGNIFICANDS = new DeciSignificands();

    private static final BigInteger THE_LARGEST_SIGNIFICAND =
            BigInteger.TEN.pow(26).subtract(BigInteger.ONE);

    private static final BigInteger THE_SMALLEST_LONG_AS_A_SIGNIFICAND = BigInteger.ONE.shiftLeft(63);

    private static final int BELOW_THIS_A_WHOLE_NUMBER_IS_NOUGHT = -26;

    private static final int FROM_THIS_A_WHOLE_NUMBER_OVERFLOWS = 20;

    private static final int PAST_THIS_A_POINT_IS_WRITTEN_AS_AN_EXPONENT = -6;

    private static final int DIGITS_A_DIVISION_AIMS_FOR_TIMES_TWO = 51;

    public static final Deci ZERO = new Deci(BigInteger.ZERO, 0, false);

    public static final Deci ONE = new Deci(BigInteger.ONE, 0, false);

    public Deci(long whole) {
        this(BigInteger.valueOf(whole).abs(), 0, whole < 0);
    }

    public Deci(double quantity) {
        this(SIGNIFICANDS.findTheShortestDigitsOf(quantity));
    }

    private Deci(Deci built) {
        this(built.significand, built.exponent, built.negative);
    }

    public boolean isZero() {
        return significand.signum() == 0;
    }

    public Deci negated() {
        return withSign(!negative);
    }

    public Deci absolute() {
        return withSign(false);
    }

    private Deci withSign(boolean signed) {
        return new Deci(significand, exponent, signed);
    }

    private record Comparable(BigInteger left, int leftTruncation,
            BigInteger right, int rightTruncation, int exponent) {
    }

    private Comparable makeComparableWith(Deci other) {
        if (exponent == other.exponent) {
            return new Comparable(significand, EXACT, other.significand, EXACT, exponent);
        }
        boolean thisIsHigher = exponent > other.exponent;
        BigInteger higher = thisIsHigher ? significand : other.significand;
        BigInteger lower = thisIsHigher ? other.significand : significand;
        int higherExponent = thisIsHigher ? exponent : other.exponent;
        int lowerExponent = thisIsHigher ? other.exponent : exponent;
        if (higher.signum() == 0) {
            return putInOrder(thisIsHigher, higher, EXACT, lower, EXACT, lowerExponent);
        }
        int shiftedLeft = Math.min(SIGNIFICANDS.measureMaxShiftLeft(higher) + 1, higherExponent - lowerExponent);
        higher = higher.multiply(BigInteger.TEN.pow(shiftedLeft));
        higherExponent -= shiftedLeft;
        int stillApart = higherExponent - lowerExponent;
        if (stillApart > 26) {
            return putInOrder(thisIsHigher, higher, EXACT, BigInteger.ZERO,
                    lower.signum() != 0 ? LESS_THAN_HALF : EXACT, higherExponent);
        }
        Shifted shifted = SIGNIFICANDS.shiftRight(lower, stillApart, EXACT);
        return putInOrder(thisIsHigher, higher, EXACT, shifted.value(), shifted.truncation(),
                higherExponent);
    }

    private Comparable putInOrder(boolean thisIsHigher, BigInteger higher, int higherTruncation,
                                  BigInteger lower, int lowerTruncation, int shared) {

        return thisIsHigher
                ? new Comparable(higher, higherTruncation, lower, lowerTruncation, shared)
                : new Comparable(lower, lowerTruncation, higher, higherTruncation, shared);
    }

    private record RoundedPair(BigInteger left, BigInteger right) {
    }

    private RoundedPair roundForComparing(Comparable made) {
        if (SIGNIFICANDS.roundsTruncationUp(made.leftTruncation(), made.left())) {
            return new RoundedPair(made.left().add(BigInteger.ONE), made.right());
        }
        if (SIGNIFICANDS.roundsTruncationUp(made.rightTruncation(), made.right())) {
            return new RoundedPair(made.left(), made.right().add(BigInteger.ONE));
        }
        return new RoundedPair(made.left(), made.right());
    }

    public boolean isEqualTo(Deci other) {
        RoundedPair rounded = roundForComparing(makeComparableWith(other));
        return rounded.left().equals(rounded.right())
                && (negative == other.negative || rounded.left().signum() == 0);
    }

    public boolean isLesserOrEqualTo(Deci other) {
        if (negative && !other.negative) {
            return true;
        }
        if (!negative && other.negative) {
            return isZero() && other.isZero();
        }
        RoundedPair rounded = roundForComparing(makeComparableWith(other));
        int compared = rounded.left().compareTo(rounded.right());
        return negative ? compared >= 0 : compared <= 0;
    }

    public boolean isSameAs(Deci other) {
        if (isZero()) {
            return other.isZero();
        }
        return significand.equals(other.significand) && negative == other.negative
                && exponent == other.exponent;
    }

    public Deci plus(Deci other) {
        Comparable made = makeComparableWith(other);
        return negative == other.negative
                ? addMagnitudes(made)
                : subtractMagnitudes(made, other.negative);
    }

    private Deci addMagnitudes(Comparable made) {
        BigInteger sum = made.left().add(made.right());
        int truncation = made.leftTruncation() + made.rightTruncation();
        int raised = made.exponent();
        for (int pass = 0; pass < 2 && needsAnotherDigitOff(sum, truncation); pass++) {
            if (raised == THE_LARGEST_EXPONENT) {
                throw Raised.of(EvaluationFailure.OVERFLOW);
            }
            raised++;
            Shifted shifted = SIGNIFICANDS.shiftRight(sum, 1, truncation);
            sum = shifted.value();
            truncation = shifted.truncation();
        }
        return new Deci(SIGNIFICANDS.roundUpWhenTheTruncationSays(new Shifted(sum, truncation)),
                raised, negative);
    }

    private boolean needsAnotherDigitOff(BigInteger sum, int truncation) {
        int compared = sum.compareTo(THE_LARGEST_SIGNIFICAND);
        return compared > 0 || (compared == 0 && SIGNIFICANDS.roundsTruncationUp(truncation, sum));
    }

    private Deci subtractMagnitudes(Comparable made, boolean otherSign) {
        BigInteger difference = made.left().subtract(made.right());
        int truncation = made.leftTruncation() - made.rightTruncation();
        boolean signed = negative;
        if (difference.signum() < 0) {
            difference = difference.negate();
            signed = otherSign;
            truncation = -truncation;
        }
        if (SIGNIFICANDS.roundsTruncationUp(truncation, difference)) {
            difference = difference.add(BigInteger.ONE);
        } else if (SIGNIFICANDS.roundsTruncationUp(-truncation, difference)) {
            difference = difference.subtract(BigInteger.ONE);
        }
        return new Deci(difference, made.exponent(), signed);
    }

    public Deci minus(Deci other) {
        return plus(other.negated());
    }

    public Deci times(Deci other) {
        BigInteger product = significand.multiply(other.significand);
        int shift = SIGNIFICANDS.measureMinShiftRight(product);
        int raised = exponent + other.exponent + shift;
        int truncation = EXACT;
        if (shift > 0) {
            Shifted shifted = SIGNIFICANDS.shiftRight(product, shift, EXACT);
            truncation = shifted.truncation();
            product = raised >= THE_SMALLEST_EXPONENT
                    ? SIGNIFICANDS.roundUpWhenTheTruncationSays(shifted)
                    : shifted.value();
        }
        return scaleBy(product, raised, truncation, negative != other.negative);
    }

    private Deci scaleBy(BigInteger significandSoFar, int power, int truncation, boolean signed) {
        Scaled scaled = SIGNIFICANDS.multiplyByTenTo(significandSoFar, 0, power, truncation);
        return new Deci(scaled.significand(), scaled.exponent(), signed);
    }

    public Deci divideBy(Deci other) {
        if (other.isZero()) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        boolean signed = negative != other.negative;
        if (isZero()) {
            return new Deci(BigInteger.ZERO, 0, signed);
        }
        int shift = (int) Math.ceil(DIGITS_A_DIVISION_AIMS_FOR_TIMES_TWO / 2.0
                + Math.log10(SIGNIFICANDS.asTheCDoubleOf(other.significand, 3))
                - Math.log10(SIGNIFICANDS.asTheCDoubleOf(significand, 3)));
        BigInteger widened = significand.multiply(BigInteger.TEN.pow(Math.max(shift, 0)));
        int lowered = exponent - other.exponent - shift;
        BigInteger[] quotientAndRemainder = widened.divideAndRemainder(other.significand);
        Shifted normalised = SIGNIFICANDS.shiftRight(quotientAndRemainder[0],
                SIGNIFICANDS.measureMinShiftRight(quotientAndRemainder[0]),
                truncateDivision(quotientAndRemainder[1], other.significand));
        lowered += SIGNIFICANDS.measureMinShiftRight(quotientAndRemainder[0]);
        BigInteger quotient = lowered >= THE_SMALLEST_EXPONENT
                ? SIGNIFICANDS.roundUpWhenTheTruncationSays(normalised)
                : normalised.value();
        return scaleBy(quotient, lowered, normalised.truncation(), signed);
    }

    private int truncateDivision(BigInteger remainder, BigInteger divisor) {
        int compared = remainder.shiftLeft(1).compareTo(divisor);
        if (compared > 0) {
            return MORE_THAN_HALF;
        }
        if (compared == 0) {
            return EXACTLY_HALF;
        }
        return remainder.signum() == 0 ? EXACT : LESS_THAN_HALF;
    }

    public Deci takeRemainderAfterDividingBy(Deci other) {
        if (other.isZero()) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        if (isZero()) {
            return ZERO;
        }
        BigInteger divisor = other.significand;
        int divisorExponent = other.exponent;
        int apart = exponent - divisorExponent;
        if (apart < 0) {
            if (SIGNIFICANDS.measureMaxShiftLeft(divisor) < -apart) {
                return this;
            }
            divisor = divisor.multiply(BigInteger.TEN.pow(-apart));
            divisorExponent = exponent;
            apart = 0;
        }
        BigInteger left = significand.mod(divisor)
                .multiply(BigInteger.TEN.modPow(BigInteger.valueOf(apart), divisor))
                .mod(divisor);
        return new Deci(left, divisorExponent, negative);
    }

    public long toLong() {
        if (isZero() || exponent < BELOW_THIS_A_WHOLE_NUMBER_IS_NOUGHT) {
            return 0;
        }
        if (exponent >= FROM_THIS_A_WHOLE_NUMBER_OVERFLOWS) {
            throw Raised.of(EvaluationFailure.OVERFLOW);
        }
        BigInteger whole = significand;
        if (exponent > 0) {
            if (BigInteger.TEN.pow(FROM_THIS_A_WHOLE_NUMBER_OVERFLOWS - exponent).compareTo(whole) <= 0) {
                throw Raised.of(EvaluationFailure.OVERFLOW);
            }
            whole = whole.multiply(BigInteger.TEN.pow(exponent));
        } else if (exponent < 0) {
            whole = SIGNIFICANDS.shiftRight(whole, -exponent, EXACT).value();
        }
        if (whole.compareTo(THE_SMALLEST_LONG_AS_A_SIGNIFICAND) > 0
                || (!negative && whole.equals(THE_SMALLEST_LONG_AS_A_SIGNIFICAND))) {
            throw Raised.of(EvaluationFailure.OVERFLOW);
        }
        return negative ? whole.negate().longValue() : whole.longValue();
    }

    public double toDouble() {
        return Double.parseDouble(write(""));
    }

    public String write(String symbol) {
        StringBuilder text = new StringBuilder();
        if (negative) {
            text.append('-');
        }
        text.append(symbol);
        if (isZero()) {
            return text.append('0').toString();
        }
        String digits = significand.toString();
        int count = digits.length();
        int point = count + exponent;
        if (point > count) {
            return text.append(digits).append('e').append(point - count).toString();
        }
        if (point == count) {
            return text.append(digits).toString();
        }
        if (point > 0) {
            return text.append(digits, 0, point).append('.').append(digits, point, count).toString();
        }
        if (point >= PAST_THIS_A_POINT_IS_WRITTEN_AS_AN_EXPONENT) {
            return text.append("0.").append("0".repeat(-point)).append(digits).toString();
        }
        text.append(digits.charAt(0));
        if (count > 1) {
            text.append('.').append(digits, 1, count);
        }
        return text.append('e').append(point - 1).toString();
    }

    private Deci denormaliseTo(Deci scale) {
        if (exponent >= scale.exponent) {
            return this;
        }
        return new Deci(SIGNIFICANDS.shiftRight(significand, scale.exponent - exponent, EXACT).value(),
                scale.exponent, negative);
    }

    public Deci truncateTo(Deci scale) {
        return plus(takeRemainderAfterDividingBy(scale).negated()).denormaliseTo(scale);
    }

    public Deci roundAwayFromZeroTo(Deci scale) {
        Deci left = takeRemainderAfterDividingBy(scale);
        Deci adding = left.isZero() ? left : left.negated().plus(scale.withSign(left.negative));
        return plus(adding).denormaliseTo(scale);
    }

    public Deci floorTo(Deci scale) {
        Deci adding = takeRemainderAfterDividingBy(scale).negated();
        if (!adding.negative && !adding.isZero()) {
            adding = scale.withSign(true).plus(adding);
        }
        return plus(adding).denormaliseTo(scale);
    }

    public Deci ceilTo(Deci scale) {
        Deci adding = takeRemainderAfterDividingBy(scale).negated();
        if (adding.negative && !adding.isZero()) {
            adding = adding.plus(scale.withSign(false));
        }
        return plus(adding).denormaliseTo(scale);
    }

    private record AgainstHalf(Deci left, Deci towardsTheNext) {
    }

    private AgainstHalf compareWithHalfOf(Deci scale) {
        Deci left = takeRemainderAfterDividingBy(scale);
        return new AgainstHalf(left.withSign(false), scale.withSign(false).plus(left.withSign(true)));
    }

    public Deci roundHalfEvenTo(Deci scale) {
        AgainstHalf half = compareWithHalfOf(scale);
        if (!half.left().isEqualTo(half.towardsTheNext())) {
            return roundEitherWay(half, half.left().isLesserOrEqualTo(half.towardsTheNext()), scale);
        }
        Deci unsignedScale = scale.withSign(false);
        Deci within = takeRemainderAfterDividingBy(unsignedScale.plus(unsignedScale)).withSign(false);
        return roundEitherWay(half, within.isLesserOrEqualTo(unsignedScale), scale);
    }

    public Deci roundHalfAwayTo(Deci scale) {
        AgainstHalf half = compareWithHalfOf(scale);
        return roundEitherWay(half, !half.towardsTheNext().isLesserOrEqualTo(half.left()), scale);
    }

    public Deci roundHalfTowardZeroTo(Deci scale) {
        AgainstHalf half = compareWithHalfOf(scale);
        return roundEitherWay(half, half.left().isLesserOrEqualTo(half.towardsTheNext()), scale);
    }

    public Deci roundHalfTowardCeilingTo(Deci scale) {
        AgainstHalf half = compareWithHalfOf(scale);
        return roundEitherWay(half, negative
                ? half.left().isLesserOrEqualTo(half.towardsTheNext())
                : !half.towardsTheNext().isLesserOrEqualTo(half.left()), scale);
    }

    public Deci roundHalfTowardFloorTo(Deci scale) {
        AgainstHalf half = compareWithHalfOf(scale);
        return roundEitherWay(half, negative
                ? !half.towardsTheNext().isLesserOrEqualTo(half.left())
                : half.left().isLesserOrEqualTo(half.towardsTheNext()), scale);
    }

    private Deci roundEitherWay(AgainstHalf half, boolean towardsZero, Deci scale) {
        Deci adding = towardsZero
                ? half.left().withSign(!negative)
                : half.towardsTheNext().withSign(negative);
        return plus(adding).denormaliseTo(scale);
    }
}
