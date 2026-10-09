package org.jebol.domain.value;

public record ComputedSlot(Value held, Value selector) implements Slot {

    @Override
    public Value value() {
        return held;
    }

    @Override
    public void setValue(Value replacement) {
        throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                selector, replacement.datatype());
    }
}
