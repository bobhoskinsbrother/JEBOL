package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            // `VAL_TIME(ret) = NO_TIME; VAL_ZONE(ret) = 0;` -- a bare date
            // names a day rather than an instant, so there is nothing for an
            // offset to offset.
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
            // "Monday is day 1" says the spec in the C, which is not what every
            // calendar says and is the whole reason to pin it.
            assertThat(answerTo("all [now/weekday >= 1 now/weekday <= 7]"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("all [now/yearday >= 1 now/yearday <= 366]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and each part agrees with the date it came from")
        void thePartsAgreeWithTheDate() {
            // Which is what makes them worth having: `now/year` is a shorter
            // way to ask what `now/date` would have told you.
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
            // `if (!D_REF(9)) dat.nano = 0; // Not /precise`. Dropped rather
            // than rounded, so a caller timing something short and not asking
            // for precision measures zero every time.
            assertThat(answerTo("t: now/time t = to time! to integer! t"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("d: now t: d/time t = to time! to integer! t"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and with it the fraction survives")
        void withItTheFractionSurvives() {
            // Not asserted as non-zero on a single reading, which would fail
            // one time in a million on a whole second. Two readings apart
            // cannot both land on one.
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
            // `if (D_REF(10)) VAL_ZONE(ret) = 0;` -- and it is the same
            // instant, not the local wall time relabelled: the two agree only
            // where the host sits on the meridian.
            assertThat(answerTo("d: now/utc d/zone = 0:00")).isEqualTo(TRUE);
            assertThat(answerTo("date? now/utc")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the offset it drops is the one NOW/ZONE reports")
        void theOffsetItDropsIsTheOneReported() {
            // The relation that makes both answers usable together: local time
            // less the zone is UTC time. Asserted as a relation rather than
            // against a number, because it holds wherever the host is.
            assertThat(answerTo(
                    "here: now there: now/utc "
                    + "(here/hour - there/hour) = to integer! here/zone/hour"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("one question at a time")
    class OneAtATime {

        @Test
        @DisplayName("two parts at once are refused rather than combined")
        void twoPartsAreRefused() {
            // `Assert_Max_Refines(ds, D_REF(9) ? 2 : 1); // prevent too many
            // refines like: now/year/month`. The parts are alternatives, and
            // answering one of the two silently would be worse than refusing.
            assertThat(errorIdFrom("now/year/month")).isEqualTo("bad-refines");
            assertThat(errorIdFrom("now/date/time")).isEqualTo("bad-refines");
            assertThat(errorIdFrom("now/utc/zone")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("but /PRECISE does not count, because it is not a part")
        void preciseDoesNotCount() {
            // It says how to read the clock rather than which part to answer,
            // so it pairs with any one of the others.
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
