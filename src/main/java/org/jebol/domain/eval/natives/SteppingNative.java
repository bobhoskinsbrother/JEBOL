package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public abstract class SteppingNative extends DefaultNative {

    protected abstract int step();

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.quoteLiterally("word"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            ContextSlot slot = ((AnyWordValue) arguments.getFirst()).findBoundSlot();
            Value before = slot.value();
            slot.setValue(step(before));
            return before;
        };
    }

    private Value step(Value before) {
        return switch (before) {
            case IntegerValue whole -> IntegerValue.of(whole.magnitude() + step());
            case CharacterValue letter -> CharacterValue.of(letter.codepoint() + step());
            case RebolSeries series -> series.atClamped(series.index() + step());
            case Value anythingElse -> refuseDatatype(anythingElse);
        };
    }
}
