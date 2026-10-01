package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class Atan2FromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("refused: try [" + source + "] refused/id");
    }

    @Nested
    @DisplayName("the angle it answers is in radians and spans the whole circle")
    class TheAngle {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "'atan2 1.0 1.0',    0.785398163397448",
                "'atan2 -1.0 1.0',   -0.785398163397448",
                "'atan2 1.0 -1.0',   2.35619449019234",
                "'atan2 -1.0 -1.0',  -2.35619449019234",
        })
        @DisplayName("one answer per quadrant, and only the third and fourth pass pi over two")
        void eachQuadrant(String written, String expected) {
            assertThat(answerTo(written)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "'atan2 0.0 1.0',    0.0",
                "'atan2 1.0 0.0',    1.5707963267949",
                "'atan2 0.0 -1.0',   3.14159265358979",
                "'atan2 -1.0 0.0',   -1.5707963267949",
        })
        @DisplayName("and one on each axis, which is where a plain arctangent would divide by zero")
        void eachAxis(String written, String expected) {
            assertThat(answerTo(written)).isEqualTo(expected);
        }

        @Test
        @DisplayName("the answer is always a decimal, never a percent or an integer")
        void alwaysADecimal() {
            assertThat(answerTo("type? atan2 1.0 1.0")).isEqualTo("#(decimal!)");
            assertThat(answerTo("type? atan2 0.0 1.0")).isEqualTo("#(decimal!)");
        }

        @Test
        @DisplayName("it takes no refinement, so there is no asking it for degrees")
        void noRefinementAtAll() {
            assertThat(answerTo("words-of :atan2")).isEqualTo("[y x]");
        }

        @Test
        @DisplayName("a proportion gives the same angle as the whole numbers it stands for")
        void aProportionIsTheSameAngle() {
            assertThat(answerTo("atan2 -1.5 1.5")).isEqualTo(answerTo("atan2 -1.0 1.0"));
        }
    }

    @Nested
    @DisplayName("at the origin and either side of a signed zero")
    class TheDegenerateCases {

        @Test
        @DisplayName("both halves zero is no angle rather than an error")
        void bothZero() {
            assertThat(answerTo("atan2 0.0 0.0")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("a negative zero y carries its sign into the answer")
        void anegativeZeroDown() {
            assertThat(answerTo("atan2 -0.0 1.0")).isEqualTo("-0.0");
            assertThat(answerTo("atan2 -0.0 -1.0")).isEqualTo("-3.14159265358979");
        }

        @Test
        @DisplayName("a negative zero x puts the angle on the far side rather than at nothing")
        void anegativeZeroAcross() {
            assertThat(answerTo("atan2 0.0 -0.0")).isEqualTo("3.14159265358979");
            assertThat(answerTo("atan2 0.0 0.0")).isEqualTo("0.0");
        }
    }

    @Nested
    @DisplayName("an infinity and a not-a-number pass through rather than failing")
    class TheValuesOutsideTheRange {

        @Test
        @DisplayName("an infinite y stands straight up, an infinite x straight along")
        void aninfinity() {
            assertThat(answerTo("atan2 1.#inf 1.0")).isEqualTo("1.5707963267949");
            assertThat(answerTo("atan2 1.0 1.#inf")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("a not-a-number answers one back")
        void anotANumber() {
            assertThat(answerTo("atan2 1.#nan 1.0")).isEqualTo("1.#NaN");
        }
    }

    @Nested
    @DisplayName("the C declares two decimals and widens neither")
    class WhatItRefuses {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "atan2 1 1",
                "atan2 1.0 1",
                "atan2 1 1.0",
                "atan2 100% 100%",
                "atan2 $1 $1",
                "atan2 1.0 none",
                "atan2 1.0 true",
                "atan2 1.0 1x1",
        })
        @DisplayName("anything that is not a decimal on either side is refused, not converted")
        void onlyADecimal(String written) {
            assertThat(errorIdFrom(written)).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("a string is refused on either side the same way")
        void astring() {
            assertThat(errorIdFrom("atan2 {a} 1.0")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("atan2 1.0 {a}")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("and both arguments are required, so one on its own has no second")
        void bothAreRequired() {
            assertThat(errorIdFrom("atan2 1.0")).isEqualTo("no-arg");
        }
    }
}
