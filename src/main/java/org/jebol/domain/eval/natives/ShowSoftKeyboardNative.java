package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;

import java.util.List;
import java.util.Set;

public class ShowSoftKeyboardNative extends ScreenNative {

    public ShowSoftKeyboardNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "show-soft-keyboard";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.belongTo("attach", "gob", Set.of(GobValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("attach");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            return UnsetValue.unset();
        };
    }
}
