package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The four shapes of a time literal, from {@code Scan_Time} in
 * {@code rebol3-source/src/core/t-time.c}, which lists them in a comment:
 *
 * <pre>
 * //    HH:MM       as part1:part2
 * //    HH:MM:SS    as part1:part2:part3
 * //    HH:MM:SS.DD as part1:part2:part3.part4
 * //    MM:SS.DD    as part1:part2.part4
 * </pre>
 *
 * <p><b>The last shape changes what the first two numbers mean.</b> {@code 12:34} is
 * twelve hours and thirty-four minutes; {@code 12:34.5} is twelve <em>minutes</em>
 * and 34.5 seconds. A fraction on the second component is the whole of the
 * difference, and the code says so:
 * {@code if (part3 >= 0 || part4 < 0) ... HOUR_TIME(part1) + MIN_TIME(part2)} against
 * {@code else ... MIN_TIME(part1) + SEC_TIME(part2)}.
 *
 * <p>JEBOL had no MM:SS shape, so a two-part time with a fraction was not a time at
 * all -- it fell through every pattern and became a <em>word</em>, silently. Which is
 * how it went unnoticed: nothing failed, a word simply appeared where a time was
 * meant, and `mezz-debug.reb` line 114 is the one place in Rebol's own library that
 * writes one: {@code round/to time 0:0.001}.
 */
class TimeLiteralFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("two parts are hours and minutes")
    class HoursAndMinutes {

        @Test
        @DisplayName("without a fraction")
        void theOrdinaryForm() {
            assertThat(answerTo("""
                    12:34 = 12:34:00""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    time? load {12:34}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a single digit in either place still reads")
        void singleDigits() {
            assertThat(answerTo("""
                    1:2 = 1:02:00""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    0:0 = 0:00:00""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("but two parts with a fraction are minutes and seconds")
    class MinutesAndSeconds {

        @Test
        @DisplayName("so the first number moves along one place")
        void theFractionChangesTheMeaning() {
            // The pair worth reading together: the same two numbers, and a fraction
            // is all that stands between twelve hours and twelve minutes.
            assertThat(answerTo("""
                    12:34.5 = 0:12:34.5""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    12:34 = 12:34:00""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the smallest one Rebol's own library writes")
        void theOneInTheLibrary() {
            // `mezz-debug.reb` line 114: `return form round/to time 0:0.001`. The
            // only place in the borrowed library that uses the shape, and the reason
            // the gap was found at all -- refusing digit-leading words made this
            // file stop loading.
            assertThat(answerTo("""
                    0:0.001 = 0:00:00.001""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    time? load {0:0.001}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a negative one keeps its sign")
        void aNegativeOne() {
            assertThat(answerTo("""
                    -0:0.001 = negate 0:00:00.001""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    -12:34.5 = negate 0:12:34.5""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a fraction of zero does not count, so it is hours again")
        void aZeroFraction() {
            // `Grab_Int_Scale` is followed by `if (part4 == 0) part4 = -1;`, and the
            // mode test reads `part4 < 0`. So a zero fraction is no fraction, and
            // `12:34.0` is twelve hours -- which is not what the spelling suggests.
            assertThat(answerTo("""
                    12:34.0 = 12:34:00""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    12:34.0 = 12:34""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and a sign, but only one")
    class TheSign {

        @Test
        @DisplayName("one sign is part of the time")
        void oneSign() {
            assertThat(answerTo("""
                    -1:23 = negate 1:23""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    +1:23 = 1:23""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and two is a malformed time, which the C calls a hole")
        void twoSigns() {
            // `if (*cp == '-' || *cp == '+') return 0; // small hole: --1:23` -- the
            // second sign is tested after the first has been taken, and the comment
            // names the spelling somebody filed. Rebol's own test asserts it.
            //
            // A malformed *time* rather than the word it looks like, because a sign
            // and a colon make the token a time before the amount is read.
            assertThat(answerTo("""
                    error? try [load {--1:23}]""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    e: try [load {--1:23}] e/id""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and any pairing of the two signs, not just two minuses")
        void everyPairing() {
            assertThat(answerTo("""
                    error? try [load {++1:23}]""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    error? try [load {-+1:23}]""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    error? try [load {+-1:23}]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a double sign without a time is still an ordinary word")
        void whatStaysAWord() {
            // `--` is a word the library binds to a stepper, so the refusal has to
            // need the colon. Without one there is no time to be malformed.
            assertThat(answerTo("""
                    (load {--}) = to word! "--\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {--a}) = to word! "--a\"""")).isEqualTo(TRUE);
            // And a trailing colon is a set-word, not a time: the colon has to have
            // something after it.
            assertThat(answerTo("""
                    set-word? load {--a:}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and three parts are always hours, minutes and seconds")
    class ThreeParts {

        @Test
        @DisplayName("with or without a fraction on the seconds")
        void theThreePartForms() {
            // `part3 >= 0` alone picks HH:MM mode, so a third component settles it
            // and the fraction has no say.
            assertThat(answerTo("""
                    time? load {12:34:56}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    12:34:56.789 = 12:34:56.789""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    0:0:0.001 = 0:00:00.001""")).isEqualTo(TRUE);
        }
    }
}
