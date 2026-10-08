package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class WithNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "with";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("context", Set.of(ObjectValue.TYPE)),
                Parameter.required("body", Set.of(BlockValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator.evaluateOrRaise(
                Binder.bindWhatTheTargetHoldsItself((AnyBlockValue) arguments.get(1),
                        arguments.getFirst().fieldsAsAContext().orElseThrow()),
                context);
    }
}
