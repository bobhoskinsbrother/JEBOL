package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class FlushAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "flush";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("port", Set.of(PortValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            evaluator.output().flush();
            return arguments.getFirst();
        };
    }
}
