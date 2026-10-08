package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.PortRequest;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class WriteAction extends PortAction {

    public WriteAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "write";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("destination",
                        Set.of(FileValue.TYPE, PortValue.TYPE, UrlValue.TYPE, BlockValue.TYPE, WordValue.TYPE)),
                Parameter.required("data"),
                Parameter.belongingTo("part", "length", Typeset.NUMBER.members()),
                Parameter.belongingTo("seek", "index", Typeset.NUMBER.members()),
                Parameter.belongingTo("allow", "access", Set.of(BlockValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "seek", "append", "allow", "lines", "binary", "all");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            PortRequest asked = asked(arguments, refinements);
            Value destination = arguments.getFirst();
            Value data = arguments.get(1);
            if (destination instanceof PortValue port) {
                return ports.writeTo(port, data, evaluator, asked);
            }
            if (ports.routesToAScheme(destination)) {
                return ports.writeTo(ports.portOpenedFor(destination, evaluator, context),
                        data, evaluator, asked);
            }
            return ports.writeTheFile(destination, data, evaluator, asked);
        };
    }
}
