package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BitwiseOperation;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public abstract class BitwiseAction extends ArithmeticNative implements ActionValue {

    protected abstract BitwiseOperation operation();

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnythingMadeOfBits("value1", "value2");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.get(0).combineBitwise(arguments.get(1), operation());
    }
}
