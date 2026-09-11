package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.*;

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
        Set<String> alreadyNamed = new java.util.HashSet<>();

        for (int index = 0; index < items.size(); index++) {
            Value item = items.get(index);

            if (item instanceof StringValue) {
                continue;
            }
            if (item instanceof BlockValue) {
                continue;
            }
            // A whole number is allowed and means nothing here. The C says why
            // beside the case it falls into: "special case used by datatype
            // test actions", which write their own type number into the spec.
            if (item instanceof IntegerValue) {
                continue;
            }
            if (!(item instanceof WordValue word)
                    || !(word.datatype() == Datatype.WORD
                        || word.datatype() == Datatype.GET_WORD
                        || word.datatype() == Datatype.LIT_WORD
                        || word.datatype() == Datatype.REFINEMENT
                        || isAReturnAnnotation(word, items, index))) {
                throw refusingTheWholeSpec(spec);
            }
            refuseADuplicate(word, alreadyNamed);
            if (word.datatype() == Datatype.REFINEMENT) {
                currentRefinement = word.canonical();
                parameters.add(Parameter.refinement(word.spelling()));
                continue;
            }
            if (LOCALS_REFINEMENT.equals(currentRefinement)) {
                continue;
            }
            if (word.datatype() == Datatype.SET_WORD) {
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

    /**
     * The one set-word a specification may hold: {@code return:} followed by a
     * block naming what the function answers.
     *
     * <p>Red writes a function that way and the C allows it here so that the
     * same definition reads in both -- "It will be ignored while evaluating",
     * says the comment, and it is. One only: a second {@code return:} is a
     * malformed definition like any other set-word.
     */
    private static boolean isAReturnAnnotation(
            WordValue word, List<Value> items, int index) {

        return word.datatype() == Datatype.SET_WORD
                && word.canonical().equals("return")
                && index + 1 < items.size()
                && items.get(index + 1) instanceof BlockValue;
    }

    /**
     * Every name in a specification is counted once, locals included.
     *
     * <p>{@code Collect_Frame(BIND_ALL | BIND_NO_DUP | ...)} walks the whole
     * block before anything is validated, so a word repeated anywhere in it is
     * a duplicate -- between two arguments, between two locals, or between an
     * argument and a local. JEBOL skipped everything after {@code /local},
     * which let {@code func [a /local a][]} through with two names for one
     * slot.
     *
     * <p>The failure names the word as it was written rather than as it was
     * spelled, so a repeated refinement reports {@code /x} and a repeated
     * argument reports {@code x}. {@code Trap1(RE_DUP_VARS, value)} hands over
     * the value from the block itself.
     */
    private static void refuseADuplicate(WordValue word, Set<String> alreadyNamed) {
        if (!alreadyNamed.add(word.canonical())) {
            throw new Raised(org.jebol.domain.value.ErrorValue.about(
                    org.jebol.domain.value.ErrorCategory.SCRIPT,
                    EvaluationFailure.DUP_VARS.errorId(),
                    EvaluationFailure.DUP_VARS.description(),
                    word));
        }
    }

    /**
     * A specification holding something that cannot be part of one, which
     * names the whole block rather than the part that was wrong.
     *
     * <p>"Report full invalid function spec block in the error", says the C
     * above the line -- and it is the more useful of the two, because a
     * stray set-word means little without the specification around it.
     */
    private static Raised refusingTheWholeSpec(BlockValue spec) {
        return new Raised(org.jebol.domain.value.ErrorValue.about(
                org.jebol.domain.value.ErrorCategory.SCRIPT,
                EvaluationFailure.BAD_FUNC_DEF.errorId(),
                EvaluationFailure.BAD_FUNC_DEF.description(),
                spec.head()));
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
