package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class NegateFunction extends DefaultFunction {

    @Override
    public String name() {
        return "negate";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAnythingWithASign("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.getFirst().negated();
    }
}
