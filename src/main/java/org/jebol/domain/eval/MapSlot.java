package org.jebol.domain.eval;

import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Slot;
import org.jebol.domain.value.Value;

record MapSlot(MapValue map, Value key, Value held) implements Slot {

    @Override
    public Value value() {
        return held;
    }

    @Override
    public void setValue(Value replacement) {
        write(map, key, replacement);
    }

    static void write(MapValue map, Value key, Value written) {
        if (key instanceof NoneValue) {
            return;
        }
        map.put(key, written);
    }
}
