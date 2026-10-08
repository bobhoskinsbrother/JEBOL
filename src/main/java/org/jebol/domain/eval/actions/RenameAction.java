package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.FilePort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
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
        Set<Datatype> anEnd = Set.of(Datatype.FILE, Datatype.BLOCK, Datatype.PORT, Datatype.URL);
        return List.of(Parameter.required("from", anEnd), Parameter.required("to", anEnd));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value asked = arguments.getFirst();
            PortValue from = ports.portMadeFor(asked, evaluator, context);
            return evaluator.theRebolActorsAnswer(nativeName(), List.of(from, arguments.get(1)), Set.of())
                    .orElseGet(() -> renamed(asked, from, arguments.get(1), evaluator));
        };
    }

    private Value renamed(Value asked, PortValue from, Value destination, Evaluator evaluator) {
        if (!from.isAFile()) {
            throw ports.noActionFor(nativeName());
        }
        if (!(destination instanceof StringValue to) || to.datatype() != Datatype.FILE) {
            throw Raised.of(EvaluationFailure.NO_RENAME, asked);
        }
        granted.require(HostService.FILES);
        try {
            evaluator.files().rename(ports.pathOf(from), to.text());
        } catch (FilePort.Denied refused) {
            throw Raised.of(EvaluationFailure.NO_RENAME, asked);
        }
        return from;
    }
}
