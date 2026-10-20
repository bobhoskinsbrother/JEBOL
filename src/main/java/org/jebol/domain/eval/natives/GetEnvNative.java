package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.eval.security.PolicyClass;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;

import java.util.List;
import java.util.Optional;

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
        return List.of(Parameter.of("name", acceptAVariablesName()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.ENVIRONMENT);
            evaluator.policies().require(PolicyClass.ENVR, PolicyAccess.READ, Optional.of(arguments.getFirst()));
            return runThroughTheFileSystem(() -> {
                String held = evaluator.ports().environment().valueOf(readTheVariableNamedBy(arguments.getFirst()));
                return held == null ? NoneValue.none() : StringValue.of(held);
            });
        };
    }
}
