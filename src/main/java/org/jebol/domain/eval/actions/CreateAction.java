package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
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
        return List.of(Parameter.required("path", Set.of(FileValue.TYPE, UrlValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            PortValue port = ports.portMadeFor(arguments.getFirst(), evaluator, context);
            return evaluator.theRebolActorsAnswer(nativeName(), List.of(port), Set.of())
                    .orElseGet(() -> created(port, evaluator));
        };
    }

    private Value created(PortValue port, Evaluator evaluator) {
        if (!port.isAFile()) {
            throw ports.noActionFor(nativeName());
        }
        granted.require(HostService.FILES);
        String path = ports.pathOf(port);
        return ports.throughTheFileSystem(() -> {
            if (path.endsWith("/")) {
                evaluator.files().makeDirectory(path, NOT_ITS_PARENTS);
            } else {
                evaluator.files().write(path, new byte[0]);
            }
            return port;
        });
    }
}
