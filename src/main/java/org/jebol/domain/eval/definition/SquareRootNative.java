package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class SquareRootNative extends DefaultNative {

    @Override
    public String name() {
        return "square-root";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsOnlyNumbers("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
        {
            Value left = arguments.get(0);
            return DecimalValue.of(Math.sqrt(Comparison.asDouble(left)));
        };
    }
    @Override
    public Set<String> refinements() {
        return Set.of();
    }

}
