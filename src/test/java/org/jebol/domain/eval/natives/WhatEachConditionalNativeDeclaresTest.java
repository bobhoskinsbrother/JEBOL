package org.jebol.domain.eval.natives;

import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachConditionalNativeDeclaresTest {

    private static final Set<String> NOTHING = Set.of();

    private static final Set<String> ONLY = Set.of("only");

    private static final Set<Datatype> A_BLOCK = Set.of(Datatype.BLOCK);

    private Value answerOf(DefaultNative definition, Set<String> refinements,
                           Value... arguments) {
        return definition.behaviour().call(List.of(arguments), null, null, refinements);
    }

    private Value yes() {
        return LogicValue.of(true);
    }

    private Value no() {
        return LogicValue.of(false);
    }

    private Value one() {
        return IntegerValue.of(1);
    }

    private Value two() {
        return IntegerValue.of(2);
    }

    private AnyBlockValue aBlockHoldingOne() {
        return BlockValue.block(List.of(IntegerValue.of(1)));
    }

    private AnyBlockValue anEmptyBlock() {
        return BlockValue.block(List.of());
    }

    private Parameter anyType(String name) {
        return Parameter.required(name, Typeset.ANY_TYPE.members());
    }

    Stream<Arguments> whatEachDeclares() {
        return Stream.of(
                Arguments.of(new IfNative(), "if",
                        List.of(anyType("condition"), anyType("branch")), ONLY),
                Arguments.of(new UnlessNative(), "unless",
                        List.of(anyType("condition"), anyType("branch")), ONLY),
                Arguments.of(new EitherNative(), "either",
                        List.of(anyType("condition"), anyType("true-branch"),
                                anyType("false-branch")), ONLY),
                Arguments.of(new NotNative(), "not",
                        List.of(anyType("value")), NOTHING),
                Arguments.of(new AnyNative(), "any",
                        List.of(Parameter.required("block", A_BLOCK)), NOTHING),
                Arguments.of(new AllNative(), "all",
                        List.of(Parameter.required("block", A_BLOCK)), NOTHING),
                Arguments.of(new CaseNative(), "case",
                        List.of(Parameter.required("choices", A_BLOCK)), Set.of("all")),
                Arguments.of(new SwitchNative(), "switch",
                        List.of(Parameter.required("value"),
                                Parameter.required("choices", A_BLOCK),
                                Parameter.belongingTo("default", "fallback", A_BLOCK)),
                        Set.of("case", "default", "all")),
                Arguments.of(new AttemptNative(), "attempt",
                        List.of(Parameter.required("block",
                                Set.of(Datatype.BLOCK, Datatype.PAREN))),
                        Set.of("safer")),
                Arguments.of(new TryNative(), "try",
                        List.of(Parameter.required("block",
                                        Set.of(Datatype.BLOCK, Datatype.PAREN)),
                                Parameter.belongingTo("with", "handler", Set.of())),
                        Set.of("all", "with")),
                Arguments.of(new DoNative(), "do",
                        List.of(anyType("value"),
                                Parameter.belongingTo("args", "arg", Set.of()),
                                Parameter.belongingTo("next", "var", Set.of(Datatype.WORD))),
                        Set.of("next", "args")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("whatEachDeclares")
    @DisplayName("each declares exactly what the inline definition declared")
    void declaresWhatItDeclaredInline(DefaultNative definition, String name,
            List<Parameter> parameters, Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.parametersAsWritten()).isEqualTo(parameters);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    @Test
    @DisplayName("switch's fallback arrives only with /default")
    void theFallbackBelongsToDefault() {
        Parameter fallback = new SwitchNative().parametersAsWritten().getLast();
        assertThat(fallback.kind()).isEqualTo(ParameterKind.REFINEMENT_ARGUMENT);
        assertThat(fallback.owningRefinement()).isEqualTo(Optional.of("default"));
    }

    @Nested
    @DisplayName("if and unless run their one branch on opposite conditions")
    class IfAndUnless {

        @Test
        @DisplayName("if answers none when the condition fails, without touching the branch")
        void ifOnAFailedCondition() {
            assertThat(answerOf(new IfNative(), NOTHING, no(), aBlockHoldingOne()))
                    .isEqualTo(NoneValue.none());
            assertThat(answerOf(new IfNative(), NOTHING, NoneValue.none(), one()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("unless answers none when the condition holds")
        void unlessOnAHeldCondition() {
            assertThat(answerOf(new UnlessNative(), NOTHING, yes(), aBlockHoldingOne()))
                    .isEqualTo(NoneValue.none());
            assertThat(answerOf(new UnlessNative(), NOTHING, one(), one()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a branch that is not a block is the answer as it stands")
        void aBranchThatIsNotABlock() {
            assertThat(answerOf(new IfNative(), NOTHING, yes(), two())).isEqualTo(two());
            assertThat(answerOf(new UnlessNative(), NOTHING, no(), two())).isEqualTo(two());
        }

        @Test
        @DisplayName("zero and an empty string count as true, as Rebol says")
        void zeroAndEmptyAreTrue() {
            assertThat(answerOf(new IfNative(), NOTHING, IntegerValue.of(0), two()))
                    .isEqualTo(two());
            assertThat(answerOf(new IfNative(), NOTHING, StringValue.of(""), two()))
                    .isEqualTo(two());
            assertThat(answerOf(new UnlessNative(), NOTHING, IntegerValue.of(0), two()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("with /only a block branch comes back unevaluated")
        void onlyHandsTheBlockBack() {
            AnyBlockValue branch = aBlockHoldingOne();
            assertThat(answerOf(new IfNative(), ONLY, yes(), branch)).isSameAs(branch);
            assertThat(answerOf(new UnlessNative(), ONLY, no(), branch)).isSameAs(branch);
        }
    }

    @Nested
    @DisplayName("either picks one of its two branches")
    class Either {

        @Test
        @DisplayName("the first when the condition holds, the second when it fails")
        void picksByTheCondition() {
            assertThat(answerOf(new EitherNative(), NOTHING, yes(), one(), two()))
                    .isEqualTo(one());
            assertThat(answerOf(new EitherNative(), NOTHING, no(), one(), two()))
                    .isEqualTo(two());
            assertThat(answerOf(new EitherNative(), NOTHING, NoneValue.none(), one(), two()))
                    .isEqualTo(two());
        }

        @Test
        @DisplayName("with /only the chosen block comes back unevaluated")
        void onlyHandsTheChosenBlockBack() {
            AnyBlockValue first = aBlockHoldingOne();
            AnyBlockValue second = anEmptyBlock();
            assertThat(answerOf(new EitherNative(), ONLY, yes(), first, second))
                    .isSameAs(first);
            assertThat(answerOf(new EitherNative(), ONLY, no(), first, second))
                    .isSameAs(second);
        }
    }

    @Nested
    @DisplayName("not is true only for false and none")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Not {

        Stream<Arguments> everyKindOfTruth() {
            return Stream.of(
                    Arguments.of(no(), true),
                    Arguments.of(NoneValue.none(), true),
                    Arguments.of(yes(), false),
                    Arguments.of(IntegerValue.of(0), false),
                    Arguments.of(StringValue.of(""), false),
                    Arguments.of(anEmptyBlock(), false));
        }

        @ParameterizedTest(name = "not {0} is {1}")
        @MethodSource("everyKindOfTruth")
        void answersAsRebolDoes(Value given, boolean wanted) {
            assertThat(answerOf(new NotNative(), NOTHING, given))
                    .isEqualTo(LogicValue.of(wanted));
        }
    }

    @Nested
    @DisplayName("do hands back what is not code, and refuses half an expression")
    class Do {

        @Test
        @DisplayName("a value that is not code is its own answer")
        void aValueThatIsNotCode() {
            assertThat(answerOf(new DoNative(), NOTHING, IntegerValue.of(5)))
                    .isEqualTo(IntegerValue.of(5));
            assertThat(answerOf(new DoNative(), NOTHING, NoneValue.none()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a quoted word comes back as a plain word")
        void aQuotedWord() {
            assertThat(answerOf(new DoNative(), NOTHING, LitWordValue.of("x")))
                    .isEqualTo(WordValue.of("x"));
        }

        @Test
        @DisplayName("a set-word on its own is invalid-arg, since there is nothing to assign")
        void aSetWordOnItsOwn() {
            assertThatThrownBy(() -> answerOf(new DoNative(), NOTHING,
                    SetWordValue.of("x")))
                    .isInstanceOf(Raised.class)
                    .extracting(raised -> ((Raised) raised).error().errorId())
                    .isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("an empty block of choices answers without evaluating anything")
    class EmptyChoices {

        @Test
        @DisplayName("any of nothing is none")
        void anyOfNothing() {
            assertThat(answerOf(new AnyNative(), NOTHING, anEmptyBlock()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("all of nothing is unset, not true")
        void allOfNothing() {
            assertThat(answerOf(new AllNative(), NOTHING, anEmptyBlock()))
                    .isInstanceOf(UnsetValue.class);
        }

        @Test
        @DisplayName("case with no cases is none, with or without /all")
        void caseOfNothing() {
            assertThat(answerOf(new CaseNative(), NOTHING, anEmptyBlock()))
                    .isEqualTo(NoneValue.none());
            assertThat(answerOf(new CaseNative(), Set.of("all"), anEmptyBlock()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("switch with no match and no /default is none")
        void switchWithNoMatch() {
            AnyBlockValue cases = BlockValue.block(List.of(one(), aBlockHoldingOne()));
            assertThat(answerOf(new SwitchNative(), NOTHING, IntegerValue.of(9), cases))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("switch with a match but no block after it is none")
        void switchWithAMatchAndNoBranch() {
            AnyBlockValue cases = BlockValue.block(List.of(one()));
            assertThat(answerOf(new SwitchNative(), NOTHING, one(), cases))
                    .isEqualTo(NoneValue.none());
        }
    }
}
