package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class UnbindNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "unbind";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("word", TypesetValue.ANY_WORD.membersAnd(BlockValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("deep");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                unbind(arguments.getFirst(), refinements.contains("deep"));
    }

    private Value unbind(Value value, boolean deeply) {
        if (value instanceof AnyWordValue word) {
            return word.bindTo(Context.unbound());
        }
        if (value instanceof AnyBlockValue block) {
            loosenInPlace(block, deeply);
        }
        return value;
    }

    private void loosenInPlace(AnyBlockValue block, boolean deeply) {
        for (int at = block.index(); at <= block.storageLength(); at++) {
            Value item = block.storage().at(at);
            if (item instanceof AnyWordValue word) {
                block.storage().rebindAt(at, word.bindTo(Context.unbound()));
            } else if (deeply && item instanceof AnyBlockValue nested) {
                loosenInPlace(nested, true);
            }
        }
    }
}
