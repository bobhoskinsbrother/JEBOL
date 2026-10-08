package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class IsProtectedNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "protected?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(isProtected(arguments.getFirst()));
    }

    private boolean isProtected(Value value) {
        return switch (value) {
            case AnyPathValue path ->
                    path.fieldThePathNames().map(ContextSlot::isProtected).orElse(false);
            case AnyBlockValue block -> block.storage().isProtected();
            case AnyStringValue text -> text.storage().isProtected();
            case BinaryValue bytes -> bytes.storage().isProtected();
            case MapValue map -> map.isProtected();
            case ObjectValue object -> object.context().slots().stream()
                    .anyMatch(ContextSlot::isProtected);
            case AnyWordValue word -> word.isBound()
                    && word.binding().knows(word.canonical())
                    && word.binding().slotFor(word.canonical()).isProtected();
            default -> false;
        };
    }
}
