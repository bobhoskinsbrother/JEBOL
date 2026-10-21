package org.jebol.domain.value;

final class Numbers {

    private Numbers() {
    }

    private static final MoneyCoercion MONEY = new MoneyCoercion();

    private static final java.util.Set<Datatype> ANY_NUMBER_WHICH_EXCLUDES_A_TIME =
            TypesetValue.NUMBER.membersAnd(MoneyValue.TYPE);

    static boolean isANumber(Value value) {
        return value instanceof IntegerValue
                || value instanceof AnyDecimalValue
                || value instanceof MoneyValue
                || value instanceof TimeValue;
    }

    static boolean canBeCompared(Value one, Value other, Sameness how) {
        if (how.theyWereBroughtTogetherFirst()) {
            return isANumber(one) && isANumber(other);
        }
        return ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(one.datatype())
                && ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(other.datatype())
                || one instanceof TimeValue && other instanceof TimeValue;
    }

    static boolean areEqual(Value one, Value other, Sameness how) {
        if (MONEY.meetsMoney(one, other)) {
            return MONEY.asDeci(one).isEqualTo(MONEY.asDeci(other));
        }
        double ours = measureTheQuantityOf(one);
        double theirs = measureTheQuantityOf(other);
        if (Double.isNaN(ours) || Double.isNaN(theirs)) {
            return Double.isNaN(ours) && Double.isNaN(theirs) && how.stepsAllowed() > 0;
        }
        if (one instanceof AnyDecimalValue || other instanceof AnyDecimalValue) {
            return withinAllowedSteps(ours, theirs, how.stepsAllowed());
        }
        return ours == theirs;
    }

    static double measureTheQuantityOfANumber(Value value) {
        return value instanceof CharacterValue(int codepoint)
                ? codepoint
                : measureTheQuantityOf(value);
    }

    static double measureTheQuantityOf(Value value) {
        return switch (value) {
            case IntegerValue(long magnitude) -> magnitude;
            case TimeValue time -> time.asSeconds().quantity();
            case AnyDecimalValue fraction -> fraction.quantity();
            case MoneyValue money -> money.asDeci().toDouble();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
    }

    private static boolean withinAllowedSteps(
            double first, double second, long stepsAllowed) {

        long steps = placeInRunningOrder(first) - placeInRunningOrder(second);
        return Math.abs(steps) <= stepsAllowed;
    }

    private static long placeInRunningOrder(double number) {
        long bits = Double.doubleToRawLongBits(number);
        return bits < 0 ? Long.MIN_VALUE - bits : bits;
    }
}
