package org.jebol.domain.value;

public final class DecimalValue extends AnyDecimalValue {

    DecimalValue(double quantity) {
        super(quantity);
    }

    public static DecimalValue of(double quantity) {
        return new DecimalValue(quantity);
    }

    @Override
    public Datatype datatype() {
        return Datatype.DECIMAL;
    }

    @Override
    DecimalValue sameKindHolding(double another) {
        return new DecimalValue(another);
    }

    @Override
    public long asCountOfRepetitions() {
        return (long) quantity();
    }
}
