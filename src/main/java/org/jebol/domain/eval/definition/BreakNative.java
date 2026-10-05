package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.LoopSignal;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;

import java.util.List;
import java.util.Set;

public class BreakNative extends DefaultNative {

    @Override
    public String name() {
        return "break";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.belongingTo("return", "value", Typeset.ANY_TYPE.members()));
    }

    @Override
    public Set<String> refinements() {
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
