package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public abstract class SteppingNative extends DefaultNative {

    protected abstract int step();

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.hardQuoted("word"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            ContextSlot slot = ((WordValue) arguments.getFirst()).boundSlot();
            Value before = slot.value();
            slot.setValue(stepped(before));
            return before;
        };
    }

    private Value stepped(Value before) {
        return switch (before) {
            case IntegerValue whole -> IntegerValue.of(whole.magnitude() + step());
            case CharacterValue letter -> CharacterValue.of(letter.codepoint() + step());
            case RebolSeries series -> series.atClamped(series.index() + step());
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }
}
