package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.crypto.ReleasableKey;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class ReleaseNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "release";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("handle", Set.of(HandleValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            HandleValue handle = (HandleValue) arguments.getFirst();
            if (handle.payload() instanceof JavaObjectValue carried) {
                carried.held()
                        .filter(ReleasableKey.class::isInstance)
                        .map(ReleasableKey.class::cast)
                        .ifPresent(ReleasableKey::release);
            }
            return LogicValue.of(handle.isContext());
        };
    }
}
