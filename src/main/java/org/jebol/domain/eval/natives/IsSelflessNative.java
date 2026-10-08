package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class IsSelflessNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "selfless?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("context"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                !(arguments.getFirst() instanceof ObjectValue(Context fields))
                        || !fields.holds("self"));
    }
}
