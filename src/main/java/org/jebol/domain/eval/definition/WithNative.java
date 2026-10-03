package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class WithNative extends DefaultNative {

    @Override
    public String name() {
        return "with";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("context", Set.of(Datatype.OBJECT)),
                Parameter.required("body", Set.of(Datatype.BLOCK)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator.evaluateOrRaise(
                Binder.bindWhatTheTargetHoldsItself((BlockValue) arguments.get(1),
                        ((ObjectValue) arguments.getFirst()).context()),
                context);
    }
}
