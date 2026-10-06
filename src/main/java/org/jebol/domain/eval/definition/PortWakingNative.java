package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

import java.util.List;

public abstract class PortWakingNative extends DefaultNative {

    protected boolean wakes(PortValue port, Value event, Evaluator evaluator) {
        if (!port.context().holds("awake")) {
            return true;
        }
        Value awake = port.context().ownSlotFor("awake").value();
        if (!awake.datatype().isAnyFunction()) {
            return true;
        }
        return evaluator.applyFunction(awake, List.of(event)) instanceof LogicValue(boolean truth)
                && truth;
    }
}
