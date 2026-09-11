package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a duration may be combined with, and what comes back when it is.
 *
 * <p>{@code REBTYPE(Time)} in {@code t-time.c} is a table of six datatypes and
 * which operations each one takes. It is narrower than it looks: a time and a
 * money multiply and divide and will not add, a time and a percentage only
 * multiply, and a time and a tuple do nothing at all. Everything outside the
 * table is {@code Trap_Math_Args}, which names the operation and the datatype.
 *
 * <p>JEBOL widened the other side to a number and did the sum, so a duration
 * added to an amount of money answered a duration and a duration divided by a
 * colour answered a colour.
 *
 * <p>Every expectation here was read off a real 3.22.5 first.
 */
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

        /**
         * Dividing one duration by another asks how many times it goes in,
         * which is a plain number. The C sets the datatype explicitly rather
         * than letting it fall through: {@code VAL_SET(DS_RETURN,
         * REB_DECIMAL)}.
         */
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

        /**
         * The same sums the other way round are narrower, because the C
         * dispatches on the left and the number handlers agree on less than
         * the time one does.
         */
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

    /**
     * "Support for actions like A_ADD does not make sense, so only MULTIPLY is
     * supported", says the C beside the branch. Half of ten hours is five
     * hours; ten hours plus fifty per cent of nothing in particular is not a
     * question with an answer.
     */
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

    /**
     * An hourly rate, which is what the pair is for. The duration counts as
     * hours -- {@code secs * NANO / 3600.0} -- so an hour and a half at five
     * pounds an hour is seven pounds fifty, and a hundred pounds over four
     * hours is twenty-five an hour.
     */
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

    /**
     * A duration is a sixty-four bit count of nanoseconds and the C adds two of
     * them as integers. Going through a double loses the low digits of any
     * duration past about a hundred days, because a double holds fifteen or so
     * significant figures where a duration that long needs nineteen.
     */
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

        /**
         * Refused rather than brought down to the longest duration there is.
         * Clamping answered the biggest time for every number above it, so a
         * calculation wrong by a factor of a thousand came back looking like
         * an answer -- and a negative one came back as {@code --2562047:-47:-16},
         * which is not a time at all.
         */
        @Test
        @DisplayName("and a count too big to hold is refused")
        void aCountTooBigToHoldIsRefused() {
            assertThat(errorFrom("to time! 9223372036.5")).isEqualTo("out-of-range");
            assertThat(errorFrom("to time! -9223372036.5")).isEqualTo("out-of-range");
            assertThat(errorFrom("to time! 1e30")).isEqualTo("out-of-range");
            assertThat(answerTo("to time! 9223372036")).isEqualTo("2562047:47:16");
        }

        /**
         * Arithmetic has its own limit, and it is the tighter of the two:
         * whole hours rather than whole seconds. So a duration can be made
         * that arithmetic will not then add to.
         */
        @Test
        @DisplayName("and a sum that would overflow says so rather than settling")
        void aSumThatWouldOverflowSaysSo() {
            assertThat(errorFrom("0:0:0.1 + to-time 9223372036"))
                    .isEqualTo("type-limit");
        }
    }
}
