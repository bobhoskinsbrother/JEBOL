package org.jebol.domain.eval.mathpresso;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import static org.jebol.domain.eval.mathpresso.SseDoubles.DEFAULT_NAN;
import static org.jebol.domain.eval.mathpresso.SseDoubles.MAGNITUDE;
import static org.jebol.domain.eval.mathpresso.SseDoubles.ONE;
import static org.jebol.domain.eval.mathpresso.SseDoubles.POSITIVE_INFINITY;
import static org.jebol.domain.eval.mathpresso.SseDoubles.QUIET_NAN;
import static org.jebol.domain.eval.mathpresso.SseDoubles.asDouble;
import static org.jebol.domain.eval.mathpresso.SseDoubles.isNaN;
import static org.jebol.domain.eval.mathpresso.SseDoubles.isSignalling;
import static org.jebol.domain.eval.mathpresso.SseDoubles.quiet;

final class LibmCalls {

    private static final long SIGNIFICAND = 0x000FFFFFFFFFFFFFL;
    private static final double FIRST_WITHOUT_ODD_INTEGERS = 0x1p53;
    private static final double NATURAL_LOGARITHM_OF_TWO = Math.log(2.0);
    private static final int FRACTION_BITS = 52;
    private static final int EXPONENT_BIAS = 1023;
    private static final int SUBNORMAL_SCALING = 54;
    private static final double TWO_TO_THE_SUBNORMAL_SCALING = 0x1p54;

    private LibmCalls() {
    }

    static long call(MathPressoOperator operator, long operand) {
        return switch (operator) {
            case EXP -> unary(operand, Math::exp, DEFAULT_NAN);
            case LOG -> unary(operand, Math::log, DEFAULT_NAN);
            case LOG2 -> unary(operand, LibmCalls::binaryLogarithm, DEFAULT_NAN);
            case LOG10 -> unary(operand, StrictMath::log10, QUIET_NAN);
            case SIN -> unary(operand, Math::sin, DEFAULT_NAN);
            case COS -> unary(operand, Math::cos, DEFAULT_NAN);
            case TAN -> unary(operand, Math::tan, DEFAULT_NAN);
            case SINH -> unary(operand, StrictMath::sinh, DEFAULT_NAN);
            case COSH -> unary(operand, StrictMath::cosh, DEFAULT_NAN);
            case TANH -> unary(operand, StrictMath::tanh, DEFAULT_NAN);
            case ASIN -> unary(operand, StrictMath::asin, QUIET_NAN);
            case ACOS -> unary(operand, StrictMath::acos, QUIET_NAN);
            case ATAN -> unary(operand, StrictMath::atan, DEFAULT_NAN);
            default -> throw new IllegalArgumentException(operator + " is not a call to the maths library");
        };
    }

    static long call(MathPressoOperator operator, long first, long second) {
        return switch (operator) {
            case POW -> power(first, second);
            case ATAN2 -> arcTangent(first, second);
            case HYPOT -> hypotenuse(first, second);
            default -> throw new IllegalArgumentException(operator + " is not a call to the maths library");
        };
    }

    static long remainderOnX87(long dividend, long divisor) {
        if (isNaN(dividend) && isNaN(divisor)) {
            long first = quiet(dividend);
            long second = quiet(divisor);
            long firstSignificand = first & SIGNIFICAND;
            long secondSignificand = second & SIGNIFICAND;
            if (firstSignificand != secondSignificand) {
                return firstSignificand > secondSignificand ? first : second;
            }
            return first < 0 ? second : first;
        }
        return binary(dividend, divisor, (x, y) -> x % y);
    }

    private static double binaryLogarithm(double value) {
        if (value > 0 && value < Double.POSITIVE_INFINITY) {
            boolean isSubnormal = value < Double.MIN_NORMAL;
            long bits = Double.doubleToRawLongBits(isSubnormal ? value * TWO_TO_THE_SUBNORMAL_SCALING : value);
            if ((bits & SIGNIFICAND) == 0) {
                return (int) (bits >>> FRACTION_BITS) - EXPONENT_BIAS - (isSubnormal ? SUBNORMAL_SCALING : 0);
            }
        }
        return Math.log(value) / NATURAL_LOGARITHM_OF_TWO;
    }

    private static long unary(long operand, DoubleUnaryOperator function, long outsideTheDomain) {
        if (isNaN(operand)) {
            return quiet(operand);
        }
        double answer = function.applyAsDouble(asDouble(operand));
        return Double.isNaN(answer) ? outsideTheDomain : Double.doubleToRawLongBits(answer);
    }

    private static long binary(long first, long second, DoubleBinaryOperator function) {
        if (isNaN(first)) {
            return quiet(first);
        }
        if (isNaN(second)) {
            return quiet(second);
        }
        double answer = function.applyAsDouble(asDouble(first), asDouble(second));
        return Double.isNaN(answer) ? DEFAULT_NAN : Double.doubleToRawLongBits(answer);
    }

    private static long arcTangent(long y, long x) {
        if (isNaN(x)) {
            return quiet(x);
        }
        if (isNaN(y)) {
            return quiet(y);
        }
        return Double.doubleToRawLongBits(StrictMath.atan2(asDouble(y), asDouble(x)));
    }

    private static long hypotenuse(long x, long y) {
        boolean eitherIsInfinite = (x & MAGNITUDE) == POSITIVE_INFINITY || (y & MAGNITUDE) == POSITIVE_INFINITY;
        if (!isSignalling(x) && !isSignalling(y) && eitherIsInfinite) {
            return POSITIVE_INFINITY;
        }
        return binary(x, y, StrictMath::hypot);
    }

    private static long power(long base, long exponent) {
        double x = asDouble(base);
        double y = asDouble(exponent);
        if (!isNaN(exponent) && y == 0.0) {
            return isSignalling(base) ? quiet(base) : ONE;
        }
        if (!isNaN(base) && x == 1.0) {
            return isSignalling(exponent) ? quiet(exponent) : ONE;
        }
        if (!isNaN(base) && x == -1.0 && Double.isInfinite(y)) {
            return ONE;
        }
        if (isNaN(base)) {
            return !isNaN(exponent) && isOddInteger(y) ? quiet(base) & MAGNITUDE : quiet(base);
        }
        return binary(base, exponent, Math::pow);
    }

    private static boolean isOddInteger(double value) {
        double magnitude = Math.abs(value);
        return magnitude < FIRST_WITHOUT_ODD_INTEGERS && magnitude == Math.floor(magnitude)
                && ((long) magnitude & 1L) == 1L;
    }
}
