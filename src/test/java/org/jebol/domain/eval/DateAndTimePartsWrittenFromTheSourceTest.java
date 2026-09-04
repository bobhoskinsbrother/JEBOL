package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writing a part of a date, and reading a part of a time by number.
 *
 * <p>Only the two offset fields could be written; the other twelve raised
 * bad-field-set. What that hid is the rule that makes the rest work: a date
 * carrying no time gets one, starting at midnight, the moment a clock part is
 * written to it -- {@code if (secs == NO_TIME && ...) { time.h = 0; ... }} in
 * {@code PD_Date}. So {@code d/hour: 2} on a bare date is two in the morning
 * rather than an error.
 *
 * <p>Reading a part of a time has two selectors that behave differently on
 * purpose. A word that is not one of the three parts is a mistake and reads as
 * invalid-path; a number outside the three is simply nothing and reads as
 * none. And the seconds are a whole number only while they are whole, turning
 * decimal the moment there is a fraction -- which PICK got wrong where the
 * path got it right, the two having been written twice.
 */
class DateAndTimePartsWrittenFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("failure: try [" + source + "] failure/id");
    }

    @Nested
    @DisplayName("a date that had no clock is given one at midnight")
    class TheClockStarts {

        @Test
        @DisplayName("writing the hour makes it that hour of the morning")
        void writingTheHour() {
            assertThat(answerTo("""
                    d: 1-1-2000
                    d/hour: 2
                    reduce [d d/time]""")).isEqualTo("[1-Jan-2000/2:00 2:00]");
        }

        @Test
        @DisplayName("and writing the minute leaves the hour at nothing")
        void writingTheMinute() {
            assertThat(answerTo("""
                    d: 1-1-2000
                    d/minute: 10
                    reduce [d d/time d/date]"""))
                    .isEqualTo("[1-Jan-2000/0:10 0:10 1-Jan-2000]");
        }

        @Test
        @DisplayName("the clock parts read as none until one of them is written")
        void theyReadAsNoneUntilWritten() {
            assertThat(answerTo("""
                    d: 1-1-2000
                    reduce [
                        none? d/zone none? d/time none? d/hour
                        none? d/minute none? d/second integer? d/year
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("the parts of a date, written")
    class TheWrittenParts {

        @Test
        @DisplayName("the three numbers of the day")
        void theThreeNumbers() {
            assertThat(answerTo("""
                    d: 1-Jan-2000
                    d/year: 2020
                    d/month: 3
                    d/day: 14
                    d""")).isEqualTo("14-Mar-2020");
        }

        @Test
        @DisplayName("a month past twelve rolls into the year after")
        void aMonthPastTwelveRolls() {
            assertThat(answerTo("""
                    d: 1-Jan-2000
                    d/month: 13
                    d""")).isEqualTo("1-Jan-2001");
        }

        @Test
        @DisplayName("and a day past the end of the month rolls into the next")
        void aDayPastTheMonthRolls() {
            assertThat(answerTo("""
                    d: 1-Jan-2000
                    d/day: 32
                    d""")).isEqualTo("1-Feb-2000");
        }

        @Test
        @DisplayName("TIME written as none takes the offset away with it")
        void timeWrittenAsNone() {
            assertThat(answerTo("""
                    d: 1-Jan-2000/5:00+2:00
                    d/time: none
                    reduce [d none? d/zone]""")).isEqualTo("[1-Jan-2000 #(true)]");
        }

        @Test
        @DisplayName("DATE written takes the day and leaves the clock")
        void dateWrittenLeavesTheClock() {
            assertThat(answerTo("""
                    d: 1-Jan-2000/5:00
                    d/date: 25-Dec-2020
                    d""")).isEqualTo("25-Dec-2020/5:00");
        }

        @Test
        @DisplayName("YEARDAY counts days into the year the date is already in")
        void yeardayCountsIntoTheYear() {
            assertThat(answerTo("""
                    d: 1-Jan-2000
                    d/yearday: 60
                    d""")).isEqualTo("29-Feb-2000");
        }

        @Test
        @DisplayName("and a name that is no part of a date at all is a bad path")
        void aNameThatIsNoPart() {
            assertThat(errorIdFrom("""
                    d: 1-Jan-2000
                    d/fortnight: 2""")).isEqualTo("invalid-path");
        }
    }

    @Nested
    @DisplayName("the parts of a time, read")
    class TheTimeParts {

        @Test
        @DisplayName("one, two and three are the hour, the minute and the second")
        void oneTwoAndThree() {
            assertThat(answerTo("""
                    t: 5:06:07
                    reduce [t/1 t/2 t/3 t/hour t/minute t/second]"""))
                    .isEqualTo("[5 6 7 5 6 7]");
        }

        @Test
        @DisplayName("the second turns decimal the moment there is a fraction")
        void theSecondTurnsDecimal() {
            assertThat(answerTo("""
                    t: 5:06:07.5
                    reduce [t/3 t/second pick t 3]""")).isEqualTo("[7.5 7.5 7.5]");
        }

        @Test
        @DisplayName("a number outside the three is nothing, not a mistake")
        void aNumberOutsideIsNothing() {
            assertThat(answerTo("""
                    t: 5:06:07
                    reduce [none? t/0 none? t/-1 none? t/100]"""))
                    .isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("where a word outside the three is one")
        void aWordOutsideIsAMistake() {
            assertThat(errorIdFrom("""
                    t: 5:06:07
                    t/hours""")).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("and a number outside a date's parts is nothing too")
        void aDateTakesNumbersTheSameWay() {
            assertThat(answerTo("""
                    d: 1-Jan-2000/5:00
                    reduce [none? d/0 none? d/-1 none? d/100]"""))
                    .isEqualTo("[#(true) #(true) #(true)]");
        }
    }
}
