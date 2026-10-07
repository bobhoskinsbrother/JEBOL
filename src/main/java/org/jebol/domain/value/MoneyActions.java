package org.jebol.domain.value;

public final class MoneyActions {

    private static final double SECONDS_IN_A_NANOSECOND = 1e-9;

    private static final double SECONDS_IN_AN_HOUR = 3600.0;

    private final MoneyValue amount;

    public MoneyActions(MoneyValue amount) {
        this.amount = amount;
    }

    public Value combinedWith(Value other, ArithmeticOperation operation) {
        return new MoneyValue(operation.onAmounts(amount.asDeci(), widenedToMeet(other, operation)));
    }

    private Deci widenedToMeet(Value other, ArithmeticOperation operation) {
        return switch (other) {
            case MoneyValue money -> money.asDeci();
            case IntegerValue(long magnitude) -> new Deci(magnitude);
            case DecimalValue decimal -> new Deci(decimal.quantity());
            case TimeValue time when operation.multiplies() -> new HoursBilled(time).asDeci();
            default -> throw Raised.of(EvaluationFailure.NOT_RELATED,
                    WordValue.of(operation.spelling()), DatatypeValue.of(Datatype.MONEY));
        };
    }

    public record HoursBilled(TimeValue time) {

        public Deci asDeci() {
            return new Deci(time.nanoseconds() * SECONDS_IN_A_NANOSECOND / SECONDS_IN_AN_HOUR);
        }
    }

    public Value heldBetween(MoneyValue lowest, MoneyValue highest) {
        if (amount.asDeci().isLesserOrEqualTo(lowest.asDeci())) {
            return lowest;
        }
        if (highest.asDeci().isLesserOrEqualTo(amount.asDeci())) {
            return highest;
        }
        return amount;
    }
}
