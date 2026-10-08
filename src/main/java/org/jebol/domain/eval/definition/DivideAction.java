package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Divide;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class DivideAction extends DefaultNative {
    public String nativeName() {
        return "divide";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAllNumbers("value1", "value2");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
        {
            Value left = arguments.get(0);
            Value right = arguments.get(1);
            return left.arithmetic(right, new Divide());
        };
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of();
    }

}
