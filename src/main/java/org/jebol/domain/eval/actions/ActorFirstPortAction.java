package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public abstract class ActorFirstPortAction extends PortAction {

    protected ActorFirstPortAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    abstract Value answerHere(PortValue port, Evaluator evaluator);

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("port", Set.of(PortValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .askRebolActor(nativeName(), arguments, Set.of())
                .orElseGet(() -> answerHere((PortValue) arguments.getFirst(), evaluator));
    }
}
