package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ReturnSignal;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ReturnNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "return";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw new ReturnSignal(arguments.getFirst());
        };
    }
}
