package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class AbsoluteFunction extends DefaultFunction {

    @Override
    public String name() {
        return "absolute";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAnythingMeasurable("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.getFirst().absolute();
    }
}
