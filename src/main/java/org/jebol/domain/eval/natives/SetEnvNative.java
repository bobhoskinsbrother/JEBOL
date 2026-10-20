package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.eval.security.PolicyClass;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SetEnvNative extends EnvironmentNative {

    public SetEnvNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "set-env";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("name", acceptAVariablesName()),
                Parameter.of("value", Set.of(StringValue.TYPE, NoneValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.ENVIRONMENT);
            evaluator.policies().require(PolicyClass.ENVR, PolicyAccess.WRITE, Optional.of(arguments.getFirst()));
            Value given = arguments.get(1);
            String variable = readTheVariableNamedBy(arguments.getFirst());
            return runThroughTheFileSystem(() -> {
                if (given instanceof AnyStringValue held) {
                    evaluator.ports().environment().assign(variable, held.text());
                } else {
                    evaluator.ports().environment().assign(variable, null);
                }
                return given;
            });
        };
    }
}
