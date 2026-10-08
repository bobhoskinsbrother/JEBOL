package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class NotNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "not";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(!arguments.getFirst().isTruthy());
    }
}
