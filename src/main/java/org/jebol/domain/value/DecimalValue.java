package org.jebol.domain.value;

import java.util.List;

public final class DecimalValue extends AnyDecimalValue {

    public static final AnyDecimalDatatype TYPE = new AnyDecimalDatatype("decimal") {

        @Override
        public AnyDecimalValue wrap(double quantity) {
            return DecimalValue.of(quantity);
        }

        @Override
        boolean isWrittenWithAPercentSign() {
            return false;
        }

        @Override
        public Value construct(List<Value> contents, Construction construction) {
            if (contents.size() != 1) {
                return super.construct(contents, construction);
            }
            return switch (contents.getFirst()) {
                case IntegerValue(long magnitude) -> DecimalValue.of(magnitude);
                case DecimalValue already -> already;
                default -> throw refuseTheConstruction(contents);
            };
        }
    };

    DecimalValue(double quantity) {
        super(quantity);
    }

    public static DecimalValue of(double quantity) {
        return new DecimalValue(quantity);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    DecimalValue makeSameKindHolding(double another) {
        return new DecimalValue(another);
    }

    @Override
    public long asCountOfRepetitions() {
        return (long) quantity();
    }
}
