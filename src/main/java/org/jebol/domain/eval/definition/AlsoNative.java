package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class AlsoNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "also";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("value1", "value2");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> arguments.getFirst();
    }
}
