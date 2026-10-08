package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
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
                Parameter.required("name", aVariablesName()),
                Parameter.required("value", Set.of(Datatype.STRING, Datatype.NONE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.ENVIRONMENT);
            Value given = arguments.get(1);
            String variable = theVariableNamedBy(arguments.getFirst());
            return throughTheFileSystem(() -> {
                if (given instanceof StringValue held) {
                    evaluator.environment().nameHolds(variable, held.text());
                } else {
                    evaluator.environment().nameHolds(variable, null);
                }
                return given;
            });
        };
    }
}
