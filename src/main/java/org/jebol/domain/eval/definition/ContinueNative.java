package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.ContinueSignal;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ContinueNative extends DefaultNative {

    @Override
    public String name() {
        return "continue";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw ContinueSignal.instance();
        };
    }
}
