package org.jebol.domain.eval.natives;

import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatEachComparisonNativeDeclaresTest {

    private Value answerOf(DefaultNative function, Value left, Value right) {
        return function.behaviour().call(List.of(left, right), null, null, Set.of());
    }

    private Value one() {
        return IntegerValue.of(1);
    }

    private Value two() {
        return IntegerValue.of(2);
    }

    private Value zero() {
        return IntegerValue.of(0);
    }

    private Value oneAsADecimal() {
        return DecimalValue.of(1.0);
    }

    private Stream<Arguments> theEqualityQuestions() {
        return Stream.of(
                Arguments.of(new EqualNative(), "equal?"),
                Arguments.of(new NotEqualNative(), "not-equal?"),
                Arguments.of(new EquivNative(), "equiv?"),
                Arguments.of(new NotEquivNative(), "not-equiv?"),
                Arguments.of(new StrictEqualNative(), "strict-equal?"),
                Arguments.of(new StrictNotEqualNative(), "strict-not-equal?"),
                Arguments.of(new SameNative(), "same?"));
    }

    private Stream<Arguments> theOrderQuestions() {
        return Stream.of(
                Arguments.of(new GreaterNative(), "greater?"),
                Arguments.of(new GreaterOrEqualNative(), "greater-or-equal?"),
                Arguments.of(new LesserNative(), "lesser?"),
                Arguments.of(new LesserOrEqualNative(), "lesser-or-equal?"));
    }

    @Nested
    @DisplayName("every comparison declares the same shape")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TheirShape {

        Stream<Arguments> eachComparison() {
            return Stream.concat(theEqualityQuestions(), theOrderQuestions());
        }

        Stream<Arguments> eachEqualityQuestion() {
            return theEqualityQuestions();
        }

        Stream<Arguments> eachOrderQuestion() {
            return theOrderQuestions();
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("eachComparison")
        @DisplayName("named as Rebol spells it, with value1 and value2 and no refinement")
        void twoValuesAndNoRefinement(DefaultNative function, String name) {
            assertThat(function.nativeName()).isEqualTo(name);
            assertThat(function.parametersAsWritten()).extracting(Parameter::name)
                    .containsExactly("value1", "value2");
            assertThat(function.refinementsDeclaredApart()).isEmpty();
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("eachEqualityQuestion")
        @DisplayName("an equality question accepts every datatype there is")
        void anEqualityQuestionAcceptsAnyType(DefaultNative function, String name) {
            assertThat(function.parametersAsWritten()).allSatisfy(parameter ->
                    assertThat(parameter.acceptedTypes())
                            .isEqualTo(Typeset.ANY_TYPE.members())
                            .contains(Datatype.BLOCK, Datatype.NONE, Datatype.OBJECT));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("eachOrderQuestion")
        @DisplayName("an order question takes a bare value, which leaves out unset")
        void anOrderQuestionTakesABareValue(DefaultNative function, String name) {
            assertThat(function.parametersAsWritten()).allSatisfy(parameter ->
                    assertThat(parameter.acceptedTypes()).isEmpty());
        }
    }

    @Nested
    @DisplayName("the equality questions answer at their own strictness")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TheEqualityAnswers {

        Stream<Arguments> answersRebolGives() {
            return Stream.of(
                    Arguments.of(new EqualNative(), one(), oneAsADecimal(), true),
                    Arguments.of(new EqualNative(), StringValue.of("abc"), StringValue.of("ABC"), true),
                    Arguments.of(new EqualNative(), one(), two(), false),
                    Arguments.of(new EqualNative(), NoneValue.none(), NoneValue.none(), true),
                    Arguments.of(new EqualNative(), NoneValue.none(), one(), false),
                    Arguments.of(new NotEqualNative(), one(), two(), true),
                    Arguments.of(new NotEqualNative(), one(), oneAsADecimal(), false),
                    Arguments.of(new EquivNative(), one(), oneAsADecimal(), true),
                    Arguments.of(new EquivNative(), StringValue.of("a"), StringValue.of("A"), true),
                    Arguments.of(new EquivNative(), one(), two(), false),
                    Arguments.of(new NotEquivNative(), one(), two(), true),
                    Arguments.of(new NotEquivNative(), one(), oneAsADecimal(), false),
                    Arguments.of(new StrictEqualNative(), one(), oneAsADecimal(), false),
                    Arguments.of(new StrictEqualNative(), StringValue.of("abc"), StringValue.of("ABC"), false),
                    Arguments.of(new StrictEqualNative(), one(), one(), true),
                    Arguments.of(new StrictNotEqualNative(), one(), oneAsADecimal(), true),
                    Arguments.of(new StrictNotEqualNative(), one(), one(), false),
                    Arguments.of(new SameNative(), one(), one(), true),
                    Arguments.of(new SameNative(), one(), oneAsADecimal(), false),
                    Arguments.of(new SameNative(), NoneValue.none(), one(), false));
        }

        @ParameterizedTest(name = "{0} {1} {2} is {3}")
        @MethodSource("answersRebolGives")
        void answersAsRebolDoes(DefaultNative function, Value left, Value right,
                                boolean wanted) {
            assertThat(answerOf(function, left, right)).isEqualTo(LogicValue.of(wanted));
        }
    }

    @Nested
    @DisplayName("the order questions answer either side of the boundary and on it")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TheOrderAnswers {

        Stream<Arguments> answersRebolGives() {
            return Stream.of(
                    Arguments.of(new GreaterNative(), two(), one(), true),
                    Arguments.of(new GreaterNative(), one(), one(), false),
                    Arguments.of(new GreaterNative(), zero(), one(), false),
                    Arguments.of(new GreaterOrEqualNative(), two(), one(), true),
                    Arguments.of(new GreaterOrEqualNative(), one(), one(), true),
                    Arguments.of(new GreaterOrEqualNative(), zero(), one(), false),
                    Arguments.of(new LesserNative(), two(), one(), false),
                    Arguments.of(new LesserNative(), one(), one(), false),
                    Arguments.of(new LesserNative(), zero(), one(), true),
                    Arguments.of(new LesserOrEqualNative(), two(), one(), false),
                    Arguments.of(new LesserOrEqualNative(), one(), one(), true),
                    Arguments.of(new LesserOrEqualNative(), zero(), one(), true));
        }

        Stream<Arguments> eachOrderQuestion() {
            return theOrderQuestions();
        }

        @ParameterizedTest(name = "{0} {1} {2} is {3}")
        @MethodSource("answersRebolGives")
        void answersAsRebolDoes(DefaultNative function, Value left, Value right,
                                boolean wanted) {
            assertThat(answerOf(function, left, right)).isEqualTo(LogicValue.of(wanted));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("eachOrderQuestion")
        @DisplayName("a value that has no order is refused rather than answered false")
        void aValueWithNoOrderIsRefused(DefaultNative function, String name) {
            assertThatThrownBy(() -> answerOf(function, NoneValue.none(), one()))
                    .isInstanceOf(Raised.class)
                    .extracting(raised -> ((Raised) raised).error().errorId())
                    .isEqualTo("invalid-compare");
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("eachOrderQuestion")
        @DisplayName("and so is a block set against a number")
        void aBlockAgainstANumberIsRefused(DefaultNative function, String name) {
            assertThatThrownBy(() -> answerOf(function, BlockValue.block(List.of(one())), one()))
                    .isInstanceOf(Raised.class)
                    .extracting(raised -> ((Raised) raised).error().errorId())
                    .isEqualTo("invalid-compare");
        }
    }

    @Nested
    @DisplayName("each negative question is exactly the opposite of its positive twin")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TheNegativeTwins {

        Stream<Arguments> eachTwinAndAPairOfValues() {
            List<List<Value>> pairs = List.of(
                    List.of(one(), one()),
                    List.of(one(), two()),
                    List.of(one(), oneAsADecimal()),
                    List.of(StringValue.of("abc"), StringValue.of("ABC")),
                    List.of(NoneValue.none(), one()));
            List<List<DefaultNative>> twins = List.of(
                    List.of(new EqualNative(), new NotEqualNative()),
                    List.of(new EquivNative(), new NotEquivNative()),
                    List.of(new StrictEqualNative(), new StrictNotEqualNative()));
            return twins.stream().flatMap(twin -> pairs.stream().map(pair ->
                    Arguments.of(twin.get(0), twin.get(1), pair.get(0), pair.get(1))));
        }

        Stream<Arguments> eachOrderTwinAndAPairOfValues() {
            List<List<Value>> pairs = List.of(
                    List.of(zero(), one()),
                    List.of(one(), one()),
                    List.of(two(), one()),
                    List.of(one(), oneAsADecimal()));
            List<List<DefaultNative>> twins = List.of(
                    List.of(new GreaterOrEqualNative(), new LesserNative()),
                    List.of(new GreaterNative(), new LesserOrEqualNative()));
            return twins.stream().flatMap(twin -> pairs.stream().map(pair ->
                    Arguments.of(twin.get(0), twin.get(1), pair.get(0), pair.get(1))));
        }

        @ParameterizedTest(name = "{0} and {1} on {2} {3}")
        @MethodSource("eachTwinAndAPairOfValues")
        void theTwinsDisagree(DefaultNative positive, DefaultNative negative,
                              Value left, Value right) {
            assertThat(answerOf(negative, left, right))
                    .isEqualTo(LogicValue.of(!answerOf(positive, left, right).isTruthy()));
        }

        @ParameterizedTest(name = "{0} and {1} on {2} {3}")
        @MethodSource("eachOrderTwinAndAPairOfValues")
        void theOrderTwinsDisagree(DefaultNative positive, DefaultNative negative,
                                   Value left, Value right) {
            assertThat(answerOf(negative, left, right))
                    .isEqualTo(LogicValue.of(!answerOf(positive, left, right).isTruthy()));
        }
    }
}
