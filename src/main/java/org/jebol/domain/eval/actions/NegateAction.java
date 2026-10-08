package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class NegateAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "negate";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnythingWithASign("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.getFirst().negated();
    }
}
