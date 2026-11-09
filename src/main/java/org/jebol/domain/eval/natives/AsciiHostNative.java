package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;

import java.util.List;
import java.util.Set;

public class AsciiHostNative extends HostNative {

    public AsciiHostNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "ascii-host";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("host", Set.of(StringValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.NETWORK);
            String written = ((AnyStringValue) arguments.getFirst()).text();
            return StringValue.of(evaluator.ports().network().asciiHostName(written).orElse(written));
        };
    }
}
