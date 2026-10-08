package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.PortRequest;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ReadAction extends PortAction {

    public ReadAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "read";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("source",
                        Set.of(Datatype.FILE, Datatype.PORT, Datatype.URL, Datatype.BLOCK, Datatype.WORD)),
                Parameter.belongingTo("part", "length", Typeset.NUMBER.members()),
                Parameter.belongingTo("seek", "index", Typeset.NUMBER.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "seek", "string", "binary", "lines", "all");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            PortRequest asked = asked(arguments, refinements);
            Value source = arguments.getFirst();
            if (source instanceof PortValue port) {
                return evaluator.theRebolActorsAnswer(nativeName(), arguments, refinements)
                        .orElseGet(() -> port.isAFile()
                                ? ports.readFromTheFileBehind(port, evaluator, asked)
                                : ports.readFrom(port, evaluator, arguments, asked));
            }
            Optional<String> behindTheUrl = ports.theFileNamedByAUrl(source, evaluator, context);
            if (behindTheUrl.isEmpty() && ports.routesToAScheme(source)) {
                return ports.readFrom(ports.portOpenedFor(source, evaluator, context),
                        evaluator, arguments, asked);
            }
            return ports.readTheFile(
                    behindTheUrl.orElseGet(() -> ((AnyStringValue) source).text()), evaluator, asked);
        };
    }
}
