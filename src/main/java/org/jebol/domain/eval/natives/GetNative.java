package org.jebol.domain.eval.natives;

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
            case PathValue path -> readThrough(path,
                    evaluator.evaluateOrRaise(BlockValue.of(List.of(path)), context),
                    refinements.contains("any"));
            case ObjectValue object -> object.refuseWhenAnyFieldIsHidden().context().valuesExcludingSelf();
            case AnyWordValue word -> readWhatIsHeldBy(word, refinements.contains("any"));
            case Value anythingElse -> anythingElse;
        };
    }

    private static Value readThrough(PathValue path, Value held, boolean unsetIsAnswer) {
        if ((held instanceof UnsetValue || held instanceof EndValue) && !unsetIsAnswer) {
            throw Raised.of(EvaluationFailure.NO_VALUE, path);
        }
        return held;
    }

    private Value readWhatIsHeldBy(AnyWordValue word, boolean unsetIsAnswer) {
        Value held = word.findBoundSlot().value();
        if (held instanceof UnsetValue && !unsetIsAnswer) {
            throw Raised.wordHasNoValue(word);
        }
        return held;
    }
}
