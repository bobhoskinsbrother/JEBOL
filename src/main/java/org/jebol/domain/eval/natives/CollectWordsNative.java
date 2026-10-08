package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CollectWordsNative extends DefaultNative {

    private static final int WHERE_THE_FIRST_REFINEMENT_ARGUMENT_ARRIVES = 1;

    @Override
    public String nativeName() {
        return "collect-words";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                Parameter.belongingTo("ignore", "words",
                        Typeset.ANY_OBJECT.membersAnd(Datatype.BLOCK, Datatype.NONE)),
                Parameter.belongingTo("as", "type", Set.of(Datatype.DATATYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("deep", "set", "ignore", "as");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            List<Value> found = ((BlockValue) arguments.getFirst()).wordsWritten(
                    refinements.contains("deep"), refinements.contains("set"));
            Value ignoring = theIgnoredWords(arguments, refinements);
            Set<String> known = namesIn(ignoring);
            found.removeIf(word -> known.contains(((WordValue) word).canonical()));
            if (refinements.contains("as")) {
                Datatype wanted = theWordKindAsked(arguments, refinements);
                found.replaceAll(word -> ((WordValue) word).as(wanted));
            }
            return BlockValue.block(found);
        };
    }

    private Value theIgnoredWords(List<Value> arguments, Set<String> refinements) {
        return refinements.contains("ignore")
                && arguments.size() > WHERE_THE_FIRST_REFINEMENT_ARGUMENT_ARRIVES
                ? arguments.get(WHERE_THE_FIRST_REFINEMENT_ARGUMENT_ARRIVES)
                : NoneValue.none();
    }

    private Datatype theWordKindAsked(List<Value> arguments, Set<String> refinements) {
        int at = WHERE_THE_FIRST_REFINEMENT_ARGUMENT_ARRIVES
                + (refinements.contains("ignore") ? 1 : 0);
        if (arguments.size() <= at
                || !(arguments.get(at) instanceof DatatypeValue(Datatype represents))
                || !represents.isAnyWord()) {
            throw Raised.of(EvaluationFailure.BAD_FUNC_ARG, "as");
        }
        return represents;
    }

    private Set<String> namesIn(Value source) {
        return switch (source) {
            case BlockValue words -> words.remaining().stream()
                    .filter(WordValue.class::isInstance)
                    .map(WordValue.class::cast)
                    .map(WordValue::canonical)
                    .collect(Collectors.toSet());
            case ObjectValue object -> object.context().slots().stream()
                    .map(ContextSlot::canonical)
                    .collect(Collectors.toSet());
            default -> Set.of();
        };
    }
}
