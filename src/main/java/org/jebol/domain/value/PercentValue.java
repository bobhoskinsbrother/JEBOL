package org.jebol.domain.value;

public final class PercentValue extends AnyDecimalValue {

    PercentValue(double quantity) {
        super(quantity);
    }

    public static PercentValue of(double quantity) {
        return new PercentValue(quantity);
    }

    @Override
    public Datatype datatype() {
        return Datatype.PERCENT;
    }

    @Override
    PercentValue sameKindHolding(double another) {
        return new PercentValue(another);
    }

    @Override
    public Value combinedWithANumber(Value right, ArithmeticOperation operation) {
        Value answered = super.combinedWithANumber(right, operation);
        return right instanceof PercentValue
                && !operation.divides()
                && answered instanceof AnyDecimalValue number
                ? new PercentValue(number.quantity())
                : answered;
    }
}
