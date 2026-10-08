package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListEnvNative extends EnvironmentNative {

    public ListEnvNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "list-env";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.ENVIRONMENT);
            return throughTheFileSystem(() -> {
                List<Value> pairs = new ArrayList<>();
                evaluator.environment().all().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(one -> {
                            pairs.add(StringValue.of(one.getKey()));
                            pairs.add(StringValue.of(one.getValue()));
                        });
                return MapValue.of(pairs);
            });
        };
    }
}
