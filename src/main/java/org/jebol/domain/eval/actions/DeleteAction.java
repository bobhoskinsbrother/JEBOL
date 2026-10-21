package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.security.PolicyAccess;
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
        return List.of(Parameter.of("path", Set.of(FileValue.TYPE, UrlValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value target = arguments.getFirst();
            PortValue port = ports.makePortFor(target, evaluator, context);
            return evaluator.askTheRebolActor(nativeName(), List.of(port), Set.of())
                    .orElseGet(() -> delete(target, port, evaluator));
        };
    }

    private Value delete(Value target, PortValue port, Evaluator evaluator) {
        if (!port.isAFile()) {
            throw ports.refuseWithNoActionFor(nativeName());
        }
        granted.require(HostService.FILES);
        if (port.schemeName().equals("dir")) {
            ports.requireFilePolicy(ports.readThePathOf(port), evaluator, Set.of(PolicyAccess.WRITE));
        }
        try {
            return evaluator.ports().files().delete(ports.readThePathOf(port)) ? port : LogicValue.of(false);
        } catch (FilePort.Denied refused) {
            if (refused.isRefusedByHost()) {
                throw refused.raise();
            }
            throw Raised.of(EvaluationFailure.NO_DELETE, target);
        }
    }
}
