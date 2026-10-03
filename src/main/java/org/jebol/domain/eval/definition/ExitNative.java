package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ReturnSignal;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;

import java.util.List;

public class ExitNative extends DefaultNative {

    @Override
    public String name() {
        return "exit";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw new ReturnSignal(UnsetValue.unset());
        };
    }
}
