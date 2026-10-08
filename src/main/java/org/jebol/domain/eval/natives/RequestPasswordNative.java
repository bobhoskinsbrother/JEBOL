package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;

public class RequestPasswordNative extends WindowNative {

    public RequestPasswordNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "request-password";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            return throughTheWindows(() -> evaluator.windows().askForPassword()
                    .<Value>map(StringValue::of)
                    .orElseGet(NoneValue::none));
        };
    }
}
