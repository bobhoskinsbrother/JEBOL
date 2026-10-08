package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.FilePort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class EchoNative extends HostNative {

    private static final String WHERE_A_TRUE_ECHO_WRITES = "output.txt";

    public EchoNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "echo";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("target",
                Set.of(Datatype.FILE, Datatype.NONE, Datatype.LOGIC)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            evaluator.stopEchoing();
            Value target = arguments.getFirst();
            if (target instanceof NoneValue || !target.isTruthy()) {
                return UnsetValue.unset();
            }
            granted.require(HostService.FILES);
            String path = target instanceof StringValue address
                    ? address.text()
                    : WHERE_A_TRUE_ECHO_WRITES;
            FilePort files = evaluator.files();
            return throughTheFileSystem(() -> {
                files.write(path, new byte[0]);
                evaluator.alsoWriteTo(text -> files.appendTo(
                        path, text.getBytes(StandardCharsets.UTF_8)));
                return UnsetValue.unset();
            });
        };
    }
}
