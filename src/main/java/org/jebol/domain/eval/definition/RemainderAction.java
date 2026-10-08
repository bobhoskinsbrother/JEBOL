package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Remainder;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class RemainderAction extends DefaultNative implements ActionValue {
    public String nativeName() {
        return "remainder";
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
            return left.arithmetic(right, new Remainder());
        };
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of();
    }

}
