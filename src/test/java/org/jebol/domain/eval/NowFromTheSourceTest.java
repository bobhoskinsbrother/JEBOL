package org.jebol.domain.eval;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOW, and the ten questions it answers.
 *
 * <p>Read out of {@code REBNATIVE(now)} in {@code n-io.c}, and checked against
 * the R3 binary. Nothing here asserts on a clock reading, because the clock
 * moves; what is asserted is the shape and the relations between the answers,
 * which hold whenever the call is made.
 *
 * <p>Three of these are not guessable. The seconds are whole unless /PRECISE is
 * asked for, so a caller timing something and not saying so measures nothing.
 * Two questions at once are refused rather than combined -- {@code now/year
 * /month} is an error, not a pair -- and /PRECISE is the one refinement that
 * does not count against that limit. And Monday is day one.
 *
 * <p>Specified in {@code spec/natives.allium} under "NOW, and the ten questions
 * it answers".
 */
class NowFromTheSourceTest {

    /**
     * An interpreter that may read the clock.
     *
     * <p>NOW is a host service, so an interpreter granted nothing answers
     * no-service and never reaches the part being tested. That refusal has its
     * own test elsewhere; here the clock is granted so the ten refinements are
     * what is being measured.
     */
    private static String answerTo(String source) {
        Interpreter interpreter =
                Interpreter.withBounds(Bounds.standard().granting(HostService.CLOCK));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("what each refinement answers")
    class TheAnswers {

        @Test
        @DisplayName("NOW is a date with a time and a zone")
        void nowIsADateAndTime() {
            assertThat(answerTo("date? now")).isEqualTo(TRUE);
            assertThat(answerTo("d: now time? d/time")).isEqualTo(TRUE);
            assertThat(answerTo("d: now time? d/zone")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/DATE is a day, with no time and no zone")
        void dateIsADayAlone() {
            assertThat(answerTo("date? now/date")).isEqualTo(TRUE);
            assertThat(answerTo("d: now/date none? d/time")).isEqualTo(TRUE);
            assertThat(answerTo("d: now/date none? d/zone")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/TIME and /ZONE are times")
        void timeAndZoneAreTimes() {
            assertThat(answerTo("time? now/time")).isEqualTo(TRUE);
            assertThat(answerTo("time? now/zone")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/YEAR, /MONTH and /DAY are integers, in range")
        void thePartsAreIntegers() {
            assertThat(answerTo("integer? now/year")).isEqualTo(TRUE);
            assertThat(answerTo("all [now/month >= 1 now/month <= 12]")).isEqualTo(TRUE);
            assertThat(answerTo("all [now/day >= 1 now/day <= 31]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/WEEKDAY counts from Monday, and /YEARDAY from the first of January")
        void weekdayAndYearday() {
            assertThat(answerTo("all [now/weekday >= 1 now/weekday <= 7]"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("all [now/yearday >= 1 now/yearday <= 366]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and each part agrees with the date it came from")
        void thePartsAgreeWithTheDate() {
            assertThat(answerTo("d: now/date d/year = now/year")).isEqualTo(TRUE);
            assertThat(answerTo("d: now/date d/month = now/month")).isEqualTo(TRUE);
            assertThat(answerTo("d: now/date d/day = now/day")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("/PRECISE, and what it costs not to ask")
    class Precision {

        @Test
        @DisplayName("without it the seconds are whole")
        void withoutItTheSecondsAreWhole() {
            assertThat(answerTo("t: now/time t = to time! to integer! t"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("d: now t: d/time t = to time! to integer! t"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and with it the fraction survives")
        void withItTheFractionSurvives() {
            assertThat(answerTo(
                    "a: now/precise/time b: now/precise/time "
                    + "or~ a <> to time! to integer! a b <> to time! to integer! b"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("it is a date, like the plain call")
        void itIsStillADate() {
            assertThat(answerTo("date? now/precise")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("/UTC")
    class Utc {

        @Test
        @DisplayName("answers the same instant with no offset")
        void theSameInstantAtZoneZero() {
            assertThat(answerTo("d: now/utc d/zone = 0:00")).isEqualTo(TRUE);
            assertThat(answerTo("date? now/utc")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the offset it drops is the one the date reports")
        void theOffsetItDropsIsTheOneReported() {
            assertThat(answerTo("""
                    d: 1-Jan-2000/10:00+2:00
                    reduce [d/hour d/utc/hour to integer! d/zone/hour]"""))
                    .isEqualTo("[10 8 2]");
        }

        @Test
        @DisplayName("even where dropping it carries the clock back over midnight")
        void theOffsetCarriesOverMidnight() {
            assertThat(answerTo("""
                    d: 1-Jan-2000/1:00+2:00
                    d/utc""")).isEqualTo("31-Dec-1999/23:00");
        }
    }

    @Nested
    @DisplayName("one question at a time")
    class OneAtATime {

        @Test
        @DisplayName("two parts at once are refused rather than combined")
        void twoPartsAreRefused() {
            assertThat(errorIdFrom("now/year/month")).isEqualTo("bad-refines");
            assertThat(errorIdFrom("now/date/time")).isEqualTo("bad-refines");
            assertThat(errorIdFrom("now/utc/zone")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("but /PRECISE does not count, because it is not a part")
        void preciseDoesNotCount() {
            assertThat(answerTo("integer? now/precise/year")).isEqualTo(TRUE);
            assertThat(answerTo("time? now/precise/time")).isEqualTo(TRUE);
            assertThat(answerTo("date? now/utc/precise")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and three at once are refused even with /PRECISE among them")
        void threeAreRefusedAnyway() {
            assertThat(errorIdFrom("now/precise/year/month")).isEqualTo("bad-refines");
        }
    }
}
