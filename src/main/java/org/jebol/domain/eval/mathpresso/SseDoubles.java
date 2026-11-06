package org.jebol.domain.eval.mathpresso;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

final class SseDoubles {

    static final long SIGN = 0x8000000000000000L;
    static final long MAGNITUDE = 0x7FFFFFFFFFFFFFFFL;
    static final long DEFAULT_NAN = 0xFFF8000000000000L;
    static final long QUIET_NAN = 0x7FF8000000000000L;
    static final long POSITIVE_INFINITY = 0x7FF0000000000000L;
    static final long NEGATIVE_INFINITY = 0xFFF0000000000000L;
    static final long ZERO = 0L;
    static final long ONE = 0x3FF0000000000000L;
    static final long HALF = 0x3FE0000000000000L;
    static final long ALL_ONES = -1L;

    private static final long EXPONENT = 0x7FF0000000000000L;
    private static final long FRACTION = 0x000FFFFFFFFFFFFFL;
    private static final long QUIET_BIT = 0x0008000000000000L;

    enum Predicate { EQUAL, NOT_EQUAL, LESS, LESS_OR_EQUAL, NOT_LESS_OR_EQUAL, NOT_LESS }

    private SseDoubles() {
    }

    static boolean isNaN(long bits) {
        return (bits & EXPONENT) == EXPONENT && (bits & FRACTION) != 0;
    }

    static boolean isSignalling(long bits) {
        return isNaN(bits) && (bits & QUIET_BIT) == 0;
    }

    static long quiet(long bits) {
        return bits | QUIET_BIT;
    }

    static double asDouble(long bits) {
        return Double.longBitsToDouble(bits);
    }

    static long toBits(double value) {
        return Double.isNaN(value) ? DEFAULT_NAN : Double.doubleToRawLongBits(value);
    }

    static long flag(boolean holds) {
        return holds ? ONE : ZERO;
    }

    static long add(long first, long second) {
        return calculate(first, second, Double::sum);
    }

    static long subtract(long first, long second) {
        return calculate(first, second, (a, b) -> a - b);
    }

    static long multiply(long first, long second) {
        return calculate(first, second, (a, b) -> a * b);
    }

    static long divide(long first, long second) {
        return calculate(first, second, (a, b) -> a / b);
    }

    private static long calculate(long first, long second, DoubleBinaryOperator operation) {
        if (isNaN(first)) {
            return quiet(first);
        }
        if (isNaN(second)) {
            return quiet(second);
        }
        return toBits(operation.applyAsDouble(asDouble(first), asDouble(second)));
    }

    static long squareRoot(long operand) {
        if (isNaN(operand)) {
            return quiet(operand);
        }
        double value = asDouble(operand);
        return value < 0 ? DEFAULT_NAN : Double.doubleToRawLongBits(Math.sqrt(value));
    }

    static long minimum(long first, long second) {
        return !isNaN(first) && !isNaN(second) && asDouble(first) < asDouble(second) ? first : second;
    }

    static long maximum(long first, long second) {
        return !isNaN(first) && !isNaN(second) && asDouble(first) > asDouble(second) ? first : second;
    }

    static long compare(long first, long second, Predicate predicate) {
        return mask(first, second, predicate) & ONE;
    }

    static long mask(long first, long second, Predicate predicate) {
        boolean unordered = isNaN(first) || isNaN(second);
        double a = asDouble(first);
        double b = asDouble(second);
        boolean holds = switch (predicate) {
            case EQUAL -> !unordered && a == b;
            case NOT_EQUAL -> unordered || a != b;
            case LESS -> !unordered && a < b;
            case LESS_OR_EQUAL -> !unordered && a <= b;
            case NOT_LESS_OR_EQUAL -> unordered || !(a <= b);
            case NOT_LESS -> unordered || !(a < b);
        };
        return holds ? ALL_ONES : ZERO;
    }

    static long floor(long operand) {
        return round(operand, Math::floor);
    }

    static long ceil(long operand) {
        return round(operand, Math::ceil);
    }

    static long nearestEven(long operand) {
        return round(operand, Math::rint);
    }

    static long truncate(long operand) {
        return round(operand, value -> value < 0 ? Math.ceil(value) : Math.floor(value));
    }

    private static long round(long operand, DoubleUnaryOperator rounding) {
        if (isNaN(operand)) {
            return quiet(operand);
        }
        return Double.doubleToRawLongBits(rounding.applyAsDouble(asDouble(operand)));
    }
}
