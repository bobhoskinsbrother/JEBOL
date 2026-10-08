package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.QuitRequested;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class QuitNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "quit";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.belongingTo("return", "value", Set.of()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
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
