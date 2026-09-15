package org.jebol.domain.eval;

import org.jebol.domain.value.Slot;
import org.jebol.domain.value.Value;

record ComputedSlot(Value held) implements Slot {

    @Override
    public Value value() {
        return held;
    }

    @Override
    public void setValue(Value replacement) {
    }
}
