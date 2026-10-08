package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class RequestDirNative extends WindowNative {

    public RequestDirNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "request-dir";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.belongingTo("title", "text", Set.of(StringValue.TYPE)),
                Parameter.belongingTo("dir", "name", Set.of(FileValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("title", "dir", "keep");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            return throughTheWindows(() -> evaluator.windows().chooseDirectory(
                            textGivenFor("dir", arguments, refinements),
                            textGivenFor("title", arguments, refinements))
                    .<Value>map(FileValue::of)
                    .orElseGet(NoneValue::none));
        };
    }
}
