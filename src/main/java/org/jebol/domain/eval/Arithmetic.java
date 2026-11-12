package org.jebol.domain.eval;

import org.jebol.domain.value.*;

public final class Arithmetic {

    public enum Division {
        SIGN_FOLLOWS_DIVIDEND,
        NEVER_NEGATIVE,
        SIGN_FOLLOWS_DIVISOR
    }

    private Arithmetic() {
    }

    public static Value subtract(Value left, Value right) {
        return left.calculate(right, new Subtract());
    }

    public static Value divideWhole(Value dividend, Value divisor) {
        long by = asIntegerDivideReadsIt(divisor);
        requireNonZero(by);
        return IntegerValue.of(asIntegerDivideReadsIt(dividend) / by);
    }

    private static final long DENORMAL_TRUNCATED = 0;

    private static long asIntegerDivideReadsIt(Value number) {
        return switch (number) {
            case IntegerValue(long magnitude) -> magnitude;
            case AnyDecimalValue fraction -> (long) Comparison.asDouble(fraction);
            case MoneyValue money -> (long) Double.longBitsToDouble(money.asDeci().significand().longValue());
            case PairValue pair -> (long) readPairCellAsDecimal(pair);
            default -> DENORMAL_TRUNCATED;
        };
    }

    private static double readPairCellAsDecimal(PairValue pair) {
        long lowHalf = Integer.toUnsignedLong(Float.floatToRawIntBits((float) pair.x()));
        long highHalf = Integer.toUnsignedLong(Float.floatToRawIntBits((float) pair.y()));
        return Double.longBitsToDouble(highHalf << Integer.SIZE | lowHalf);
    }

    public static boolean isDivisible(Value dividend) {
        return Comparison.isNumeric(dividend) || dividend instanceof CharacterValue;
    }

    public static Value answerUndividedLeftOperand(Value dividend, Value divisor) {
        requireNonZero(asModulusReadsIt(divisor));
        return dividend;
    }

    public static Value findRest(Value dividend, Value divisor, Division definition) {
        if (dividend instanceof IntegerValue(long magnitude1) && divisor instanceof IntegerValue(long magnitude)) {
            return IntegerValue.of(
                    findWholeRest(magnitude1, magnitude, definition));
        }
        double first = asModulusReadsIt(dividend);
        double second = asModulusReadsIt(divisor);
        requireNonZero(second);
        if (definition == Division.SIGN_FOLLOWS_DIVIDEND) {
            return shapeLikeDividend(dividend, first % second);
        }
        double by = definition == Division.NEVER_NEGATIVE
                ? Math.abs(second) : second;
        double rest = ((first % by) + by) % by;
        return shapeLikeDividend(dividend, isNegligibleAgainstItsOperands(rest, first, by)
                ? 0.0
                : rest);
    }

    private static long findWholeRest(long dividend, long divisor, Division definition) {
        requireNonZero(divisor);
        long rest = dividend % divisor;
        return switch (definition) {
            case SIGN_FOLLOWS_DIVIDEND -> rest;
            case NEVER_NEGATIVE -> rest < 0 ? rest + Math.abs(divisor) : rest;
            case SIGN_FOLLOWS_DIVISOR -> rest != 0 && (rest < 0) != (divisor < 0)
                    ? rest + divisor
                    : rest;
        };
    }

    private static Value shapeLikeDividend(Value dividend, double magnitude) {
        return switch (dividend) {
            case CharacterValue ignored -> CharacterValue.of((int) magnitude);
            case TimeValue ignored -> TimeValue.ofNanoseconds(
                    (long) ((magnitude + HALF_NANOSECOND_IN_SECONDS) * NANOSECONDS_IN_SECOND));
            case MoneyValue ignored -> new MoneyValue(new Deci(magnitude));
            case IntegerValue ignored -> IntegerValue.of((long) magnitude);
            default -> DecimalValue.of(magnitude);
        };
    }

    private static boolean isNegligibleAgainstItsOperands(
            double rest, double dividend, double divisor) {

        return areNearlySame(dividend, dividend - rest)
                || areNearlySame(divisor, divisor + rest);
    }

    private static final long STEPS_MODULUS_ALLOWS = 10;

    public static boolean areNearlySame(double first, double second) {
        return Comparison.areLooselyEqual(
                DecimalValue.of(first), DecimalValue.of(second), STEPS_MODULUS_ALLOWS);
    }

    private static final double NANOSECONDS_IN_SECOND = 1.0e9;

    private static final double HALF_NANOSECOND_IN_SECONDS = 5.0e-10;

    private static final double SECONDS_IN_NANOSECOND = 1.0e-9;

    private static double asModulusReadsIt(Value value) {
        return value instanceof TimeValue(long nanoseconds)
                ? nanoseconds * SECONDS_IN_NANOSECOND
                : asMagnitude(value);
    }

    public static double asMagnitude(Value value) {
        return switch (value) {
            case CharacterValue character -> character.codepoint();
            case TimeValue time -> time.nanoseconds();
            default -> Comparison.asDouble(value);
        };
    }

    static void requireNonZero(double divisor) {
        if (divisor == 0.0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

}