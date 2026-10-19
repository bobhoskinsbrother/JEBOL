package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.Multiply;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class MultiplyAction extends ArithmeticNative implements ActionValue {

    public String nativeName() {
        return "multiply";
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
            return left.calculate(right, new Multiply());
        };
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of();
    }

}
