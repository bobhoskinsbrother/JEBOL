package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class PickAction extends PickingNative {

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
                .theRebolActorsAnswer(nativeName(), arguments, Set.of())
                .orElseGet(() -> picked(arguments.getFirst(), arguments.get(1)));
    }

    private Value picked(Value subject, Value selector) {
        if (subject instanceof PortValue port && port.eventQueue().isPresent()) {
            return pickedBy(port.eventQueue().orElseThrow(), selector);
        }
        if (selector instanceof LogicValue(boolean truth) && !(subject instanceof BitsetValue)) {
            return subject.picked(truth ? WHAT_TRUE_PICKS : WHAT_FALSE_PICKS);
        }
        return pickedBy(subject, selector);
    }
}
