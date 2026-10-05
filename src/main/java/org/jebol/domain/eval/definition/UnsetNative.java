package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class UnsetNative extends DefaultNative {

    @Override
    public String name() {
        return "unset";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("word",
                Set.of(Datatype.WORD, Datatype.BLOCK, Datatype.NONE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            switch (arguments.getFirst()) {
                case NoneValue nothing -> {
                    return nothing;
                }
                case WordValue word -> unsetEach(List.of(word));
                case BlockValue words -> unsetEach(words.remaining());
                default -> { }
            }
            return UnsetValue.unset();
        };
    }

    private void unsetEach(List<Value> names) {
        names.forEach(Value::refuseToBeWrittenWhenItNamesSelf);
        names.stream()
                .filter(WordValue.class::isInstance)
                .map(WordValue.class::cast)
                .forEach(word -> word.boundSlot().setValue(UnsetValue.unset()));
    }
}
