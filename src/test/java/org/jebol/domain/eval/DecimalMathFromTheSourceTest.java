package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The decimal datatype and the maths natives over it, read out of
 * {@code src/core/t-decimal.c} and {@code src/core/n-math.c}.
 *
 * <p>Written from the C and not from the Java beside it. Each group names the
 * function it was taken from, so a disagreement can be settled by reading that
 * function rather than by arguing about what the arithmetic ought to answer.
 *
 * <p>Two ideas run through most of it, and both are about a number that is
 * almost but not quite what it should be. Rebol does not leave those alone: it
 * snaps them. A remainder too small to matter at the scale of its own operands
 * becomes exactly zero; a sine or cosine within one step of the representation
 * becomes exactly zero; a tangent close enough to a right angle becomes an
 * infinity. Each snap is a named place in the C rather than a general policy,
 * and each has an assertion in Rebol's own suite that no amount of correct
 * floating point arithmetic will pass.
 */
class DecimalMathFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    @Nested
    @DisplayName("modulus: three definitions of division under four names")
    class Remainders {

        @Test
        @DisplayName("REMAINDER and MOD are the same answer, with the dividend's sign")
        void truncatedDivisionFollowsTheDividend() {
            assertThat(answerTo("b: copy [] for i -7 7 1 [append b i % 3] "
                    + "b = [-1 0 -2 -1 0 -2 -1 0 1 2 0 1 2 0 1]")).isEqualTo(TRUE);
            assertThat(answerTo("b: copy [] for i -7 7 1 [append b mod i 3] "
                    + "b = [-1 0 -2 -1 0 -2 -1 0 1 2 0 1 2 0 1]")).isEqualTo(TRUE);
            assertThat(answerTo("b: copy [] for i -7 7 1 [append b i % -3] "
                    + "b = [-1 0 -2 -1 0 -2 -1 0 1 2 0 1 2 0 1]")).isEqualTo(TRUE);
            assertThat(answerTo("b: copy [] for i -7 7 1 [append b mod i -3] "
                    + "b = [-1 0 -2 -1 0 -2 -1 0 1 2 0 1 2 0 1]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("MODULO is Euclidean, so it is never negative whatever the signs")
        void euclideanModuloIsNeverNegative() {
            assertThat(answerTo("b: copy [] for i -7 7 1 [append b i %% 3] "
                    + "b = [2 0 1 2 0 1 2 0 1 2 0 1 2 0 1]")).isEqualTo(TRUE);
            assertThat(answerTo("b: copy [] for i -7 7 1 [append b i %% -3] "
                    + "b = [2 0 1 2 0 1 2 0 1 2 0 1 2 0 1]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("MODULO/FLOOR takes the divisor's sign, which is the third answer")
        void flooredModuloFollowsTheDivisor() {
            assertThat(answerTo("b: copy [] for i -7 7 1 [append b modulo/floor i 3] "
                    + "b = [2 0 1 2 0 1 2 0 1 2 0 1 2 0 1]")).isEqualTo(TRUE);
            assertThat(answerTo("b: copy [] for i -7 7 1 [append b modulo/floor i -3] "
                    + "b = [-1 0 -2 -1 0 -2 -1 0 -2 -1 0 -2 -1 0 -2]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("all four agree on two positive numbers, which is why a wrong pairing is quiet")
        void theyAgreeOnPositives() {
            assertThat(answerTo("(7 % 3) = 1")).isEqualTo(TRUE);
            assertThat(answerTo("(mod 7 3) = 1")).isEqualTo(TRUE);
            assertThat(answerTo("(7 %% 3) = 1")).isEqualTo(TRUE);
            assertThat(answerTo("(modulo/floor 7 3) = 1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("MODULO rounds a negligible answer to zero and MOD does not")
        void onlyTheNonTruncatedOnesSnap() {
            assertThat(answerTo("0.0 = modulo 562949953421311.25 1")).isEqualTo(TRUE);
            assertThat(answerTo("0.25 = mod 562949953421311.25 1")).isEqualTo(TRUE);
            assertThat(answerTo("0.0 = modulo 0.1 + 0.1 + 0.1 0.3")).isEqualTo(TRUE);
            assertThat(answerTo("5.55111512312578e-17 = mod 0.1 + 0.1 + 0.1 0.3"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the answer keeps the dividend's datatype")
        void theDividendDecidesTheDatatype() {
            assertThat(answerTo("integer? 7 % 3")).isEqualTo(TRUE);
            assertThat(answerTo("decimal? 7.0 % 3")).isEqualTo(TRUE);
            assertThat(answerTo("integer? 7 %% 3")).isEqualTo(TRUE);
            assertThat(answerTo("decimal? 7.5 %% 3")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a zero divisor raises zero-divide whichever definition asked")
        void aZeroDivisorRaises() {
            assertThat(errorIdOf("7 % 0")).isEqualTo("zero-divide");
            assertThat(errorIdOf("mod 7 0")).isEqualTo("zero-divide");
            assertThat(errorIdOf("7 %% 0")).isEqualTo("zero-divide");
            assertThat(errorIdOf("modulo/floor 7 0")).isEqualTo("zero-divide");
        }

        @Test
        @DisplayName("the negative cases Rebol's suite names for MOD")
        void theNamedNegativeCases() {
            assertThat(answerTo("-3 == mod -8 -5")).isEqualTo(TRUE);
            assertThat(answerTo("-3.0 == mod -8.0 -5")).isEqualTo(TRUE);
            assertThat(answerTo("1.1 = mod 3.3 1.1")).isEqualTo(TRUE);
            assertThat(answerTo("0.0999999999999996 = mod 3.4 1.1")).isEqualTo(TRUE);
            assertThat(answerTo("1.1 = remainder 3.3 1.1")).isEqualTo(TRUE);
            assertThat(answerTo("1.222090944E+33 % -2147483648.0 = 0.0")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("Trig_Value, cosine, sine and tangent in n-math.c")
    class Trigonometry {

        @Test
        @DisplayName("a sine or cosine within one step of the representation is exactly zero")
        void aNegligibleAnswerSnapsToZero() {
            assertThat(answerTo("0.0 = cosine 90")).isEqualTo(TRUE);
            assertThat(answerTo("0.0 = cosine/radians pi / 2")).isEqualTo(TRUE);
            assertThat(answerTo("0.0 = sine/radians pi")).isEqualTo(TRUE);
            assertThat(answerTo("mold cosine 90")).isEqualTo("\"0.0\"");
        }

        @Test
        @DisplayName("the snap is only near zero, so an ordinary answer is untouched")
        void anOrdinaryAnswerIsUntouched() {
            assertThat(answerTo("1.0 = cosine 0")).isEqualTo(TRUE);
            assertThat(answerTo("1.0 = sine 90")).isEqualTo(TRUE);
            assertThat(answerTo("0.5 = cosine 60")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a tangent close enough to a right angle is an infinity")
        void tangentAtARightAngleIsInfinite() {
            assertThat(answerTo("1.#INF = tangent 89.99999999999987")).isEqualTo(TRUE);
            assertThat(answerTo("1.#INF = tangent 90")).isEqualTo(TRUE);
            assertThat(answerTo("-1.#INF = tangent -90")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a tangent away from a right angle is an ordinary number")
        void tangentElsewhereIsFinite() {
            assertThat(answerTo("0.0 = tangent 0")).isEqualTo(TRUE);
            assertThat(answerTo("1.0 = tangent 45")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("sqrt and square-root: two functions for one curve")
    class SquareRoots {

        @Test
        @DisplayName("SQRT takes a decimal and refuses an integer")
        void sqrtTakesADecimalOnly() {
            assertThat(answerTo("2.0 = sqrt 4.0")).isEqualTo(TRUE);
            assertThat(answerTo("all [error? e: try [sqrt 4] e/id = 'expect-arg "
                    + "e/arg3 = integer!]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("SQUARE-ROOT takes any number, so it works where SQRT does not")
        void squareRootTakesAnyNumber() {
            assertThat(answerTo("2.0 = square-root 4")).isEqualTo(TRUE);
            assertThat(answerTo("2.0 = square-root 4.0")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("REBNATIVE(numberq): the one predicate that is not about the datatype")
    class TheNumberPredicate {

        @Test
        @DisplayName("a NaN is a decimal and is not a number")
        void aNotANumberIsNotANumber() {
            assertThat(answerTo("not number? to decimal! #{7FFFFFFFFFFFFFFF}"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("decimal? to decimal! #{7FFFFFFFFFFFFFFF}")).isEqualTo(TRUE);
            assertThat(answerTo("not number? 1.#NaN")).isEqualTo(TRUE);
            assertThat(answerTo("number? 1.#INF")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a money counts as a number here, where the typeset says otherwise")
        void aMoneyIsANumber() {
            assertThat(answerTo("number? $1")).isEqualTo(TRUE);
            assertThat(answerTo("number? 1")).isEqualTo(TRUE);
            assertThat(answerTo("number? 100%")).isEqualTo(TRUE);
            assertThat(answerTo("number? 1.0")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("nothing else is a number, and asking never raises")
        void nothingElseIsANumber() {
            assertThat(answerTo("number? \"1\"")).isEqualTo(FALSE);
            assertThat(answerTo("number? 1x1")).isEqualTo(FALSE);
            assertThat(answerTo("number? 0:0:1")).isEqualTo(FALSE);
            assertThat(answerTo("number? none")).isEqualTo(FALSE);
            assertThat(errorIdOf("number? none")).isEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("A_ROUND in t-decimal.c")
    class Rounding {

        @Test
        @DisplayName("a zero scale rounds nothing, rather than rounding to a whole number")
        void aZeroScaleRoundsNothing() {
            assertThat(answerTo("x: 11.6543212345679 11.6543212345679 == round/to x 0.0"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("x: 11.6543212345679 11.6543212345679 == round/to x 1e-400"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("mold 1e-400")).isEqualTo("\"0.0\"");
        }

        @Test
        @DisplayName("the scale decides the datatype of the answer")
        void theScaleDecidesTheDatatype() {
            assertThat(answerTo("integer? round/to 0.5 1")).isEqualTo(TRUE);
            assertThat(answerTo("money? round/to 0.5 $1")).isEqualTo(TRUE);
            assertThat(answerTo("150% = to percent! round/to 1.45677 to decimal! 10%"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("12 == round/to 11.6543212345679 1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("plain ROUND keeps the subject's datatype")
        void plainRoundKeepsTheSubject() {
            assertThat(answerTo("decimal? round 1.5")).isEqualTo(TRUE);
            assertThat(answerTo("integer? round 2")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a character in arithmetic")
    class Characters {

        @Test
        @DisplayName("a decimal times a character is the code point, as a decimal")
        void aCharacterIsItsCodePoint() {
            assertThat(answerTo("97.0 = try [1.0 * #\"a\"]")).isEqualTo(TRUE);
            assertThat(answerTo("decimal? 1.0 * #\"a\"")).isEqualTo(TRUE);
            assertThat(answerTo("98.0 = (1.0 + #\"a\")")).isEqualTo(TRUE);
        }
    }
}
