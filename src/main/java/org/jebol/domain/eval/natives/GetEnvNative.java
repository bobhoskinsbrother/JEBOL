package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;

import java.util.List;

public class GetEnvNative extends EnvironmentNative {

    public GetEnvNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "get-env";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("name", aVariablesName()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.ENVIRONMENT);
            return throughTheFileSystem(() -> {
                String held = evaluator.environment().valueOf(theVariableNamedBy(arguments.getFirst()));
                return held == null ? NoneValue.none() : StringValue.of(held);
            });
        };
    }
}
