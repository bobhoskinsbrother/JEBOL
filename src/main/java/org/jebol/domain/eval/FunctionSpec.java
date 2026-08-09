package org.jebol.domain.eval;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ParameterKind;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * Reads a function's spec block into the parameters it declares.
 *
 * <p>A spec is itself a block of ordinary values, which is why a function can
 * be built at runtime from a block someone assembled. What each value means
 * depends on its datatype rather than on its position:
 *
 * <ul>
 *   <li>a word is an argument that gets evaluated
 *   <li>a lit-word is an argument taken unevaluated
 *   <li>a get-word is an argument fetched without being called
 *   <li>a refinement is a switch, and words after it belong to it
 *   <li>a block after any of those restricts the datatypes it accepts
 *   <li>a string is documentation and contributes nothing
 * </ul>
 *
 * <p>{@code /local} is a refinement by spelling but not by behaviour: its
 * words are the function's own working names, not arguments a caller supplies.
 */
final class FunctionSpec {

    private static final String LOCALS_REFINEMENT = "local";

    private FunctionSpec() {
    }

    /** The parameters a caller supplies, in the order they are supplied. */
    static List<Parameter> parametersIn(BlockValue spec) {
        List<Parameter> parameters = new ArrayList<>();
        String currentRefinement = null;
        List<Value> items = spec.remaining();

        for (int index = 0; index < items.size(); index++) {
            Value item = items.get(index);

            if (item instanceof StringValue) {
                continue;
            }
            if (item instanceof BlockValue) {
                continue;
            }
            if (!(item instanceof WordValue word)) {
                throw Raised.of(EvaluationFailure.CANNOT_USE,
                        "a function spec holds words, not "
                                + item.datatype().literalSpelling());
            }
            if (word.datatype() == Datatype.REFINEMENT) {
                currentRefinement = word.canonical();
                if (!currentRefinement.equals(LOCALS_REFINEMENT)) {
                    parameters.add(Parameter.refinement(word.spelling()));
                }
                continue;
            }
            if (LOCALS_REFINEMENT.equals(currentRefinement)) {
                continue;
            }
            parameters.add(new Parameter(
                    word.spelling(),
                    kindOf(word),
                    acceptedTypesAfter(items, index),
                    Optional.ofNullable(currentRefinement)));
        }
        return List.copyOf(parameters);
    }

    /** The words a function reserves for itself, from {@code /local}. */
    static List<String> localNamesIn(BlockValue spec) {
        List<String> locals = new ArrayList<>();
        boolean collecting = false;

        for (Value item : spec.remaining()) {
            if (item instanceof WordValue word && word.datatype() == Datatype.REFINEMENT) {
                collecting = word.canonical().equals(LOCALS_REFINEMENT);
                continue;
            }
            if (collecting && item instanceof WordValue word) {
                locals.add(word.spelling());
            }
        }
        return List.copyOf(locals);
    }

    private static ParameterKind kindOf(WordValue word) {
        return switch (word.datatype()) {
            case LIT_WORD -> ParameterKind.LITERAL;
            case GET_WORD -> ParameterKind.SOFT_LITERAL;
            default -> ParameterKind.NORMAL;
        };
    }

    /** The block immediately after a parameter, if it declares its types. */
    private static Set<Datatype> acceptedTypesAfter(List<Value> items, int index) {
        if (index + 1 >= items.size() || !(items.get(index + 1) instanceof BlockValue types)) {
            return Set.of();
        }
        Set<Datatype> accepted = EnumSet.noneOf(Datatype.class);
        for (Value declared : types.remaining()) {
            switch (declared) {
                case DatatypeValue datatype -> accepted.add(datatype.represents());
                case TypesetValue typeset -> accepted.addAll(typeset.represents().members());
                default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                        "a type block holds datatypes, not "
                                + declared.datatype().literalSpelling());
            }
        }
        return accepted;
    }
}
