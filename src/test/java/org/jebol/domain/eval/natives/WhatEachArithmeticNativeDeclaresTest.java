package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.actions.AbsoluteAction;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WhatEachArithmeticNativeDeclaresTest {

    private static final Set<String> NOTHING = Set.of();

    private static final Set<String> IN_RADIANS = Set.of("radians");

    private Value answerOf(DefaultNative function, Set<String> refinements,
                           Value... arguments) {
        return function.behaviour().call(List.of(arguments), null, null, refinements);
    }

    private double quantityOf(Value answered) {
        return ((AnyDecimalValue) answered).quantity();
    }

    @Nested
    @DisplayName("the trigonometric functions take an angle, in degrees unless asked")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TheTrigonometricOnes {

        Stream<Arguments> eachOne() {
            return Stream.of(
                    Arguments.of(new SineNative(), "sine", 0.0, 1.0),
                    Arguments.of(new CosineNative(), "cosine", 1.0, 0.0),
                    Arguments.of(new TangentNative(), "tangent", 0.0, Double.NaN));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("eachOne")
        @DisplayName("zero degrees and zero radians give the same answer")
        void zeroIsZeroEitherWay(
                DefaultNative function, String name, double atZero, double ignored) {
            assertThat(quantityOf(answerOf(function, NOTHING, IntegerValue.of(0))))
                    .isEqualTo(atZero);
            assertThat(quantityOf(answerOf(function, IN_RADIANS, IntegerValue.of(0))))
                    .isEqualTo(atZero);
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("eachOne")
        @DisplayName("and each declares the radians refinement and one number")
        void eachDeclaresTheSameShape(
                DefaultNative function, String name, double a, double b) {
            assertThat(function.nativeName()).isEqualTo(name);
            assertThat(function.refinementsDeclaredApart()).containsExactly("radians");
            assertThat(function.parametersAsWritten()).hasSize(1);
        }

        @Test
        @DisplayName("ninety degrees is a right angle, and the same in radians")
        void aRightAngleEitherWay() {
            assertThat(quantityOf(answerOf(new SineNative(), NOTHING,
                    IntegerValue.of(90)))).isEqualTo(1.0);
            assertThat(quantityOf(answerOf(new SineNative(), IN_RADIANS,
                    DecimalValue.of(Math.PI / 2)))).isEqualTo(1.0);
        }

        @Test
        @DisplayName("a tangent at a right angle is infinite rather than enormous")
        void aTangentAtTheAsymptote() {
            assertThat(quantityOf(answerOf(new TangentNative(), NOTHING,
                    IntegerValue.of(90)))).isEqualTo(Double.POSITIVE_INFINITY);
            assertThat(quantityOf(answerOf(new TangentNative(), NOTHING,
                    IntegerValue.of(-90)))).isEqualTo(Double.NEGATIVE_INFINITY);
        }
    }

    @Nested
    @DisplayName("the inverse trigonometric functions answer an angle")
    class TheInverseOnes {

        @Test
        @DisplayName("arcsine of one is ninety degrees, or half pi in radians")
        void arcsineOfOne() {
            assertThat(quantityOf(answerOf(new ArcsineNative(), NOTHING,
                    IntegerValue.of(1)))).isEqualTo(90.0);
            assertThat(quantityOf(answerOf(new ArcsineNative(), IN_RADIANS,
                    IntegerValue.of(1)))).isEqualTo(Math.PI / 2);
        }

        @Test
        @DisplayName("arccosine of one is no angle at all, either way round")
        void arccosineOfOne() {
            assertThat(quantityOf(answerOf(new ArccosineNative(), NOTHING,
                    IntegerValue.of(1)))).isEqualTo(0.0);
            assertThat(quantityOf(answerOf(new ArccosineNative(), IN_RADIANS,
                    IntegerValue.of(1)))).isEqualTo(0.0);
        }

        @Test
        @DisplayName("arctangent of one is forty-five degrees")
        void arctangentOfOne() {
            assertThat(quantityOf(answerOf(new ArctangentNative(), NOTHING,
                    IntegerValue.of(1)))).isEqualTo(45.0);
        }
    }

    @Nested
    @DisplayName("the logarithms and the exponential each take one number")
    class TheLogarithms {

        @Test
        @DisplayName("each answers what its own base says")
        void eachAtItsOwnBase() {
            assertThat(quantityOf(answerOf(new NaturalLogarithmNative(), NOTHING,
                    IntegerValue.of(1)))).isEqualTo(0.0);
            assertThat(quantityOf(answerOf(new CommonLogarithmNative(), NOTHING,
                    IntegerValue.of(100)))).isEqualTo(2.0);
            assertThat(quantityOf(answerOf(new BinaryLogarithmNative(), NOTHING,
                    IntegerValue.of(8)))).isEqualTo(3.0);
            assertThat(quantityOf(answerOf(new ExponentialNative(), NOTHING,
                    IntegerValue.of(0)))).isEqualTo(1.0);
        }

        @Test
        @DisplayName("and none of them takes a refinement")
        void noneTakesARefinement() {
            for (DefaultNative function : List.of(
                    new NaturalLogarithmNative(), new CommonLogarithmNative(),
                    new BinaryLogarithmNative(), new ExponentialNative())) {
                assertThat(function.refinementsDeclaredApart()).as(function.nativeName()).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("absolute asks the value, so every datatype keeps its own")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class AbsoluteKeepsTheDatatype {

        Stream<Arguments> eachMeasurableDatatype() {
            return Stream.of(
                    Arguments.of(IntegerValue.of(-7), IntegerValue.of(7)),
                    Arguments.of(DecimalValue.of(-7.5), DecimalValue.of(7.5)),
                    Arguments.of(PercentValue.of(-2.0), PercentValue.of(2.0)),
                    Arguments.of(PairValue.of(-1, -2), PairValue.of(1, 2)),
                    Arguments.of(new TimeValue(-7L), new TimeValue(7L)),
                    Arguments.of(new MoneyValue(BigDecimal.valueOf(-7), Optional.empty()),
                            new MoneyValue(BigDecimal.valueOf(7), Optional.empty())));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("eachMeasurableDatatype")
        void theAnswerIsTheSameDatatype(Value given, Value wanted) {
            Value answered = answerOf(new AbsoluteAction(), NOTHING, given);
            assertThat(answered.datatype()).isEqualTo(given.datatype());
            assertThat(answered).isEqualTo(wanted);
        }

        @Test
        @DisplayName("a percent stays a percent rather than dropping to a decimal")
        void aPercentStaysAPercent() {
            assertThat(answerOf(new AbsoluteAction(), NOTHING,
                    PercentValue.of(-2.0)).datatype())
                    .isEqualTo(Datatype.PERCENT);
        }
    }

    @Nested
    @DisplayName("what each one declares it accepts")
    class WhatTheyAccept {

        @Test
        @DisplayName("integer-divide takes two, the rest of these take one")
        void theirArity() {
            assertThat(new IntegerDivideNative().parametersAsWritten()).hasSize(2);
            assertThat(new AbsoluteAction().parametersAsWritten()).hasSize(1);
            assertThat(new ToDegreesNative().parametersAsWritten()).hasSize(1);
            assertThat(new ToRadiansNative().parametersAsWritten()).hasSize(1);
        }

        @Test
        @DisplayName("the angle conversions name their argument for what it holds")
        void theAngleConversionsNameTheirArgument() {
            assertThat(theFirstParameterOf(new ToDegreesNative())).isEqualTo("radians");
            assertThat(theFirstParameterOf(new ToRadiansNative())).isEqualTo("degrees");
        }

        @Test
        @DisplayName("a round trip through both conversions comes back where it started")
        void aRoundTripThroughBoth() {
            Value asRadians = answerOf(new ToRadiansNative(), NOTHING,
                    IntegerValue.of(180));
            assertThat(quantityOf(asRadians)).isEqualTo(Math.PI);
            assertThat(quantityOf(answerOf(new ToDegreesNative(), NOTHING, asRadians)))
                    .isEqualTo(180.0);
        }

        private String theFirstParameterOf(DefaultNative function) {
            return function.parametersAsWritten().stream()
                    .map(Parameter::name)
                    .findFirst()
                    .orElseThrow();
        }
    }
}
