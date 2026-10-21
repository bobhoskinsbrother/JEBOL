package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class RenameAction extends PortAction {

    public RenameAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "rename";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        Set<Datatype> anEnd = Set.of(FileValue.TYPE, BlockValue.TYPE, PortValue.TYPE, UrlValue.TYPE);
        return List.of(Parameter.of("from", anEnd), Parameter.of("to", anEnd));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value asked = arguments.getFirst();
            PortValue from = ports.makePortFor(asked, evaluator, context);
            return evaluator.askTheRebolActor(nativeName(), List.of(from, arguments.get(1)), Set.of())
                    .orElseGet(() -> rename(asked, from, arguments.get(1), evaluator));
        };
    }

    private Value rename(Value asked, PortValue from, Value destination, Evaluator evaluator) {
        if (!from.isAFile()) {
            throw ports.refuseWithNoActionFor(nativeName());
        }
        if (!(destination instanceof FileValue to)) {
            throw Raised.of(EvaluationFailure.NO_RENAME, asked);
        }
        granted.require(HostService.FILES);
        if (from.schemeName().equals("dir")) {
            ports.requireFilePolicy(ports.readThePathOf(from), evaluator, Set.of(PolicyAccess.WRITE));
        }
        try {
            evaluator.ports().files().rename(ports.readThePathOf(from), to.text());
        } catch (FilePort.Denied refused) {
            if (refused.isRefusedByHost()) {
                throw refused.raise();
            }
            throw Raised.of(EvaluationFailure.NO_RENAME, asked);
        }
        return from;
    }
}
