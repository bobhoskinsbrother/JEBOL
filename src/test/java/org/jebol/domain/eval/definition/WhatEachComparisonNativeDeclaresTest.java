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

class WhatEachComparisonNativeDeclaresTest {

    private static Value answerOf(NativeDefinition function, Value left, Value right) {
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
                Arguments.of(new EqualNative(), "equal?"),
                Arguments.of(new NotEqualNative(), "not-equal?"),
                Arguments.of(new EquivNative(), "equiv?"),
                Arguments.of(new NotEquivNative(), "not-equiv?"),
                Arguments.of(new StrictEqualNative(), "strict-equal?"),
                Arguments.of(new StrictNotEqualNative(), "strict-not-equal?"),
                Arguments.of(new SameNative(), "same?"));
    }

    static Stream<Arguments> theOrderQuestions() {
        return Stream.of(
                Arguments.of(new GreaterNative(), "greater?"),
                Arguments.of(new GreaterOrEqualNative(), "greater-or-equal?"),
                Arguments.of(new LesserNative(), "lesser?"),
                Arguments.of(new LesserOrEqualNative(), "lesser-or-equal?"));
    }

    static Stream<Arguments> everyComparison() {
        return Stream.concat(theEqualityQuestions(), theOrderQuestions());
    }

    @Nested
    @DisplayName("every comparison declares the same shape")
    class TheirShape {

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonNativeDeclaresTest#everyComparison")
        @DisplayName("named as Rebol spells it, with value1 and value2 and no refinement")
        void twoValuesAndNoRefinement(NativeDefinition function, String name) {
            assertThat(function.name()).isEqualTo(name);
            assertThat(function.parameters()).extracting(Parameter::name)
                    .containsExactly("value1", "value2");
            assertThat(function.refinements()).isEmpty();
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonNativeDeclaresTest#theEqualityQuestions")
        @DisplayName("an equality question accepts every datatype there is")
        void anEqualityQuestionAcceptsAnyType(NativeDefinition function, String name) {
            assertThat(function.parameters()).allSatisfy(parameter ->
                    assertThat(parameter.acceptedTypes())
                            .isEqualTo(Typeset.ANY_TYPE.members())
                            .contains(Datatype.BLOCK, Datatype.NONE, Datatype.OBJECT));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonNativeDeclaresTest#theOrderQuestions")
        @DisplayName("an order question takes a bare value, which leaves out unset")
        void anOrderQuestionTakesABareValue(NativeDefinition function, String name) {
            assertThat(function.parameters()).allSatisfy(parameter ->
                    assertThat(parameter.acceptedTypes()).isEmpty());
        }
    }

    @Nested
    @DisplayName("the equality questions answer at their own strictness")
    class TheEqualityAnswers {

        static Stream<Arguments> answersRebolGives() {
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
        void answersAsRebolDoes(NativeDefinition function, Value left, Value right,
                                boolean wanted) {
            assertThat(answerOf(function, left, right)).isEqualTo(LogicValue.of(wanted));
        }
    }

    @Nested
    @DisplayName("the order questions answer either side of the boundary and on it")
    class TheOrderAnswers {

        static Stream<Arguments> answersRebolGives() {
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

        @ParameterizedTest(name = "{0} {1} {2} is {3}")
        @MethodSource("answersRebolGives")
        void answersAsRebolDoes(NativeDefinition function, Value left, Value right,
                                boolean wanted) {
            assertThat(answerOf(function, left, right)).isEqualTo(LogicValue.of(wanted));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonNativeDeclaresTest#theOrderQuestions")
        @DisplayName("a value that has no order is refused rather than answered false")
        void aValueWithNoOrderIsRefused(NativeDefinition function, String name) {
            assertThatThrownBy(() -> answerOf(function, NoneValue.none(), one()))
                    .isInstanceOf(Raised.class)
                    .extracting(raised -> ((Raised) raised).error().errorId())
                    .isEqualTo("invalid-compare");
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("org.jebol.domain.eval.definition.WhatEachComparisonNativeDeclaresTest#theOrderQuestions")
        @DisplayName("and so is a block set against a number")
        void aBlockAgainstANumberIsRefused(NativeDefinition function, String name) {
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
            List<List<NativeDefinition>> twins = List.of(
                    List.of(new EqualNative(), new NotEqualNative()),
                    List.of(new EquivNative(), new NotEquivNative()),
                    List.of(new StrictEqualNative(), new StrictNotEqualNative()));
            return twins.stream().flatMap(twin -> pairs.stream().map(pair ->
                    Arguments.of(twin.get(0), twin.get(1), pair.get(0), pair.get(1))));
        }

        @ParameterizedTest(name = "{0} and {1} on {2} {3}")
        @MethodSource("eachTwinAndAPairOfValues")
        void theTwinsDisagree(NativeDefinition positive, NativeDefinition negative,
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
            List<List<NativeDefinition>> twins = List.of(
                    List.of(new GreaterOrEqualNative(), new LesserNative()),
                    List.of(new GreaterNative(), new LesserOrEqualNative()));
            return twins.stream().flatMap(twin -> pairs.stream().map(pair ->
                    Arguments.of(twin.get(0), twin.get(1), pair.get(0), pair.get(1))));
        }

        @ParameterizedTest(name = "{0} and {1} on {2} {3}")
        @MethodSource("eachOrderTwinAndAPairOfValues")
        void theOrderTwinsDisagree(NativeDefinition positive, NativeDefinition negative,
                                   Value left, Value right) {
            assertThat(answerOf(negative, left, right))
                    .isEqualTo(LogicValue.of(!answerOf(positive, left, right).isTruthy()));
        }
    }
}
