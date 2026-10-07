package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;

import java.util.List;
import java.util.Set;

public class InitTopWindowNative extends ScreenNative {

    public InitTopWindowNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String name() {
        return "init-top-window";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("gob", Set.of(Datatype.GOB)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            if (!(arguments.getFirst() instanceof GobValue root)) {
                return refuseTheArgument(arguments.getFirst(), "gob");
            }
            evaluator.screen().takeAsTheRoot(root);
            return UnsetValue.unset();
        };
    }
}
