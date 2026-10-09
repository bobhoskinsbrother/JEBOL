package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.LoopSignal;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TypesetValue;

import java.util.List;
import java.util.Set;

public class BreakNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "break";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.belongingTo("return", "value", TypesetValue.ANY_TYPE.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("return");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw refinements.contains("return") && !arguments.isEmpty()
                    ? LoopSignal.breakingWith(arguments.getFirst())
                    : LoopSignal.breaking();
        };
    }
}
