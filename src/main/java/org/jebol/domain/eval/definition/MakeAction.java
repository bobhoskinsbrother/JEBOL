package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;

public class MakeAction extends DefaultNative {

    @Override
    public String name() {
        return "make";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAnyType("prototype", "body");
    }

    @Override
    public RefinedCallable behaviour() {
        return (value, evaluator, context, refinements) ->
        {
            Value first = value.getFirst();
            Value second = value.get(1);
            return first.make(second, evaluator.makerIn(context));
        };
    }
}
