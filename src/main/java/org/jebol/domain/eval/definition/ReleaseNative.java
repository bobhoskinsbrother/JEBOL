package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.AKeyThatCanBeReleased;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class ReleaseNative extends DefaultNative {

    @Override
    public String name() {
        return "release";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("handle", Set.of(Datatype.HANDLE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            HandleValue handle = (HandleValue) arguments.getFirst();
            if (handle.payload() instanceof JavaObjectValue carried) {
                carried.held()
                        .filter(AKeyThatCanBeReleased.class::isInstance)
                        .map(AKeyThatCanBeReleased.class::cast)
                        .ifPresent(AKeyThatCanBeReleased::release);
            }
            return LogicValue.of(handle.isContext());
        };
    }
}
