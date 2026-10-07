package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;

import java.util.List;

public class ToValueNative extends DefaultNative {

    @Override
    public String name() {
        return "to-value";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAnyType("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.getFirst() instanceof UnsetValue
                        ? NoneValue.none()
                        : arguments.getFirst();
    }
}
