package org.jebol.domain.value;

public final class PercentValue extends AnyDecimalValue {

    public static final AnyDecimalDatatype TYPE = new AnyDecimalDatatype("percent") {

        @Override
        public AnyDecimalValue wrap(double quantity) {
            return PercentValue.of(quantity);
        }

        @Override
        public AnyDecimalValue wrapHundredths(double quantity) {
            return PercentValue.of(quantity / 100.0);
        }

        @Override
        boolean isWrittenWithPercentSign() {
            return true;
        }
    };

    PercentValue(double quantity) {
        super(quantity);
    }

    public static PercentValue of(double quantity) {
        return new PercentValue(quantity);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    PercentValue makeSameKindHolding(double another) {
        return new PercentValue(another);
    }

    @Override
    public Value combineWithNumber(Value right, ArithmeticOperation operation) {
        Value answered = super.combineWithNumber(right, operation);
        return right instanceof PercentValue
                && !operation.divides()
                && answered instanceof AnyDecimalValue number
                ? new PercentValue(number.quantity())
                : answered;
    }
}
