package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class UnbindNative extends DefaultNative {

    @Override
    public String name() {
        return "unbind";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("word", Typeset.ANY_WORD.membersAnd(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("deep");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                unbound(arguments.getFirst(), refinements.contains("deep"));
    }

    private Value unbound(Value value, boolean deeply) {
        if (value instanceof WordValue word) {
            return WordValue.of(word.spelling(), word.datatype());
        }
        if (value instanceof BlockValue block) {
            loosenInPlace(block, deeply);
        }
        return value;
    }

    private void loosenInPlace(BlockValue block, boolean deeply) {
        for (int at = block.index(); at <= block.storageLength(); at++) {
            Value item = block.storage().at(at);
            if (item instanceof WordValue word) {
                block.storage().rebindAt(at, WordValue.of(word.spelling(), word.datatype()));
            } else if (deeply && item instanceof BlockValue nested) {
                loosenInPlace(nested, true);
            }
        }
    }
}
