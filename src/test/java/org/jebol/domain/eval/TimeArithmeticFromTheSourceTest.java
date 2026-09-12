package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeArithmeticFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorFrom(String source) {
        return answerTo("""
                e: try [%s] either error? e [e/id] ['no-error]""".formatted(source));
    }

    @Nested
    @DisplayName("a duration against another duration")
    class AgainstAnotherDuration {

        @Test
        @DisplayName("adding and subtracting give a duration")
        void addingAndSubtractingGiveADuration() {
            assertThat(answerTo("1:0 + 0:30")).isEqualTo("1:30");
            assertThat(answerTo("1:0 - 0:30")).isEqualTo("0:30");
        }

        @Test
        @DisplayName("but dividing gives a plain number, not a duration")
        void dividingGivesAPlainNumber() {
            assertThat(answerTo("1:0 / 0:30")).isEqualTo("2.0");
            assertThat(answerTo("type? 1:0 / 0:30")).isEqualTo("#(decimal!)");
        }

        @Test
        @DisplayName("and multiplying two durations means nothing")
        void multiplyingTwoDurationsMeansNothing() {
            assertThat(errorFrom("1:0 * 0:30")).isEqualTo("not-related");
        }
    }

    @Nested
    @DisplayName("a duration against a number, which counts as seconds")
    class AgainstANumber {

        @Test
        @DisplayName("a bare number added is seconds, and multiplying scales")
        void aBareNumberIsSeconds() {
            assertThat(answerTo("0:0 + 60")).isEqualTo("0:01");
            assertThat(answerTo("1:0:0 * 2")).isEqualTo("2:00");
            assertThat(answerTo("1:0:0 / 2")).isEqualTo("0:30");
        }

        @Test
        @DisplayName("and with the number on the left, fewer of them work")
        void withTheNumberOnTheLeft() {
            assertThat(answerTo("2 * 1:0:0")).isEqualTo("2:00");
            assertThat(answerTo("2 + 10:0")).isEqualTo("10:00:02");
            assertThat(answerTo("2 - 10:0")).isEqualTo("-9:59:58");
            assertThat(errorFrom("2 / 10:0")).isEqualTo("not-related");
            assertThat(errorFrom("0.5 - 10:0")).isEqualTo("not-related");
            assertThat(errorFrom("0.5 / 10:0")).isEqualTo("not-related");
        }
    }

    @Nested
    @DisplayName("a duration against a percentage, which only scales it")
    class AgainstAPercentage {

        @Test
        @DisplayName("multiplying takes a proportion of it, either way round")
        void multiplyingTakesAProportion() {
            assertThat(answerTo("10:0:0 * 50%")).isEqualTo("5:00");
            assertThat(answerTo("50% * 10:0:0")).isEqualTo("5:00");
        }

        @Test
        @DisplayName("and nothing else is allowed at all")
        void nothingElseIsAllowed() {
            assertThat(errorFrom("50% + 10:0")).isEqualTo("not-related");
            assertThat(errorFrom("50% - 10:0")).isEqualTo("not-related");
            assertThat(errorFrom("50% / 10:0")).isEqualTo("not-related");
        }
    }

    @Nested
    @DisplayName("a duration against an amount of money, which is a rate")
    class AgainstMoney {

        @Test
        @DisplayName("multiplying charges the rate, and answers money")
        void multiplyingChargesTheRate() {
            assertThat(answerTo("1:30:0 * $5")).isEqualTo("$7.5");
            assertThat(answerTo("$5 * 1:30:0")).isEqualTo("$7.5");
        }

        @Test
        @DisplayName("and dividing works the rate out")
        void dividingWorksTheRateOut() {
            assertThat(answerTo("4:0:0 / $100")).isEqualTo("$25");
        }

        @Test
        @DisplayName("but adding a duration to money means nothing")
        void addingMoneyMeansNothing() {
            assertThat(errorFrom("1:30:0 + $5")).isEqualTo("not-related");
            assertThat(errorFrom("1:30:0 - $5")).isEqualTo("not-related");
        }
    }

    @Test
    @DisplayName("a duration and a tuple do not go together at all")
    void aDurationAndATupleDoNotGoTogether() {
        assertThat(errorFrom("0:0:01 / 1.1.1")).isEqualTo("not-related");
        assertThat(errorFrom("1.1.1 / 0:0:01")).isEqualTo("not-related");
        assertThat(errorFrom("1.1.1 + 0:0:01")).isEqualTo("not-related");
    }

    @Nested
    @DisplayName("and the counting is in whole nanoseconds throughout")
    class InWholeNanoseconds {

        @Test
        @DisplayName("a long duration keeps every digit through a sum")
        void aLongDurationKeepsEveryDigit() {
            assertThat(answerTo("-1.0 + -596523:14:07.999999999"))
                    .isEqualTo("-596523:14:08.999999999");
        }

        @Test
        @DisplayName("and a count of seconds converts exactly")
        void aCountOfSecondsConvertsExactly() {
            assertThat(answerTo("to time! 9223372034")).isEqualTo("2562047:47:14");
            assertThat(answerTo("to time! 9223372035")).isEqualTo("2562047:47:15");
            assertThat(answerTo("to integer! to time! 9223372036"))
                    .isEqualTo("9223372036");
        }

        @Test
        @DisplayName("a fraction of a second is rounded, not cut short")
        void aFractionOfASecondIsRounded() {
            assertThat(answerTo("mold make time! 0.1234567896"))
                    .isEqualTo("\"0:00:00.12345679\"");
        }

        @Test
        @DisplayName("and a count too big to hold is refused")
        void aCountTooBigToHoldIsRefused() {
            assertThat(errorFrom("to time! 9223372036.5")).isEqualTo("out-of-range");
            assertThat(errorFrom("to time! -9223372036.5")).isEqualTo("out-of-range");
            assertThat(errorFrom("to time! 1e30")).isEqualTo("out-of-range");
            assertThat(answerTo("to time! 9223372036")).isEqualTo("2562047:47:16");
        }

        @Test
        @DisplayName("and a sum that would overflow says so rather than settling")
        void aSumThatWouldOverflowSaysSo() {
            assertThat(errorFrom("0:0:0.1 + to-time 9223372036"))
                    .isEqualTo("type-limit");
        }
    }
}
