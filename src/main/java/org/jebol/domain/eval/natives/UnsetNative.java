package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class UnsetNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "unset";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("word",
                Set.of(WordValue.TYPE, BlockValue.TYPE, NoneValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            switch (arguments.getFirst()) {
                case NoneValue nothing -> {
                    return nothing;
                }
                case AnyWordValue word -> unsetEach(List.of(word));
                case AnyBlockValue words -> unsetEach(words.remaining());
                default -> { }
            }
            return UnsetValue.unset();
        };
    }

    private void unsetEach(List<Value> names) {
        names.forEach(Value::refuseToBeWrittenWhenItNamesSelf);
        names.stream()
                .filter(AnyWordValue.class::isInstance)
                .map(AnyWordValue.class::cast)
                .forEach(word -> word.boundSlot().setValue(UnsetValue.unset()));
    }
}
