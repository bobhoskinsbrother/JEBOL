package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class AddFunctionDefinition extends DefaultFunctionDefinition {
    @Override
    public String name() {
        return "add";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAllNumbers("value1", "value2");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                Arithmetic.sum(arguments.get(0), arguments.get(1), name());
    }

    @Override
    public Set<String> refinements() {
        return Set.of();
    }
}
