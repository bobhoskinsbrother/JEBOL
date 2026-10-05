package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class GetNative extends DefaultNative {

    @Override
    public String name() {
        return "get";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsWhateverComesAlong("word");
    }

    @Override
    public Set<String> refinements() {
        return Set.of("any");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case BlockValue path when path.datatype() == Datatype.PATH ->
                    evaluator.evaluateOrRaise(BlockValue.block(List.of(path)), context);
            case ObjectValue object -> object.context().valuesExcludingSelf();
            case WordValue word -> heldBy(word, refinements.contains("any"));
            case Value anythingElse -> anythingElse;
        };
    }

    private Value heldBy(WordValue word, boolean unsetIsAnAnswer) {
        Value held = word.boundSlot().value();
        if (held instanceof UnsetValue && !unsetIsAnAnswer) {
            throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling() + " has no value");
        }
        return held;
    }
}
