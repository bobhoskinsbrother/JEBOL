package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class ShowNative extends ScreenNative {

    public ShowNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "show";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("gob", Set.of(GobValue.TYPE, NoneValue.TYPE, BlockValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            if (arguments.getFirst() instanceof GobValue gob) {
                throughTheScreen(() -> evaluator.screen().show(gob));
            }
            return arguments.getFirst();
        };
    }
}
