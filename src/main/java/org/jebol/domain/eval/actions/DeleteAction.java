package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class DeleteAction extends PortAction {

    public DeleteAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "delete";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("path", Set.of(FileValue.TYPE, UrlValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value target = arguments.getFirst();
            PortValue port = ports.portMadeFor(target, evaluator, context);
            return evaluator.theRebolActorsAnswer(nativeName(), List.of(port), Set.of())
                    .orElseGet(() -> deleted(target, port, evaluator));
        };
    }

    private Value deleted(Value target, PortValue port, Evaluator evaluator) {
        if (!port.isAFile()) {
            throw ports.noActionFor(nativeName());
        }
        granted.require(HostService.FILES);
        try {
            return evaluator.files().delete(ports.pathOf(port)) ? port : LogicValue.of(false);
        } catch (FilePort.Denied refused) {
            throw Raised.of(EvaluationFailure.NO_DELETE, target);
        }
    }
}
