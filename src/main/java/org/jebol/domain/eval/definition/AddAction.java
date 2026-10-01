package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Add;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class AddAction extends DefaultNative {
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
        return (arguments, evaluator, context, refinements) -> {
            Value left = arguments.get(0);
            Value right = arguments.get(1);
            return left.arithmetic(right, new Add());
        };
    }

    @Override
    public Set<String> refinements() {
        return Set.of();
    }
}
