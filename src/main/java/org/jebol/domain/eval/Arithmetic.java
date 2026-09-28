package org.jebol.domain.eval;

import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.eval.arithmetic.arithmeticType.*;
import org.jebol.domain.value.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class Arithmetic {

    public enum Division {
        SIGN_FOLLOWS_THE_DIVIDEND,
        NEVER_NEGATIVE,
        SIGN_FOLLOWS_THE_DIVISOR
    }

    private Arithmetic() {
    }

    public static Value sum(Value left, Value right, String name) {
        return combined(left, right, ArithmeticOperation.findOperation(name));
    }

    public static Value difference(Value left, Value right) {
        return combined(left, right, ArithmeticOperation.findOperation("subtract"));
    }

    public static Value product(Value left, Value right) {
        return combined(left, right, ArithmeticOperation.findOperation("multiply"));
    }

    public static Value quotient(Value left, Value right) {
        return combined(left, right, ArithmeticOperation.findOperation("divide"));
    }

    public static Value remainder(Value left, Value right) {
        return combined(left, right, ArithmeticOperation.findOperation("remainder"));
    }

    public static Value wholeQuotient(Value dividend, Value divisor) {
        long by = (long) Comparison.asDouble(divisor);
        requireNonZero(by);
        return IntegerValue.of((long) Comparison.asDouble(dividend) / by);
    }

    public static Value rest(Value dividend, Value divisor, Division definition) {
        if (dividend instanceof IntegerValue(long magnitude1) && divisor instanceof IntegerValue(long magnitude)) {
            return IntegerValue.of(
                    wholeRest(magnitude1, magnitude, definition));
        }
        double first = asMagnitude(dividend);
        double second = asMagnitude(divisor);
        requireNonZero(second);
        if (definition == Division.SIGN_FOLLOWS_THE_DIVIDEND) {
            return likeTheDividend(dividend, first % second);
        }
        double by = definition == Division.NEVER_NEGATIVE
                ? Math.abs(second) : second;
        double rest = ((first % by) + by) % by;
        return likeTheDividend(dividend, negligibleAgainstItsOperands(rest, first, by)
                ? 0.0
                : rest);
    }

    private static long wholeRest(long dividend, long divisor, Division definition) {
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

    public static Value combined(Value left, Value right, ArithmeticOperation operation) {
        return firstRegisteredHandler(left, right, operation)
                .combine(left, right, operation);
    }

    private static ArithmeticType firstRegisteredHandler(
            Value left, Value right, ArithmeticOperation operation) {

        return arithmeticTypes().stream()
                .filter(kind -> kind.shouldHandle(left, right, operation))
                .findFirst()
                .orElseThrow(() -> nothingCombinesThese(left, right, operation));
    }

    private static final Set<Datatype> THE_DATATYPES_THAT_DO_ARITHMETIC = Set.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT, Datatype.MONEY,
            Datatype.CHAR, Datatype.TIME, Datatype.DATE, Datatype.PAIR,
            Datatype.TUPLE, Datatype.VECTOR);

    private static Raised nothingCombinesThese(
            Value left, Value right, ArithmeticOperation operation) {

        if (left instanceof LogicValue) {
            return Raised.of(EvaluationFailure.EXPECT_VAL,
                    WordValue.of("logic!"),
                    WordValue.of(right.datatype().literalSpelling()));
        }
        return THE_DATATYPES_THAT_DO_ARITHMETIC.contains(left.datatype())
                ? Raised.notRelated(left, right)
                : Raised.cannotUse(left, operation.spelling());
    }

    private static List<ArithmeticType> arithmeticTypes() {
        return List.of(
                new Vectors(),
                new Characters(),
                new Points(),
                new Tuples(),
                new Dates(),
                new ANumberAndACharacter(),
                new Amounts(),
                new Durations(),
                new ANumberAndAnAmount(),
                new WholeNumbers(),
                new Fractions()
        );
    }


    static Value decimalCombined(
            double left, double right, ArithmeticOperation operation) {
        return operation.onFractions(left, right);
    }


    private static Value likeTheDividend(Value dividend, double magnitude) {
        return switch (dividend) {
            case CharacterValue ignored -> CharacterValue.of((int) magnitude);
            case TimeValue ignored -> TimeValue.ofNanoseconds((long) magnitude);
            case MoneyValue ignored -> MoneyValue.of(new BigDecimal((long) magnitude));
            case IntegerValue ignored -> IntegerValue.of((long) magnitude);
            default -> DecimalValue.of(magnitude);
        };
    }

    private static boolean negligibleAgainstItsOperands(
            double rest, double dividend, double divisor) {

        return nearlyTheSame(dividend, dividend - rest)
                || nearlyTheSame(divisor, divisor + rest);
    }

    private static final long STEPS_MODULUS_ALLOWS = 10;

    static boolean nearlyTheSame(double first, double second) {
        return Comparison.looselyEqual(
                DecimalValue.of(first), DecimalValue.of(second), STEPS_MODULUS_ALLOWS);
    }

    static double asMagnitude(Value value) {
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

    public static Raised notRelated(Value left, Value right) {
        return Raised.notRelated(left, right);
    }

}