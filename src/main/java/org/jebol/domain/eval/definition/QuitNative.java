package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.QuitRequested;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class QuitNative extends DefaultNative {

    @Override
    public String name() {
        return "quit";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.belongingTo("return", "value", Set.of()));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("return", "now");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw new QuitRequested(argumentOf("return", 0, arguments, refinements)
                    .filter(this::isSomethingOtherThanNone)
                    .orElse(UnsetValue.unset()));
        };
    }

    private boolean isSomethingOtherThanNone(Value carried) {
        return !(carried instanceof NoneValue);
    }
}
