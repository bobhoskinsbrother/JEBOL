package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.eval.security.PolicyClass;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LoadExtensionNative extends ExtensionPointNative {

    @Override
    public String nativeName() {
        return "load-extension";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("name", Set.of(FileValue.TYPE, BinaryValue.TYPE)),
                Parameter.belongingTo("dispatch", "function", Set.of(HandleValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("dispatch");
    }

    @Override
    public RefinedCallable behaviour() {
        RefinedCallable refused = super.behaviour();
        return (arguments, evaluator, context, refinements) -> {
            if (!refinements.contains("dispatch") && arguments.getFirst() instanceof FileValue library) {
                evaluator.policies().require(PolicyClass.EXTENSION, PolicyAccess.EXECUTE, Optional.of(library));
            }
            return refused.call(arguments, evaluator, context, refinements);
        };
    }
}
