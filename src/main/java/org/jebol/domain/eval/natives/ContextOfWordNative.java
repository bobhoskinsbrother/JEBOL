package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class ContextOfWordNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "context?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("word", TypesetValue.ANY_WORD.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyWordValue word = (AnyWordValue) arguments.getFirst();
            Context binding = word.binding();
            if (!word.isBound() || binding.isALoopFrame()) {
                return NoneValue.none();
            }
            if (!binding.isFunctionFrame()) {
                return new ObjectValue(binding);
            }
            return evaluator.findCallRunningBelowInnermost().orElse(NoneValue.none());
        };
    }
}
