package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.FileValue;

import java.util.List;
import java.util.Set;

public class ToRealFileNative extends HostNative {

    public ToRealFileNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "to-real-file";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("path", Set.of(FileValue.TYPE, StringValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.FILES);
            return throughTheFileSystem(() -> {
                String resolved = evaluator.files().canonicalPathOf(
                        ((AnyStringValue) arguments.getFirst()).text());
                return resolved == null
                        ? NoneValue.none()
                        : FileValue.of(resolved);
            });
        };
    }
}
