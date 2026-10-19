package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class AbsoluteAction extends ArithmeticNative implements ActionValue {

    @Override
    public String nativeName() {
        return "absolute";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnythingMeasurable("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.getFirst().makeAbsolute();
    }
}
