package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BitwiseOperation;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public abstract class BitwiseAction extends DefaultNative {

    protected abstract BitwiseOperation operation();

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnythingMadeOfBits("value1", "value2");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.get(0).bitwise(arguments.get(1), operation());
    }
}
