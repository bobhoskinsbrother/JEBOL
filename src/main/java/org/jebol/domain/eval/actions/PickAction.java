package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.natives.PickingNative;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class PickAction extends PickingNative implements ActionValue {

    private static final int WHAT_TRUE_PICKS = 1;

    private static final int WHAT_FALSE_PICKS = 2;

    @Override
    public String nativeName() {
        return "pick";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("series", "index");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .askRebolActor(nativeName(), arguments, Set.of())
                .orElseGet(() -> pick(arguments.getFirst(), arguments.get(1)));
    }

    private Value pick(Value subject, Value selector) {
        if (subject instanceof PortValue port && port.findEventQueue().isPresent()) {
            return pickBy(port.findEventQueue().orElseThrow(), selector);
        }
        if (selector instanceof LogicValue(boolean truth) && !(subject instanceof BitsetValue)) {
            return subject.pick(truth ? WHAT_TRUE_PICKS : WHAT_FALSE_PICKS);
        }
        return pickBy(subject, selector);
    }
}
