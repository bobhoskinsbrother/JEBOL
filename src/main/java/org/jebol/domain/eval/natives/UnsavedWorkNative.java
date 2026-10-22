package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class UnsavedWorkNative extends NavigationNative {

    public UnsavedWorkNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "unsaved-work";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("flag", Set.of(LogicValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            LogicValue flag = (LogicValue) arguments.getFirst();
            requireNavigation(evaluator).guardLeaving(flag.truth());
            return flag;
        };
    }
}
