package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class IsSelflessNative extends DefaultNative {

    @Override
    public String name() {
        return "selfless?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("context"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                !(arguments.getFirst() instanceof ObjectValue(Context fields))
                        || !fields.holds("self"));
    }
}
