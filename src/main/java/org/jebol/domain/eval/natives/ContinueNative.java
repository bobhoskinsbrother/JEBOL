package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.ContinueSignal;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ContinueNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "continue";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw ContinueSignal.instance();
        };
    }
}
