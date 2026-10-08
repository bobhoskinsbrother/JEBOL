package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ToAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "to";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("type", "value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator.makerIn(context)
                .convertedTo(arguments.getFirst() instanceof DatatypeValue asked
                                ? asked
                                : DatatypeValue.of(arguments.getFirst().datatype()),
                        arguments.get(1));
    }
}
