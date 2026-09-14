package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.*;

final class FunctionSpec {

    private static final String LOCALS_REFINEMENT = "local";

    private FunctionSpec() {
    }

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
            if (isADatatypeTestsOwnTypeNumber(item)) {
                continue;
            }
            if (!(item instanceof WordValue word)
                    || !(word.datatype() == Datatype.WORD
                        || word.datatype() == Datatype.GET_WORD
                        || word.datatype() == Datatype.LIT_WORD
                        || word.datatype() == Datatype.REFINEMENT
                        || isTheOneSetWordASpecMayHold(word, items, index))) {
                throw refusingTheWholeSpecRatherThanThePartThatWasWrong(spec);
            }
            refuseADuplicateNamingItAsItWasWritten(word, alreadyNamed);
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

    private static boolean isADatatypeTestsOwnTypeNumber(Value item) {
        return item instanceof IntegerValue;
    }

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

    private static boolean isTheOneSetWordASpecMayHold(
            WordValue word, List<Value> items, int index) {

        return word.datatype() == Datatype.SET_WORD
                && word.canonical().equals("return")
                && index + 1 < items.size()
                && items.get(index + 1) instanceof BlockValue;
    }

    private static void refuseADuplicateNamingItAsItWasWritten(
            WordValue word, Set<String> alreadyNamed) {
        if (!alreadyNamed.add(word.canonical())) {
            throw new Raised(org.jebol.domain.value.ErrorValue.about(
                    org.jebol.domain.value.ErrorCategory.SCRIPT,
                    EvaluationFailure.DUP_VARS.errorId(),
                    EvaluationFailure.DUP_VARS.description(),
                    word));
        }
    }

    private static Raised refusingTheWholeSpecRatherThanThePartThatWasWrong(
            BlockValue spec) {
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

    private static Value resolveTypeName(Value declared) {
        if (!(declared instanceof WordValue word) || !word.spelling().endsWith("!")) {
            return declared;
        }
        String withoutMark = word.spelling().substring(0, word.spelling().length() - 1);
        for (Datatype candidate : Datatype.values()) {
            if (candidate.spelling().equalsIgnoreCase(withoutMark)) {
                return DatatypeValue.of(candidate);
            }
        }
        return Typeset.named(withoutMark)
                .map(typeset -> (Value) TypesetValue.of(typeset))
                .orElseThrow(() -> Raised.of(EvaluationFailure.CANNOT_USE,
                        word.spelling() + " names no datatype"));
    }
}
