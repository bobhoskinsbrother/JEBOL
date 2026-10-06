package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;

import java.util.List;
import java.util.Set;

public class ToRealFileNative extends HostNative {

    public ToRealFileNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String name() {
        return "to-real-file";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("path", Set.of(Datatype.FILE, Datatype.STRING)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.FILES);
            return throughTheFilePort(() -> {
                String resolved = evaluator.files().canonicalPathOf(
                        ((StringValue) arguments.getFirst()).text());
                return resolved == null
                        ? NoneValue.none()
                        : StringValue.of(resolved, Datatype.FILE);
            });
        };
    }
}
