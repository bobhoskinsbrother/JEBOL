package org.jebol.domain.value;

final class Numbers {

    private Numbers() {
    }

    private static final java.util.Set<Datatype> ANY_NUMBER_WHICH_EXCLUDES_A_TIME =
            Typeset.NUMBER.membersAnd(Datatype.MONEY);

    static boolean isANumber(Value value) {
        return value instanceof IntegerValue
                || value instanceof DecimalValue
                || value instanceof MoneyValue
                || value instanceof TimeValue;
    }

    static boolean theyMayBeCompared(Value one, Value other, Sameness how) {
        if (how.theyWereBroughtTogetherFirst()) {
            return isANumber(one) && isANumber(other);
        }
        return ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(one.datatype())
                && ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(other.datatype())
                || one.datatype() == Datatype.TIME && other.datatype() == Datatype.TIME;
    }

    static boolean areEqual(Value one, Value other, Sameness how) {
        double ours = quantityOf(one);
        double theirs = quantityOf(other);
        if (Double.isNaN(ours) || Double.isNaN(theirs)) {
            return Double.isNaN(ours) && Double.isNaN(theirs) && how.stepsAllowed() > 0;
        }
        if (one instanceof DecimalValue || other instanceof DecimalValue) {
            return withinTheAllowedSteps(ours, theirs, how.stepsAllowed());
        }
        return ours == theirs;
    }

    static double quantityOfANumber(Value value) {
        return value instanceof CharacterValue(int codepoint)
                ? codepoint
                : quantityOf(value);
    }

    static double quantityOf(Value value) {
        return switch (value) {
            case IntegerValue(long magnitude) -> magnitude;
            case TimeValue time -> time.asSeconds().quantity();
            case DecimalValue(double quantity, Datatype ignored) -> quantity;
            case MoneyValue money -> money.amount().doubleValue();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
    }

    private static boolean withinTheAllowedSteps(
            double first, double second, long stepsAllowed) {

        long steps = inRunningOrder(first) - inRunningOrder(second);
        return Math.abs(steps) <= stepsAllowed;
    }

    private static long inRunningOrder(double number) {
        long bits = Double.doubleToRawLongBits(number);
        return bits < 0 ? Long.MIN_VALUE - bits : bits;
    }
}
