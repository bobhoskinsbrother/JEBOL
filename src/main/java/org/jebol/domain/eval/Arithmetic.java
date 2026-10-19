package org.jebol.domain.eval;

import org.jebol.domain.value.*;

public final class Arithmetic {

    public enum Division {
        SIGN_FOLLOWS_THE_DIVIDEND,
        NEVER_NEGATIVE,
        SIGN_FOLLOWS_THE_DIVISOR
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

    private static long asIntegerDivideReadsIt(Value number) {
        return switch (number) {
            case IntegerValue(long magnitude) -> magnitude;
            case MoneyValue money -> (long) Double.longBitsToDouble(money.asDeci().significand().longValue());
            default -> (long) Comparison.asDouble(number);
        };
    }

    public static Value findTheRest(Value dividend, Value divisor, Division definition) {
        if (dividend instanceof IntegerValue(long magnitude1) && divisor instanceof IntegerValue(long magnitude)) {
            return IntegerValue.of(
                    findTheWholeRest(magnitude1, magnitude, definition));
        }
        double first = asMagnitude(dividend);
        double second = asMagnitude(divisor);
        requireNonZero(second);
        if (definition == Division.SIGN_FOLLOWS_THE_DIVIDEND) {
            return shapeLikeTheDividend(dividend, first % second);
        }
        double by = definition == Division.NEVER_NEGATIVE
                ? Math.abs(second) : second;
        double rest = ((first % by) + by) % by;
        return shapeLikeTheDividend(dividend, isNegligibleAgainstItsOperands(rest, first, by)
                ? 0.0
                : rest);
    }

    private static long findTheWholeRest(long dividend, long divisor, Division definition) {
        requireNonZero(divisor);
        long rest = dividend % divisor;
        return switch (definition) {
            case SIGN_FOLLOWS_THE_DIVIDEND -> rest;
            case NEVER_NEGATIVE -> rest < 0 ? rest + Math.abs(divisor) : rest;
            case SIGN_FOLLOWS_THE_DIVISOR -> rest != 0 && (rest < 0) != (divisor < 0)
                    ? rest + divisor
                    : rest;
        };
    }

    private static Value shapeLikeTheDividend(Value dividend, double magnitude) {
        return switch (dividend) {
            case CharacterValue ignored -> CharacterValue.of((int) magnitude);
            case TimeValue ignored -> TimeValue.ofNanoseconds((long) magnitude);
            case MoneyValue ignored -> new MoneyValue(new Deci(magnitude));
            case IntegerValue ignored -> IntegerValue.of((long) magnitude);
            default -> DecimalValue.of(magnitude);
        };
    }

    private static boolean isNegligibleAgainstItsOperands(
            double rest, double dividend, double divisor) {

        return areNearlyTheSame(dividend, dividend - rest)
                || areNearlyTheSame(divisor, divisor + rest);
    }

    private static final long STEPS_MODULUS_ALLOWS = 10;

    public static boolean areNearlyTheSame(double first, double second) {
        return Comparison.areLooselyEqual(
                DecimalValue.of(first), DecimalValue.of(second), STEPS_MODULUS_ALLOWS);
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