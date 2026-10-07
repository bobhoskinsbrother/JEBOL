package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.PortRequest;
import org.jebol.domain.eval.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
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
    public String name() {
        return "write";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("destination",
                        Set.of(Datatype.FILE, Datatype.PORT, Datatype.URL, Datatype.BLOCK, Datatype.WORD)),
                Parameter.required("data"),
                Parameter.belongingTo("part", "length", Typeset.NUMBER.members()),
                Parameter.belongingTo("seek", "index", Typeset.NUMBER.members()),
                Parameter.belongingTo("allow", "access", Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinements() {
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
