package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class BindNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "bind";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("word"), Parameter.of("target"));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("copy", "only", "new", "set");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value targetGiven = arguments.get(1);
            Context target = findContextFor(targetGiven).orElseThrow(() ->
                    Raised.of(EvaluationFailure.EXPECT_ARG,
                            "bind wanted an object or a bound word, not "
                                    + targetGiven.datatype().literalSpelling()));
            boolean addsWhatIsMissing = refinements.contains("new") || refinements.contains("set");
            return switch (arguments.getFirst()) {
                case AnyWordValue word -> bindWordInto(word, target, addsWhatIsMissing);
                case AnyBlockValue block -> bindBlockInto(
                        block, target, refinements, addsWhatIsMissing);
                default -> refuseArgument(arguments.getFirst(), "word");
            };
        };
    }

    private Optional<Context> findContextFor(Value target) {
        if (target instanceof AnyWordValue word) {
            return word.isBound() ? Optional.of(word.binding()) : Optional.empty();
        }
        return target.fieldsAsContext();
    }

    private Value bindWordInto(AnyWordValue word, Context target, boolean addsWhatIsMissing) {
        if (addsWhatIsMissing) {
            target.register(word.canonical());
        }
        if (target.declaresItRelatively(word.canonical())) {
            return word.bindTo(target);
        }
        if (!target.holdsWhereResolved(word.canonical())) {
            throw Raised.of(EvaluationFailure.NOT_IN_CONTEXT, word.spelling());
        }
        return word.bindTo(target.contextHolding(word.canonical()));
    }

    private Value bindBlockInto(AnyBlockValue block, Context target,
                                Set<String> refinements, boolean addsWhatIsMissing) {

        boolean deeply = !refinements.contains("only");
        if (addsWhatIsMissing) {
            block.listWordsWritten(deeply, refinements.contains("set")).stream()
                    .map(word -> ((AnyWordValue) word).canonical())
                    .filter(canonical -> !target.answersAlreadyTo(canonical))
                    .forEach(target::register);
        }
        return refinements.contains("copy")
                ? Binder.bindWordsTargetHoldsInCopy(block, target, deeply)
                : Binder.bindWordsTargetHolds(block, target, deeply);
    }
}
