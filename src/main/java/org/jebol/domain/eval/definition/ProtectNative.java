package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.Set;

public class ProtectNative extends ProtectingNative {

    private static final boolean PROTECTED = true;

    @Override
    public String nativeName() {
        return "protect";
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("deep", "words", "values", "hide", "lock");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value target = arguments.getFirst();
            if (refinements.contains("hide") && target instanceof WordValue word) {
                word.boundSlot().hide(true);
                return target;
            }
            if (refinements.contains("hide") && refinements.contains("words")
                    && target instanceof BlockValue names
                    && names.datatype() == Datatype.BLOCK) {
                hideEachWordIn(names);
                return target;
            }
            if (refinements.contains("hide") && !namesAField(target, refinements)) {
                throw Raised.of(EvaluationFailure.BAD_REFINES, "protect/hide needs a word");
            }
            protectionChanged(target, PROTECTED, refinements);
            return target;
        };
    }

    private boolean namesAField(Value target, Set<String> refinements) {
        return target instanceof BlockValue path
                && path.datatype().isAnyPath()
                && !refinements.contains("values");
    }

    private void hideEachWordIn(BlockValue names) {
        names.remaining().stream()
                .filter(WordValue.class::isInstance)
                .map(WordValue.class::cast)
                .forEach(word -> word.boundSlot().hide(true));
    }
}
