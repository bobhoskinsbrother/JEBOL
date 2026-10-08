package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;

public class MakeAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "make";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("prototype", "body");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value made = arguments.getFirst().make(arguments.get(1), evaluator.makerIn(context));
            evaluator.symbols().internWhatWasRead(List.of(made));
            return made;
        };
    }
}
