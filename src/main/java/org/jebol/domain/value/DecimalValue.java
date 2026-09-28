package org.jebol.domain.value;

import java.util.Optional;

/**
 * A double, covering both {@code decimal!} and {@code percent!}.
 *
 * <p>The two share a representation and differ only in how they are printed,
 * which is why the datatype is carried rather than inferred.
 */
public record DecimalValue(double quantity, Datatype datatype)
        implements Value, RebolNumber {

    @Override
    public Value combinedWithANumber(Value right, ArithmeticOperation operation) {
        Value answered = operation.onFractions(
                quantity, Numbers.quantityOfANumber(right), true);
        return datatype == Datatype.PERCENT
                && right.datatype() == Datatype.PERCENT
                && !operation.divides()
                && answered instanceof DecimalValue(double amount, Datatype ignored)
                ? DecimalValue.percent(amount)
                : answered;
    }

    @Override
    public boolean mayLoseATime(ArithmeticOperation operation) {
        return false;
    }

    @Override
    public boolean mayMeetADate() {
        return false;
    }

    @Override
    public Optional<DecimalValue> asDecimalNumber() {
        return Optional.of(this);
    }

    @Override
    public Optional<MoneyValue> asMoneyInTheCurrencyOf(MoneyValue other) {
        return Optional.of(other.amounting(
                MoneyActions.asBigDecimal(this)));
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return switch (other) {
            case MoneyValue theirs -> both(asMoneyInTheCurrencyOf(theirs).orElseThrow(), theirs);
            case DecimalValue ignored -> both(this, other);
            case TimeValue theirs -> both(this, theirs.asSeconds());
            default -> Optional.empty();
        };
    }


    public DecimalValue {
        if (datatype != Datatype.DECIMAL && datatype != Datatype.PERCENT) {
            throw new IllegalArgumentException(
                    "a decimal value is decimal! or percent!, not " + datatype.literalSpelling());
        }
    }

    @Override
    public java.util.Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return java.util.Optional.of(asItStands(wanted, quantity));
    }

    public static DecimalValue of(double quantity) {
        return new DecimalValue(quantity, Datatype.DECIMAL);
    }

    public static DecimalValue percent(double quantity) {
        return new DecimalValue(quantity, Datatype.PERCENT);
    }

    @Override
    public String toString() {
        return Double.toString(quantity);
    }
}
