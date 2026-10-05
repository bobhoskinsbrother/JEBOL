package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

public class FirstPlusNative extends DefaultNative {

    @Override
    public String name() {
        return "first+";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.softQuoted("word"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            ContextSlot slot = theSlotNamedBy(arguments.getFirst());
            if (!(slot.value() instanceof RebolSeries series)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        WordValue.of(((WordValue) arguments.getFirst()).spelling()));
            }
            Value first = series.picked(1);
            if (!series.atTail()) {
                slot.setValue(series.atIndex(series.index() + 1));
            }
            return first;
        };
    }

    private ContextSlot theSlotNamedBy(Value given) {
        if (!(given instanceof WordValue word)
                || !word.isBound()
                || !word.binding().knows(word.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, given);
        }
        return word.binding().slotFor(word.canonical());
    }
}
