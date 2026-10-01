package org.jebol.domain.eval.definition;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatEachComparisonFunctionDeclaresTest {

    private static Value answerOf(FunctionDefinition function, Value left, Value right) {
        return function.behaviour().call(List.of(left, right), null, null, Set.of());
    }

    private static Value one() {
        return IntegerValue.of(1);
    }

    private static Value two() {
        return IntegerValue.of(2);
    }

    private static Value zero() {
        return IntegerValue.of(0);
    }

    private static Value oneAsADecimal() {
        return DecimalValue.of(1.0);
    }

    static Stream<Arguments> theEqualityQuestions() {
        return Stream.of(
                Arguments.of(new EqualFunction(), "equal?"),
                Arguments.of(new NotEqualFunction(), "not-equal?"),
                Arguments.of(new EquivFunction(), "equiv?"),
                Arguments.of(new NotEquivFunction(), "not-equiv?"),
                Arguments.of(new StrictEqualFunction(), "strict-equal?"),
                Arguments.of(new StrictNotEqualFunction(), "strict-not-equal?"),
                Arguments.of(new SameFunction(), "same?"));
    }

    static Stream<Arguments> theOrderQuestions() {
        return Stream.of(
                Arguments.of(new GreaterFunction(), "greater?"),
                Arguments.of(new GreaterOrEqualFunction(), "greater-or-equal?"),
                Arguments.of(new LesserFunction(), "lesser?"),
                Arguments.of(new LesserOrEqualFunction(), "lesser-or-equal?"));
    }

    static Stream<Arguments> everyComparison() {
        return Stream.concat(theEqualityQuestions(), theOrderQuestions());
    }

    @Nested
    @DisplayName("every comparison declares the same shape")
    class TheirShape {

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonFunctionDeclaresTest#everyComparison")
        @DisplayName("named as Rebol spells it, with value1 and value2 and no refinement")
        void twoValuesAndNoRefinement(FunctionDefinition function, String name) {
            assertThat(function.name()).isEqualTo(name);
            assertThat(function.parameters()).extracting(Parameter::name)
                    .containsExactly("value1", "value2");
            assertThat(function.refinements()).isEmpty();
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonFunctionDeclaresTest#theEqualityQuestions")
        @DisplayName("an equality question accepts every datatype there is")
        void anEqualityQuestionAcceptsAnyType(FunctionDefinition function, String name) {
            assertThat(function.parameters()).allSatisfy(parameter ->
                    assertThat(parameter.acceptedTypes())
                            .isEqualTo(Typeset.ANY_TYPE.members())
                            .contains(Datatype.BLOCK, Datatype.NONE, Datatype.OBJECT));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonFunctionDeclaresTest#theOrderQuestions")
        @DisplayName("an order question takes a bare value, which leaves out unset")
        void anOrderQuestionTakesABareValue(FunctionDefinition function, String name) {
            assertThat(function.parameters()).allSatisfy(parameter ->
                    assertThat(parameter.acceptedTypes()).isEmpty());
        }
    }

    @Nested
    @DisplayName("the equality questions answer at their own strictness")
    class TheEqualityAnswers {

        static Stream<Arguments> answersRebolGives() {
            return Stream.of(
                    Arguments.of(new EqualFunction(), one(), oneAsADecimal(), true),
                    Arguments.of(new EqualFunction(), StringValue.of("abc"), StringValue.of("ABC"), true),
                    Arguments.of(new EqualFunction(), one(), two(), false),
                    Arguments.of(new EqualFunction(), NoneValue.none(), NoneValue.none(), true),
                    Arguments.of(new EqualFunction(), NoneValue.none(), one(), false),
                    Arguments.of(new NotEqualFunction(), one(), two(), true),
                    Arguments.of(new NotEqualFunction(), one(), oneAsADecimal(), false),
                    Arguments.of(new EquivFunction(), one(), oneAsADecimal(), true),
                    Arguments.of(new EquivFunction(), StringValue.of("a"), StringValue.of("A"), true),
                    Arguments.of(new EquivFunction(), one(), two(), false),
                    Arguments.of(new NotEquivFunction(), one(), two(), true),
                    Arguments.of(new NotEquivFunction(), one(), oneAsADecimal(), false),
                    Arguments.of(new StrictEqualFunction(), one(), oneAsADecimal(), false),
                    Arguments.of(new StrictEqualFunction(), StringValue.of("abc"), StringValue.of("ABC"), false),
                    Arguments.of(new StrictEqualFunction(), one(), one(), true),
                    Arguments.of(new StrictNotEqualFunction(), one(), oneAsADecimal(), true),
                    Arguments.of(new StrictNotEqualFunction(), one(), one(), false),
                    Arguments.of(new SameFunction(), one(), one(), true),
                    Arguments.of(new SameFunction(), one(), oneAsADecimal(), false),
                    Arguments.of(new SameFunction(), NoneValue.none(), one(), false));
        }

        @ParameterizedTest(name = "{0} {1} {2} is {3}")
        @MethodSource("answersRebolGives")
        void answersAsRebolDoes(FunctionDefinition function, Value left, Value right,
                boolean wanted) {
            assertThat(answerOf(function, left, right)).isEqualTo(LogicValue.of(wanted));
        }
    }

    @Nested
    @DisplayName("the order questions answer either side of the boundary and on it")
    class TheOrderAnswers {

        static Stream<Arguments> answersRebolGives() {
            return Stream.of(
                    Arguments.of(new GreaterFunction(), two(), one(), true),
                    Arguments.of(new GreaterFunction(), one(), one(), false),
                    Arguments.of(new GreaterFunction(), zero(), one(), false),
                    Arguments.of(new GreaterOrEqualFunction(), two(), one(), true),
                    Arguments.of(new GreaterOrEqualFunction(), one(), one(), true),
                    Arguments.of(new GreaterOrEqualFunction(), zero(), one(), false),
                    Arguments.of(new LesserFunction(), two(), one(), false),
                    Arguments.of(new LesserFunction(), one(), one(), false),
                    Arguments.of(new LesserFunction(), zero(), one(), true),
                    Arguments.of(new LesserOrEqualFunction(), two(), one(), false),
                    Arguments.of(new LesserOrEqualFunction(), one(), one(), true),
                    Arguments.of(new LesserOrEqualFunction(), zero(), one(), true));
        }

        @ParameterizedTest(name = "{0} {1} {2} is {3}")
        @MethodSource("answersRebolGives")
        void answersAsRebolDoes(FunctionDefinition function, Value left, Value right,
                boolean wanted) {
            assertThat(answerOf(function, left, right)).isEqualTo(LogicValue.of(wanted));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonFunctionDeclaresTest#theOrderQuestions")
        @DisplayName("a value that has no order is refused rather than answered false")
        void aValueWithNoOrderIsRefused(FunctionDefinition function, String name) {
            assertThatThrownBy(() -> answerOf(function, NoneValue.none(), one()))
                    .isInstanceOf(Raised.class)
                    .extracting(raised -> ((Raised) raised).error().errorId())
                    .isEqualTo("invalid-compare");
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonFunctionDeclaresTest#theOrderQuestions")
        @DisplayName("and so is a block set against a number")
        void aBlockAgainstANumberIsRefused(FunctionDefinition function, String name) {
            assertThatThrownBy(() -> answerOf(function, BlockValue.block(List.of(one())), one()))
                    .isInstanceOf(Raised.class)
                    .extracting(raised -> ((Raised) raised).error().errorId())
                    .isEqualTo("invalid-compare");
        }
    }

    @Nested
    @DisplayName("each negative question is exactly the opposite of its positive twin")
    class TheNegativeTwins {

        static Stream<Arguments> eachTwinAndAPairOfValues() {
            List<List<Value>> pairs = List.of(
                    List.of(one(), one()),
                    List.of(one(), two()),
                    List.of(one(), oneAsADecimal()),
                    List.of(StringValue.of("abc"), StringValue.of("ABC")),
                    List.of(NoneValue.none(), one()));
            List<List<FunctionDefinition>> twins = List.of(
                    List.of(new EqualFunction(), new NotEqualFunction()),
                    List.of(new EquivFunction(), new NotEquivFunction()),
                    List.of(new StrictEqualFunction(), new StrictNotEqualFunction()));
            return twins.stream().flatMap(twin -> pairs.stream().map(pair ->
                    Arguments.of(twin.get(0), twin.get(1), pair.get(0), pair.get(1))));
        }

        @ParameterizedTest(name = "{0} and {1} on {2} {3}")
        @MethodSource("eachTwinAndAPairOfValues")
        void theTwinsDisagree(FunctionDefinition positive, FunctionDefinition negative,
                Value left, Value right) {
            assertThat(answerOf(negative, left, right))
                    .isEqualTo(LogicValue.of(!answerOf(positive, left, right).isTruthy()));
        }

        static Stream<Arguments> eachOrderTwinAndAPairOfValues() {
            List<List<Value>> pairs = List.of(
                    List.of(zero(), one()),
                    List.of(one(), one()),
                    List.of(two(), one()),
                    List.of(one(), oneAsADecimal()));
            List<List<FunctionDefinition>> twins = List.of(
                    List.of(new GreaterOrEqualFunction(), new LesserFunction()),
                    List.of(new GreaterFunction(), new LesserOrEqualFunction()));
            return twins.stream().flatMap(twin -> pairs.stream().map(pair ->
                    Arguments.of(twin.get(0), twin.get(1), pair.get(0), pair.get(1))));
        }

        @ParameterizedTest(name = "{0} and {1} on {2} {3}")
        @MethodSource("eachOrderTwinAndAPairOfValues")
        void theOrderTwinsDisagree(FunctionDefinition positive, FunctionDefinition negative,
                Value left, Value right) {
            assertThat(answerOf(negative, left, right))
                    .isEqualTo(LogicValue.of(!answerOf(positive, left, right).isTruthy()));
        }
    }
}
