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

    private Value makeOne() {
        return IntegerValue.of(1);
    }

    private Value makeTwo() {
        return IntegerValue.of(2);
    }

    private Value zero() {
        return IntegerValue.of(0);
    }

    private Value makeOneAsADecimal() {
        return DecimalValue.of(1.0);
    }

    private Stream<Arguments> listTheEqualityQuestions() {
        return Stream.of(
                Arguments.of(new EqualNative(), "equal?"),
                Arguments.of(new NotEqualNative(), "not-equal?"),
                Arguments.of(new EquivNative(), "equiv?"),
                Arguments.of(new NotEquivNative(), "not-equiv?"),
                Arguments.of(new StrictEqualNative(), "strict-equal?"),
                Arguments.of(new StrictNotEqualNative(), "strict-not-equal?"),
                Arguments.of(new SameNative(), "same?"));
    }

    private Stream<Arguments> listTheOrderQuestions() {
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

        Stream<Arguments> listEachComparison() {
            return Stream.concat(listTheEqualityQuestions(), listTheOrderQuestions());
        }

        Stream<Arguments> listEachEqualityQuestion() {
            return listTheEqualityQuestions();
        }

        Stream<Arguments> listEachOrderQuestion() {
            return listTheOrderQuestions();
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("listEachComparison")
        @DisplayName("named as Rebol spells it, with value1 and value2 and no refinement")
        void takeTwoValuesAndNoRefinement(DefaultNative function, String name) {
            assertThat(function.nativeName()).isEqualTo(name);
            assertThat(function.parametersAsWritten()).extracting(Parameter::name)
                    .containsExactly("value1", "value2");
            assertThat(function.refinementsDeclaredApart()).isEmpty();
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("listEachEqualityQuestion")
        @DisplayName("an equality question accepts every datatype there is")
        void acceptAnyTypeForEquality(DefaultNative function, String name) {
            assertThat(function.parametersAsWritten()).allSatisfy(parameter ->
                    assertThat(parameter.acceptedTypes())
                            .isEqualTo(TypesetValue.ANY_TYPE.members())
                            .contains(BlockValue.TYPE, NoneValue.TYPE, ObjectValue.TYPE));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("listEachOrderQuestion")
        @DisplayName("an order question takes a bare value, which leaves out unset")
        void takeABareValueForOrder(DefaultNative function, String name) {
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
                    Arguments.of(new EqualNative(), makeOne(), makeOneAsADecimal(), true),
                    Arguments.of(new EqualNative(), StringValue.of("abc"), StringValue.of("ABC"), true),
                    Arguments.of(new EqualNative(), makeOne(), makeTwo(), false),
                    Arguments.of(new EqualNative(), NoneValue.none(), NoneValue.none(), true),
                    Arguments.of(new EqualNative(), NoneValue.none(), makeOne(), false),
                    Arguments.of(new NotEqualNative(), makeOne(), makeTwo(), true),
                    Arguments.of(new NotEqualNative(), makeOne(), makeOneAsADecimal(), false),
                    Arguments.of(new EquivNative(), makeOne(), makeOneAsADecimal(), true),
                    Arguments.of(new EquivNative(), StringValue.of("a"), StringValue.of("A"), true),
                    Arguments.of(new EquivNative(), makeOne(), makeTwo(), false),
                    Arguments.of(new NotEquivNative(), makeOne(), makeTwo(), true),
                    Arguments.of(new NotEquivNative(), makeOne(), makeOneAsADecimal(), false),
                    Arguments.of(new StrictEqualNative(), makeOne(), makeOneAsADecimal(), false),
                    Arguments.of(new StrictEqualNative(), StringValue.of("abc"), StringValue.of("ABC"), false),
                    Arguments.of(new StrictEqualNative(), makeOne(), makeOne(), true),
                    Arguments.of(new StrictNotEqualNative(), makeOne(), makeOneAsADecimal(), true),
                    Arguments.of(new StrictNotEqualNative(), makeOne(), makeOne(), false),
                    Arguments.of(new SameNative(), makeOne(), makeOne(), true),
                    Arguments.of(new SameNative(), makeOne(), makeOneAsADecimal(), false),
                    Arguments.of(new SameNative(), NoneValue.none(), makeOne(), false));
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
                    Arguments.of(new GreaterNative(), makeTwo(), makeOne(), true),
                    Arguments.of(new GreaterNative(), makeOne(), makeOne(), false),
                    Arguments.of(new GreaterNative(), zero(), makeOne(), false),
                    Arguments.of(new GreaterOrEqualNative(), makeTwo(), makeOne(), true),
                    Arguments.of(new GreaterOrEqualNative(), makeOne(), makeOne(), true),
                    Arguments.of(new GreaterOrEqualNative(), zero(), makeOne(), false),
                    Arguments.of(new LesserNative(), makeTwo(), makeOne(), false),
                    Arguments.of(new LesserNative(), makeOne(), makeOne(), false),
                    Arguments.of(new LesserNative(), zero(), makeOne(), true),
                    Arguments.of(new LesserOrEqualNative(), makeTwo(), makeOne(), false),
                    Arguments.of(new LesserOrEqualNative(), makeOne(), makeOne(), true),
                    Arguments.of(new LesserOrEqualNative(), zero(), makeOne(), true));
        }

        Stream<Arguments> listEachOrderQuestion() {
            return listTheOrderQuestions();
        }

        @ParameterizedTest(name = "{0} {1} {2} is {3}")
        @MethodSource("answersRebolGives")
        void answersAsRebolDoes(DefaultNative function, Value left, Value right,
                                boolean wanted) {
            assertThat(answerOf(function, left, right)).isEqualTo(LogicValue.of(wanted));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("listEachOrderQuestion")
        @DisplayName("a value that has no order is refused rather than answered false")
        void refuseAValueWithNoOrder(DefaultNative function, String name) {
            assertThatThrownBy(() -> answerOf(function, NoneValue.none(), makeOne()))
                    .isInstanceOf(Raised.class)
                    .extracting(raised -> ((Raised) raised).error().errorId())
                    .isEqualTo("invalid-compare");
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("listEachOrderQuestion")
        @DisplayName("and so is a block set against a number")
        void refuseABlockAgainstANumber(DefaultNative function, String name) {
            assertThatThrownBy(() -> answerOf(function, BlockValue.of(List.of(makeOne())), makeOne()))
                    .isInstanceOf(Raised.class)
                    .extracting(raised -> ((Raised) raised).error().errorId())
                    .isEqualTo("invalid-compare");
        }
    }

    @Nested
    @DisplayName("each negative question is exactly the opposite of its positive twin")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TheNegativeTwins {

        Stream<Arguments> listEachTwinAndAPairOfValues() {
            List<List<Value>> pairs = List.of(
                    List.of(makeOne(), makeOne()),
                    List.of(makeOne(), makeTwo()),
                    List.of(makeOne(), makeOneAsADecimal()),
                    List.of(StringValue.of("abc"), StringValue.of("ABC")),
                    List.of(NoneValue.none(), makeOne()));
            List<List<DefaultNative>> twins = List.of(
                    List.of(new EqualNative(), new NotEqualNative()),
                    List.of(new EquivNative(), new NotEquivNative()),
                    List.of(new StrictEqualNative(), new StrictNotEqualNative()));
            return twins.stream().flatMap(twin -> pairs.stream().map(pair ->
                    Arguments.of(twin.get(0), twin.get(1), pair.get(0), pair.get(1))));
        }

        Stream<Arguments> listEachOrderTwinAndAPairOfValues() {
            List<List<Value>> pairs = List.of(
                    List.of(zero(), makeOne()),
                    List.of(makeOne(), makeOne()),
                    List.of(makeTwo(), makeOne()),
                    List.of(makeOne(), makeOneAsADecimal()));
            List<List<DefaultNative>> twins = List.of(
                    List.of(new GreaterOrEqualNative(), new LesserNative()),
                    List.of(new GreaterNative(), new LesserOrEqualNative()));
            return twins.stream().flatMap(twin -> pairs.stream().map(pair ->
                    Arguments.of(twin.get(0), twin.get(1), pair.get(0), pair.get(1))));
        }

        @ParameterizedTest(name = "{0} and {1} on {2} {3}")
        @MethodSource("listEachTwinAndAPairOfValues")
        void disagree(DefaultNative positive, DefaultNative negative,
                      Value left, Value right) {
            assertThat(answerOf(negative, left, right))
                    .isEqualTo(LogicValue.of(!answerOf(positive, left, right).isTruthy()));
        }

        @ParameterizedTest(name = "{0} and {1} on {2} {3}")
        @MethodSource("listEachOrderTwinAndAPairOfValues")
        void disagreeOnOrder(DefaultNative positive, DefaultNative negative,
                             Value left, Value right) {
            assertThat(answerOf(negative, left, right))
                    .isEqualTo(LogicValue.of(!answerOf(positive, left, right).isTruthy()));
        }
    }
}
