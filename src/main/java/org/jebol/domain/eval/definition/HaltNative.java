package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.HaltRequested;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class HaltNative extends DefaultNative {

    @Override
    public String name() {
        return "halt";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw new HaltRequested();
        };
    }
}
