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

    private static final Set<Datatype> A_BLOCK = Set.of(BlockValue.TYPE);

    private Value answerOf(DefaultNative definition, Set<String> refinements,
                           Value... arguments) {
        return definition.behaviour().call(List.of(arguments), null, null, refinements);
    }

    private Value yes() {
        return LogicValue.of(true);
    }

    private Value makeNo() {
        return LogicValue.of(false);
    }

    private Value makeOne() {
        return IntegerValue.of(1);
    }

    private Value makeTwo() {
        return IntegerValue.of(2);
    }

    private AnyBlockValue makeABlockHoldingOne() {
        return BlockValue.of(List.of(IntegerValue.of(1)));
    }

    private AnyBlockValue makeAnEmptyBlock() {
        return BlockValue.of(List.of());
    }

    private Parameter acceptAnyType(String name) {
        return Parameter.of(name, TypesetValue.ANY_TYPE.members());
    }

    Stream<Arguments> listWhatEachDeclares() {
        return Stream.of(
                Arguments.of(new IfNative(), "if",
                        List.of(acceptAnyType("condition"), acceptAnyType("branch")), ONLY),
                Arguments.of(new UnlessNative(), "unless",
                        List.of(acceptAnyType("condition"), acceptAnyType("branch")), ONLY),
                Arguments.of(new EitherNative(), "either",
                        List.of(acceptAnyType("condition"), acceptAnyType("true-branch"),
                                acceptAnyType("false-branch")), ONLY),
                Arguments.of(new NotNative(), "not",
                        List.of(acceptAnyType("value")), NOTHING),
                Arguments.of(new AnyNative(), "any",
                        List.of(Parameter.of("block", A_BLOCK)), NOTHING),
                Arguments.of(new AllNative(), "all",
                        List.of(Parameter.of("block", A_BLOCK)), NOTHING),
                Arguments.of(new CaseNative(), "case",
                        List.of(Parameter.of("choices", A_BLOCK)), Set.of("all")),
                Arguments.of(new SwitchNative(), "switch",
                        List.of(Parameter.of("value"),
                                Parameter.of("choices", A_BLOCK),
                                Parameter.belongTo("default", "fallback", A_BLOCK)),
                        Set.of("case", "default", "all")),
                Arguments.of(new AttemptNative(), "attempt",
                        List.of(Parameter.of("block",
                                Set.of(BlockValue.TYPE, ParenValue.TYPE))),
                        Set.of("safer")),
                Arguments.of(new TryNative(), "try",
                        List.of(Parameter.of("block",
                                        Set.of(BlockValue.TYPE, ParenValue.TYPE)),
                                Parameter.belongTo("with", "handler", Set.of())),
                        Set.of("all", "with")),
                Arguments.of(new DoNative(), "do",
                        List.of(acceptAnyType("value"),
                                Parameter.belongTo("args", "arg", Set.of()),
                                Parameter.belongTo("next", "var", Set.of(WordValue.TYPE))),
                        Set.of("next", "args")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("listWhatEachDeclares")
    @DisplayName("each declares exactly what the inline definition declared")
    void declaresWhatItDeclaredInline(DefaultNative definition, String name,
            List<Parameter> parameters, Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.parametersAsWritten()).isEqualTo(parameters);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    @Test
    @DisplayName("switch's fallback arrives only with /default")
    void givesTheFallbackToDefault() {
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
            assertThat(answerOf(new IfNative(), NOTHING, makeNo(), makeABlockHoldingOne()))
                    .isEqualTo(NoneValue.none());
            assertThat(answerOf(new IfNative(), NOTHING, NoneValue.none(), makeOne()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("unless answers none when the condition holds")
        void unlessOnAHeldCondition() {
            assertThat(answerOf(new UnlessNative(), NOTHING, yes(), makeABlockHoldingOne()))
                    .isEqualTo(NoneValue.none());
            assertThat(answerOf(new UnlessNative(), NOTHING, makeOne(), makeOne()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a branch that is not a block is the answer as it stands")
        void takeABranchThatIsNotABlock() {
            assertThat(answerOf(new IfNative(), NOTHING, yes(), makeTwo())).isEqualTo(makeTwo());
            assertThat(answerOf(new UnlessNative(), NOTHING, makeNo(), makeTwo())).isEqualTo(makeTwo());
        }

        @Test
        @DisplayName("zero and an empty string count as true, as Rebol says")
        void zeroAndEmptyAreTrue() {
            assertThat(answerOf(new IfNative(), NOTHING, IntegerValue.of(0), makeTwo()))
                    .isEqualTo(makeTwo());
            assertThat(answerOf(new IfNative(), NOTHING, StringValue.of(""), makeTwo()))
                    .isEqualTo(makeTwo());
            assertThat(answerOf(new UnlessNative(), NOTHING, IntegerValue.of(0), makeTwo()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("with /only a block branch comes back unevaluated")
        void handOnlyTheBlockBack() {
            AnyBlockValue branch = makeABlockHoldingOne();
            assertThat(answerOf(new IfNative(), ONLY, yes(), branch)).isSameAs(branch);
            assertThat(answerOf(new UnlessNative(), ONLY, makeNo(), branch)).isSameAs(branch);
        }
    }

    @Nested
    @DisplayName("either picks one of its two branches")
    class Either {

        @Test
        @DisplayName("the first when the condition holds, the second when it fails")
        void picksByTheCondition() {
            assertThat(answerOf(new EitherNative(), NOTHING, yes(), makeOne(), makeTwo()))
                    .isEqualTo(makeOne());
            assertThat(answerOf(new EitherNative(), NOTHING, makeNo(), makeOne(), makeTwo()))
                    .isEqualTo(makeTwo());
            assertThat(answerOf(new EitherNative(), NOTHING, NoneValue.none(), makeOne(), makeTwo()))
                    .isEqualTo(makeTwo());
        }

        @Test
        @DisplayName("with /only the chosen block comes back unevaluated")
        void handsOnlyTheChosenBlockBack() {
            AnyBlockValue first = makeABlockHoldingOne();
            AnyBlockValue second = makeAnEmptyBlock();
            assertThat(answerOf(new EitherNative(), ONLY, yes(), first, second))
                    .isSameAs(first);
            assertThat(answerOf(new EitherNative(), ONLY, makeNo(), first, second))
                    .isSameAs(second);
        }
    }

    @Nested
    @DisplayName("not is true only for false and none")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Not {

        Stream<Arguments> listEveryKindOfTruth() {
            return Stream.of(
                    Arguments.of(makeNo(), true),
                    Arguments.of(NoneValue.none(), true),
                    Arguments.of(yes(), false),
                    Arguments.of(IntegerValue.of(0), false),
                    Arguments.of(StringValue.of(""), false),
                    Arguments.of(makeAnEmptyBlock(), false));
        }

        @ParameterizedTest(name = "not {0} is {1}")
        @MethodSource("listEveryKindOfTruth")
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
        void takesAValueThatIsNotCode() {
            assertThat(answerOf(new DoNative(), NOTHING, IntegerValue.of(5)))
                    .isEqualTo(IntegerValue.of(5));
            assertThat(answerOf(new DoNative(), NOTHING, NoneValue.none()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a quoted word comes back as a plain word")
        void takesAQuotedWord() {
            assertThat(answerOf(new DoNative(), NOTHING, LitWordValue.of("x")))
                    .isEqualTo(WordValue.of("x"));
        }

        @Test
        @DisplayName("a set-word on its own is invalid-arg, since there is nothing to assign")
        void takesASetWordOnItsOwn() {
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
        void answerAnyOfNothing() {
            assertThat(answerOf(new AnyNative(), NOTHING, makeAnEmptyBlock()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("all of nothing is unset, not true")
        void answerAllOfNothing() {
            assertThat(answerOf(new AllNative(), NOTHING, makeAnEmptyBlock()))
                    .isInstanceOf(UnsetValue.class);
        }

        @Test
        @DisplayName("case with no cases is none, with or without /all")
        void answerCaseOfNothing() {
            assertThat(answerOf(new CaseNative(), NOTHING, makeAnEmptyBlock()))
                    .isEqualTo(NoneValue.none());
            assertThat(answerOf(new CaseNative(), Set.of("all"), makeAnEmptyBlock()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("switch with no match and no /default is none")
        void switchWithNoMatch() {
            AnyBlockValue cases = BlockValue.of(List.of(makeOne(), makeABlockHoldingOne()));
            assertThat(answerOf(new SwitchNative(), NOTHING, IntegerValue.of(9), cases))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("switch with a match but no block after it is none")
        void switchWithAMatchAndNoBranch() {
            AnyBlockValue cases = BlockValue.of(List.of(makeOne()));
            assertThat(answerOf(new SwitchNative(), NOTHING, makeOne(), cases))
                    .isEqualTo(NoneValue.none());
        }
    }
}
