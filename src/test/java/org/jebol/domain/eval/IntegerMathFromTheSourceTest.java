package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The integer datatype and the bit operations over it, read out of
 * {@code src/core/t-integer.c} and {@code REBNATIVE(shift)} in
 * {@code src/core/n-math.c}.
 *
 * <p>Written from the C and not from the Java beside it. Each group names the
 * function it was taken from, so a disagreement can be settled by reading that
 * function rather than by arguing about what a shift ought to answer.
 *
 * <p>The idea underneath SHIFT: it keeps the sign and refuses to lose a bit off
 * the top. That makes it fussier than any shift in Java or C, and it is why
 * {@code shift 1 63} raises rather than answering the most negative whole
 * number. /LOGICAL drops the fussiness and shifts the bits, so the two
 * refinements of one native disagree on almost every boundary.
 */
class IntegerMathFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    /** The most negative whole number, written the way Rebol's suite writes it. */
    private static final String MOST_NEGATIVE = "m: to-integer #{8000000000000000} ";

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("REBNATIVE(shift): keeps the sign and loses no bits")
    class Shifting {

        @Test
        @DisplayName("an ordinary shift is an ordinary shift")
        void theEasyCases() {
            assertThat(answerTo("0 = shift 0 0")).isEqualTo(TRUE);
            assertThat(answerTo("0 = shift 0 1")).isEqualTo(TRUE);
            assertThat(answerTo("0 = shift 0 63")).isEqualTo(TRUE);
            assertThat(answerTo("0 = shift 0 -1")).isEqualTo(TRUE);
            assertThat(answerTo("1 = shift 1 0")).isEqualTo(TRUE);
            assertThat(answerTo("2 = shift 1 1")).isEqualTo(TRUE);
            assertThat(answerTo("0 = shift 1 -1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("shifting a positive number into the sign bit raises")
        void theOverflowCheckOnALeftShift() {
            assertThat(errorIdOf("shift 1 63")).isEqualTo("overflow");
            assertThat(errorIdOf("shift 2 62")).isEqualTo("overflow");
            assertThat(errorIdOf("shift 4611686018427387904 1")).isEqualTo("overflow");
        }

        @Test
        @DisplayName("the most negative number is reachable, and is the one exception")
        void theMostNegativeNumberIsNotAnOverflow() {
            assertThat(answerTo(MOST_NEGATIVE + "m = shift -1 63")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a count of sixty-four or more raises, unless the value is already zero")
        void aCountPastTheWidthRaises() {
            assertThat(errorIdOf("shift 1 64")).isEqualTo("overflow");
            assertThat(errorIdOf("shift 1 100")).isEqualTo("overflow");
            assertThat(answerTo("0 = shift 0 64")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("shifting right past the width repeats the sign bit")
        void aRightShiftPastTheWidth() {
            assertThat(answerTo(MOST_NEGATIVE + "-1 = shift m -63")).isEqualTo(TRUE);
            assertThat(answerTo(MOST_NEGATIVE + "-1 = shift m -64")).isEqualTo(TRUE);
            assertThat(answerTo("-1 = shift -1 -100")).isEqualTo(TRUE);
            assertThat(answerTo("0 = shift 1 -100")).isEqualTo(TRUE);
            assertThat(answerTo("0 = shift 1000 -64")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/LOGICAL shifts the bits and refuses nothing")
        void theLogicalRefinementDropsTheFussiness() {
            assertThat(answerTo(MOST_NEGATIVE + "m = shift/logical 1 63")).isEqualTo(TRUE);
            assertThat(answerTo("-9223372036854775808 = shift/logical 1 63"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(MOST_NEGATIVE + "1 = shift/logical m -63")).isEqualTo(TRUE);
            assertThat(answerTo(MOST_NEGATIVE + "0 = shift/logical m -64")).isEqualTo(TRUE);
            assertThat(answerTo("0 = shift/logical 1 64")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("REBNATIVE(integer_divide): what // means")
    class IntegerDivision {

        @Test
        @DisplayName("// is INTEGER-DIVIDE and not REMAINDER")
        void theOperatorIsTheQuotient() {
            assertThat(answerTo("2 == (23 // 10)")).isEqualTo(TRUE);
            assertThat(answerTo("3 == (23 % 10)")).isEqualTo(TRUE);
            assertThat(answerTo("2 == integer-divide 23 10")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("both sides truncate to whole numbers, and so does the answer")
        void bothSidesTruncate() {
            assertThat(answerTo("2 == (23.5 // 10)")).isEqualTo(TRUE);
            assertThat(answerTo("2 == (23 // 10.5)")).isEqualTo(TRUE);
            assertThat(answerTo("2 == integer-divide 23.5 10")).isEqualTo(TRUE);
            assertThat(answerTo("2 == integer-divide 23 10.5")).isEqualTo(TRUE);
            assertThat(answerTo("integer? 23.5 // 10")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("dividing by zero raises, and the fraction does not save it")
        void aZeroDivisorRaises() {
            assertThat(errorIdOf("2 // 0")).isEqualTo("zero-divide");
            assertThat(errorIdOf("integer-divide 2 0")).isEqualTo("zero-divide");
            assertThat(errorIdOf("2 // 0.5")).isEqualTo("zero-divide");
        }
    }

    @Nested
    @DisplayName("reading an integer out of text")
    class Conversion {

        @Test
        @DisplayName("a number too large for a machine word raises rather than saturating")
        void anOversizedNumberRaises() {
            assertThat(errorIdOf("to integer! \"11111111111111111111111\""))
                    .isNotEqualTo("no-error");
            assertThat(errorIdOf("to integer! \"9223372036854775808\""))
                    .isNotEqualTo("no-error");
            assertThat(answerTo("9223372036854775807 = to integer! \"9223372036854775807\""))
                    .isEqualTo(TRUE);
            assertThat(answerTo("-9223372036854775808 = to integer! \"-9223372036854775808\""))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("is_prime: trial division to the square root")
    class Primality {

        @Test
        @DisplayName("the small answers")
        void theSmallAnswers() {
            assertThat(answerTo("not prime? 42")).isEqualTo(TRUE);
            assertThat(answerTo("prime? 43")).isEqualTo(TRUE);
            assertThat(answerTo("not prime? 1")).isEqualTo(TRUE);
            assertThat(answerTo("prime? 2")).isEqualTo(TRUE);
            assertThat(answerTo("prime? 3")).isEqualTo(TRUE);
            assertThat(answerTo("not prime? 0")).isEqualTo(TRUE);
            assertThat(answerTo("not prime? -7")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a fourteen digit prime is answered rather than refused")
        void aLargePrimeIsAnswered() {
            assertThat(answerTo("prime? 99'504'028'301'131")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a quote inside a number is a digit separator")
        void quotesSeparateDigits() {
            assertThat(answerTo("1000 = 1'000")).isEqualTo(TRUE);
            assertThat(answerTo("1000000 = 1'000'000")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("SET refuses a name-shaped argument at the door")
    class Setting {

        @Test
        @DisplayName("a number is expect-arg, from the argument check")
        void aNumberIsRefusedAtTheDoor() {
            assertThat(errorIdOf("set 1 1")).isEqualTo("expect-arg");
            assertThat(errorIdOf("set \"a\" 1")).isEqualTo("expect-arg");
            assertThat(errorIdOf("set 1x1 1")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("the shapes it does take still work")
        void theAcceptedShapes() {
            assertThat(answerTo("set 'a 1 a")).isEqualTo("1");
            assertThat(answerTo("set [a b] [1 2] a")).isEqualTo("1");
        }
    }
}
