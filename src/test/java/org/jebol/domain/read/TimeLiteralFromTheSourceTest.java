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
            assertThat(answerTo("""
                    12:34.5 = 0:12:34.5""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    12:34 = 12:34:00""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the smallest one Rebol's own library writes")
        void theOneInTheLibrary() {
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
            assertThat(answerTo("""
                    (load {--}) = to word! "--\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {--a}) = to word! "--a\"""")).isEqualTo(TRUE);
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
            assertThat(answerTo("""
                    time? load {12:34:56}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    12:34:56.789 = 12:34:56.789""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    0:0:0.001 = 0:00:00.001""")).isEqualTo(TRUE);
        }
    }
}
