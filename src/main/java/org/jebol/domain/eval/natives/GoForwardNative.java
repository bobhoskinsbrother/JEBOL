package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;

import java.util.List;

public class GoForwardNative extends NavigationNative {

    public GoForwardNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "go-forward";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            requireNavigation(evaluator).goForward();
            return UnsetValue.unset();
        };
    }
}
