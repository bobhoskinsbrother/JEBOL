package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class SubtractAction extends DefaultNative {

    public String nativeName() {
        return "subtract";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAllNumbers("value1", "value2");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                Arithmetic.difference(arguments.get(0), arguments.get(1));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of();
    }

}
