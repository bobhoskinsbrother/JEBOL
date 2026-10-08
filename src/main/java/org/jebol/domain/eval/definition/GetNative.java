package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class GetNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "get";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("word");
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
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
