package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class IsValueNative extends DefaultNative {

    @Override
    public String name() {
        return "value?";
    }

    @Override
    public List<Parameter> parameters() {
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
