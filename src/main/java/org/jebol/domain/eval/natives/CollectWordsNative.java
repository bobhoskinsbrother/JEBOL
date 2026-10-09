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
        return List.of(Parameter.required("block", Set.of(BlockValue.TYPE)),
                Parameter.belongingTo("ignore", "words",
                        TypesetValue.ANY_OBJECT.membersAnd(BlockValue.TYPE, NoneValue.TYPE)),
                Parameter.belongingTo("as", "type", Set.of(Datatype.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("deep", "set", "ignore", "as");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            List<Value> found = ((AnyBlockValue) arguments.getFirst()).wordsWritten(
                    refinements.contains("deep"), refinements.contains("set"));
            Value ignoring = theIgnoredWords(arguments, refinements);
            Set<String> known = namesIn(ignoring);
            found.removeIf(word -> known.contains(((AnyWordValue) word).canonical()));
            if (refinements.contains("as")) {
                AnyWordValue.AnyWordDatatype wanted = theWordKindAsked(arguments, refinements);
                found.replaceAll(wanted::as);
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

    private AnyWordValue.AnyWordDatatype theWordKindAsked(List<Value> arguments, Set<String> refinements) {
        int at = WHERE_THE_FIRST_REFINEMENT_ARGUMENT_ARRIVES
                + (refinements.contains("ignore") ? 1 : 0);
        if (arguments.size() <= at
                || !(arguments.get(at) instanceof AnyWordValue.AnyWordDatatype asked)) {
            throw Raised.of(EvaluationFailure.BAD_FUNC_ARG, "as");
        }
        return asked;
    }

    private Set<String> namesIn(Value source) {
        return switch (source) {
            case AnyBlockValue words -> words.remaining().stream()
                    .filter(AnyWordValue.class::isInstance)
                    .map(AnyWordValue.class::cast)
                    .map(AnyWordValue::canonical)
                    .collect(Collectors.toSet());
            case ObjectValue object -> object.context().slots().stream()
                    .map(ContextSlot::canonical)
                    .collect(Collectors.toSet());
            default -> Set.of();
        };
    }
}
