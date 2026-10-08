package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class IsValueNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "value?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("word", Set.of(Datatype.WORD)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(holdsAValue((WordValue) arguments.getFirst()));
    }

    private boolean holdsAValue(WordValue word) {
        return word.isBound()
                && word.binding().knows(word.canonical())
                && !word.binding().slotFor(word.canonical()).holdsUnset();
    }
}
