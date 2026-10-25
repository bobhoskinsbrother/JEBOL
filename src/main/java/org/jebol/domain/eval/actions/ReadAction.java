package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.ActionSentToPort;
import org.jebol.domain.eval.ports.PortRequest;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.TypesetValue;
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
                Parameter.of("source",
                        Set.of(FileValue.TYPE, PortValue.TYPE, UrlValue.TYPE, BlockValue.TYPE, WordValue.TYPE)),
                Parameter.belongTo("part", "length", TypesetValue.NUMBER.members()),
                Parameter.belongTo("seek", "index", TypesetValue.NUMBER.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "seek", "string", "binary", "lines", "all");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            PortRequest asked = readRequest(arguments, refinements);
            Value source = arguments.getFirst();
            if (source instanceof PortValue port) {
                return port.isAFile()
                        ? ports.readFromFileBehind(port, evaluator, asked)
                        : ports.readFrom(port, evaluator, arguments, asked);
            }
            Optional<String> behindUrl = ports.findFileNamedByUrl(source, evaluator, context);
            if (behindUrl.isEmpty() && ports.routesToScheme(source)) {
                PortValue routed = ports.openPortFor(source, evaluator, context);
                return ActionSentToPort
                        .answerForPortNamedBy(routed, this, arguments, refinements, evaluator)
                        .orElseGet(() -> ports.readFrom(routed, evaluator, arguments, asked));
            }
            return ports.readFile(
                    behindUrl.orElseGet(() -> ((AnyStringValue) source).text()), evaluator, asked);
        };
    }
}
