package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

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
        return (arguments, evaluator, context, refinements) -> {
            Value converted = arguments.getFirst().theDatatypeItStandsFor()
                    .convertedFrom(arguments.get(1), evaluator.makerIn(context));
            evaluator.symbols().internWhatWasRead(List.of(converted));
            return converted;
        };
    }
}
