package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.ActionSentToPort;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class CreateAction extends PortAction {

    private static final boolean NOT_ITS_PARENTS = false;

    public CreateAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "create";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("path", Set.of(FileValue.TYPE, UrlValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            PortValue port = ports.makePortFor(arguments.getFirst(), evaluator, context);
            return ActionSentToPort
                    .answerOnPort(port, this, arguments, refinements, evaluator)
                    .orElseGet(() -> create(port, evaluator));
        };
    }

    private Value create(PortValue port, Evaluator evaluator) {
        if (!port.isAFile()) {
            throw ports.refuseWithNoActionFor(nativeName());
        }
        granted.require(HostService.FILES);
        String path = ports.portPath(port);
        ports.requireFilePolicy(path, evaluator, Set.of(PolicyAccess.WRITE));
        return ports.runThroughFileSystem(() -> {
            if (path.endsWith("/")) {
                evaluator.ports().files().makeDirectory(path, NOT_ITS_PARENTS);
            } else {
                evaluator.ports().files().write(path, new byte[0]);
            }
            return port;
        });
    }
}
