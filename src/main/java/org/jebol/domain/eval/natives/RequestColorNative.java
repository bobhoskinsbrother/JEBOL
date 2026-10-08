package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RequestColorNative extends WindowNative {

    public RequestColorNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "request-color";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.belongingTo("default", "color", Set.of(TupleValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("default");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            Optional<int[]> suggested = argumentOf("default", 0, arguments, refinements)
                    .filter(TupleValue.class::isInstance)
                    .map(given -> ((TupleValue) given).segments());
            return throughTheWindows(() -> evaluator.windows().chooseColour(suggested)
                    .<Value>map(TupleValue::of)
                    .orElseGet(NoneValue::none));
        };
    }
}
