package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

public class WhetherProtectedNative extends DefaultNative {

    @Override
    public String name() {
        return "protected?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("value"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(isProtected(arguments.getFirst()));
    }

    private boolean isProtected(Value value) {
        return switch (value) {
            case BlockValue path when path.datatype().isAnyPath() ->
                    path.fieldThePathNames().map(ContextSlot::isProtected).orElse(false);
            case BlockValue block -> block.storage().isProtected();
            case StringValue text -> text.storage().isProtected();
            case BinaryValue bytes -> bytes.storage().isProtected();
            case MapValue map -> map.isProtected();
            case ObjectValue object -> object.context().slots().stream()
                    .anyMatch(ContextSlot::isProtected);
            case WordValue word -> word.isBound()
                    && word.binding().knows(word.canonical())
                    && word.binding().slotFor(word.canonical()).isProtected();
            default -> false;
        };
    }
}
