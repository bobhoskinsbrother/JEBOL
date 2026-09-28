package org.jebol.domain.eval;

import org.jebol.domain.value.*;

record ComputedSlot(Value held, Value selector) implements Slot {

    @Override
    public Value value() {
        return held;
    }

    @Override
    public void setValue(Value replacement) {
        throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                selector, DatatypeValue.of(replacement.datatype()));
    }
}
