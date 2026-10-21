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
        return List.of(Parameter.of("word", Set.of(WordValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(holdsValue((AnyWordValue) arguments.getFirst()));
    }

    private boolean holdsValue(AnyWordValue word) {
        return word.isBound()
                && word.binding().contains(word.canonical())
                && !word.binding().findSlotFor(word.canonical()).holdsUnset();
    }
}
