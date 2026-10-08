package org.jebol.domain.value;

import java.util.Optional;
import java.util.function.ToLongFunction;

public abstract sealed class AnyDecimalValue implements Value, RebolNumber
        permits DecimalValue, PercentValue {

    private final double quantity;

    AnyDecimalValue(double quantity) {
        this.quantity = quantity;
    }

    @Override
    public abstract Datatype datatype();

    abstract AnyDecimalValue sameKindHolding(double another);

    public double quantity() {
        return quantity;
    }

    @Override
    public Value randomised(RandomDraw draw) {
        return sameKindHolding(draw.fraction() * quantity);
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return Double.doubleToRawLongBits(quantity);
    }

    @Override
    public long asPosition() {
        return (long) quantity;
    }

    @Override
    public Value absolute() {
        return quantity == 0.0 ? this : sameKindHolding(Math.abs(quantity));
    }

    @Override
    public Value negated() {
        return sameKindHolding(-quantity);
    }

    @Override
    public Value heldBetween(Value lowest, Value highest) {
        return sameKindHolding(Math.max(((AnyDecimalValue) lowest).quantity,
                Math.min(((AnyDecimalValue) highest).quantity, quantity)));
    }

    @Override
    public Value combinedWithANumber(Value right, ArithmeticOperation operation) {
        return operation.onFractions(quantity, Numbers.quantityOfANumber(right), true);
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
    public Optional<AnyDecimalValue> asDecimalNumber() {
        return Optional.of(this);
    }

    @Override
    public Optional<MoneyValue> asMoneyInTheCurrencyOf(MoneyValue other) {
        return Optional.of(new MoneyValue(new Deci(quantity)));
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return switch (other) {
            case MoneyValue theirs -> both(asMoneyInTheCurrencyOf(theirs).orElseThrow(), theirs);
            case AnyDecimalValue ignored -> both(this, other);
            case TimeValue theirs -> both(this, theirs.asSeconds());
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return Optional.of(asItStands(wanted, quantity));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnyDecimalValue number
                && number.datatype() == datatype()
                && Double.compare(number.quantity, quantity) == 0;
    }

    @Override
    public int hashCode() {
        return datatype().hashCode() * 31 + Double.hashCode(quantity);
    }

    @Override
    public String toString() {
        return Double.toString(quantity);
    }
}
