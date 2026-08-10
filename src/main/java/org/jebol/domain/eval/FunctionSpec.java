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
import org.jebol.domain.value.Typeset;
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
            // `return:` says what the function answers, not what it
            // takes. Counting it as a parameter made every function
            // carrying one want an argument it never uses. The spec has
            // always named this kind; the walk did not honour it.
            if (word.datatype() == Datatype.SET_WORD && word.canonical().equals("return")) {
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
            case LIT_WORD -> ParameterKind.SOFT_QUOTED;
            case GET_WORD -> ParameterKind.HARD_QUOTED;
            default -> ParameterKind.NORMAL;
        };
    }

    /** The block immediately after a parameter, if it declares its types. */
    private static Set<Datatype> acceptedTypesAfter(List<Value> items, int index) {
        if (index + 1 >= items.size() || !(items.get(index + 1) instanceof BlockValue types)) {
            return Set.of();
        }
        // A `return:` annotation may describe what comes back alongside
        // the datatypes: `return: [string! "the answer"]`. An ordinary
        // parameter may not, so the string is allowed here only for that
        // one word rather than in every type block.
        boolean describesTheReturn = items.get(index) instanceof WordValue word
                && word.datatype() == Datatype.SET_WORD
                && word.canonical().equals("return");
        Set<Datatype> accepted = EnumSet.noneOf(Datatype.class);
        for (Value declared : types.remaining()) {
            if (describesTheReturn && declared instanceof StringValue) {
                continue;
            }
            switch (resolveTypeName(declared)) {
                case DatatypeValue datatype -> accepted.add(datatype.represents());
                case TypesetValue typeset -> accepted.addAll(typeset.members());
                // INVALID_ARG rather than CANNOT_USE: the spec is the
                // argument being refused, not an operation the value
                // does not support.
                default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "a type block holds datatypes, not "
                                + declared.datatype().literalSpelling());
            }
        }
        return accepted;
    }

    /**
     * The datatype or typeset a type-block entry names.
     *
     * <p>A type block holds words. {@code integer!} is a word the system
     * context binds to a datatype, and the reader hands it over as written,
     * so the name is resolved here. This used to receive datatypes directly
     * because the reader made them, which is not what a real REBOL does.
     */
    private static Value resolveTypeName(Value declared) {
        if (!(declared instanceof WordValue named) || !named.spelling().endsWith("!")) {
            return declared;
        }
        String withoutMark = named.spelling().substring(0, named.spelling().length() - 1);
        for (Datatype candidate : Datatype.values()) {
            if (candidate.spelling().equalsIgnoreCase(withoutMark)) {
                return DatatypeValue.of(candidate);
            }
        }
        return Typeset.named(withoutMark)
                .map(typeset -> (Value) TypesetValue.of(typeset))
                .orElseThrow(() -> Raised.of(EvaluationFailure.CANNOT_USE,
                        named.spelling() + " names no datatype"));
    }
}
