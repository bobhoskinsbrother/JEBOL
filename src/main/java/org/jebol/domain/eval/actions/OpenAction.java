package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;

import java.util.List;
import java.util.Set;

public class OpenAction extends PortAction {

    public OpenAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "open";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("spec"),
                Parameter.belongTo("allow", "access", Set.of(BlockValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("new", "read", "write", "seek", "allow");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            PortValue port = ports.makePortFor(arguments.getFirst(), evaluator, context);
            return evaluator.askRebolActor(nativeName(), List.of(port), refinements)
                    .orElseGet(() -> ports.open(port, evaluator, refinements));
        };
    }
}
