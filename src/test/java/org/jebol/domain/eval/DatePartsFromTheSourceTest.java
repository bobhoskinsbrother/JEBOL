package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading a date's parts, and reading a date literal that carries a time.
 *
 * <p>Read out of {@code PD_Date} and {@code Gregorian_To_Julian_Date} in
 * {@code t-date.c}, and every answer checked against the R3 binary. Nothing here
 * reads the clock, so every case is a fixed value.
 *
 * <p>Four of these are not what a reader would assume. SECOND is a whole number
 * until there is a fraction and a decimal after that, so the datatype of the
 * answer depends on the value. JULIAN counts from noon, so a bare day comes out
 * a whole number and not a half. Every clock part of a date with no time is none
 * rather than zero. And a written offset of zero is written as nothing at all,
 * so a date that carried an offset and a date that never did are the same
 * afterwards.
 *
 * <p>Specified in {@code spec/natives.allium} under "Reading a date's parts".
 */
class DatePartsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a literal carrying a time is one value, not a path")
    class ReadingTheLiteral {

        @Test
        @DisplayName("a date and a time read as a date")
        void aDateAndATimeAreADate() {
            assertThat(answerTo("type? first load \"[1-Jan-2000/12:00]\""))
                    .isEqualTo("#(date!)");
            assertThat(answerTo("type? first load \"[1-Jan-2000/12:30:15+2:00]\""))
                    .isEqualTo("#(date!)");
        }

        @Test
        @DisplayName("and a bare date still reads as one")
        void aBareDateStillReads() {
            assertThat(answerTo("type? first load \"[1-Jan-2000]\""))
                    .isEqualTo("#(date!)");
        }

        @Test
        @DisplayName("what it molds to reads back as the same date")
        void itRoundTrips() {
            for (String written : new String[] {
                "1-Jan-2000", "1-Jan-2000/12:00", "1-Jan-2000/12:30:15",
                "1-Jan-2000/12:00:00.5", "1-Jan-2000/12:00+2:00",
                "1-Jan-2000/12:00-5:00", "1-Jan-2000/12:00+5:45"}) {
                assertThat(answerTo("d: " + written + " d = load mold d"))
                        .as("%s reads back", written)
                        .isEqualTo(TRUE);
            }
        }

        @Test
        @DisplayName("an offset of zero is written as nothing")
        void anOffsetOfZeroIsWrittenAsNothing() {
            assertThat(answerTo("mold 1-Jan-2000/12:00+0:00"))
                    .isEqualTo("\"1-Jan-2000/12:00\"");
            assertThat(answerTo("d: 1-Jan-2000/12:00+0:00 d/zone")).isEqualTo("0:00");
            assertThat(answerTo("d: 1-Jan-2000/12:00 d/zone")).isEqualTo("0:00");
        }

        @Test
        @DisplayName("an offset needs its colon, and Z is not an offset")
        void anOffsetNeedsItsColon() {
            assertThat(answerTo("d: first load \"[1-Jan-2000/12:00+2]\" d/zone"))
                    .isEqualTo("0:00");
            assertThat(answerTo("d: first load \"[1-Jan-2000/12:00Z]\" d/zone"))
                    .isEqualTo("0:00");
        }

        @Test
        @DisplayName("a path that looks like a date is still a path")
        void aPathIsStillAPath() {
            assertThat(answerTo("type? first load \"[a/b/c]\"")).isEqualTo("#(path!)");
            assertThat(answerTo("type? first load \"[system/options]\""))
                    .isEqualTo("#(path!)");
        }
    }

    @Nested
    @DisplayName("the parts of a date that carries a time")
    class TheParts {

        private static final String NOON = "d: 1-Jan-2000/12:30:15+2:00 ";

        @Test
        @DisplayName("year, month and day are integers")
        void theCalendarParts() {
            assertThat(answerTo(NOON + "reduce [d/year d/month d/day]"))
                    .isEqualTo("[2000 1 1]");
        }

        @Test
        @DisplayName("hour, minute and second read the local clock")
        void theClockParts() {
            assertThat(answerTo(NOON + "reduce [d/hour d/minute d/second]"))
                    .isEqualTo("[12 30 15]");
        }

        @Test
        @DisplayName("time and zone are times")
        void timeAndZone() {
            assertThat(answerTo(NOON + "reduce [d/time d/zone d/timezone]"))
                    .isEqualTo("[12:30:15 2:00 2:00]");
        }

        @Test
        @DisplayName("weekday counts from Monday and yearday from January")
        void weekdayAndYearday() {
            assertThat(answerTo(NOON + "reduce [d/weekday d/yearday]"))
                    .isEqualTo("[6 1]");
            assertThat(answerTo("d: 31-Dec-2000 d/yearday")).isEqualTo("366");
        }

        @Test
        @DisplayName("date drops both the time and the offset")
        void dateDropsTheTime() {
            assertThat(answerTo(NOON + "d/date")).isEqualTo("1-Jan-2000");
            assertThat(answerTo(NOON + "e: d/date none? e/time")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("utc moves the clock back by the offset")
        void utcMovesTheClock() {
            assertThat(answerTo(NOON + "d/utc")).isEqualTo("1-Jan-2000/10:30:15");
            assertThat(answerTo("d: 1-Jan-2000/12:00-5:00 d/utc"))
                    .isEqualTo("1-Jan-2000/17:00");
            assertThat(answerTo("d: 1-Jan-2000/12:00 d/utc"))
                    .isEqualTo("1-Jan-2000/12:00");
        }

        @Test
        @DisplayName("second is whole until there is a fraction, and then it is a decimal")
        void theSecondChangesDatatype() {
            assertThat(answerTo("d: 1-Jan-2000/12:00:01 reduce [d/second integer? d/second]"))
                    .isEqualTo("[1 #(true)]");
            assertThat(answerTo("d: 1-Jan-2000/12:00:01.5 reduce [d/second decimal? d/second]"))
                    .isEqualTo("[1.5 #(true)]");
        }

        @Test
        @DisplayName("julian is a decimal, counted from noon")
        void julianCountsFromNoon() {
            assertThat(answerTo("d: 1-Jan-2000 d/julian")).isEqualTo("2451545.0");
            assertThat(answerTo(NOON + "d/julian")).isEqualTo("2451544.93767361");
        }

        @Test
        @DisplayName("a number names a part by its place in the C's word list")
        void aNumberNamesAPart() {
            assertThat(answerTo(NOON + "reduce [d/1 d/2 d/3 d/4]"))
                    .isEqualTo("[2000 1 1 12:30:15]");
            assertThat(answerTo(NOON + "reduce [d/5 d/6 d/7 d/8 d/9]"))
                    .isEqualTo("[1-Jan-2000 2:00 12 30 15]");
            assertThat(answerTo(NOON + "reduce [d/10 d/11 d/12]"))
                    .isEqualTo("[6 1 2:00]");
            assertThat(answerTo(NOON + "reduce [d/13 d/14]"))
                    .isEqualTo("[1-Jan-2000/10:30:15 2451544.93767361]");
        }

        @Test
        @DisplayName("and a number outside the list answers none, as an unknown name does")
        void aNumberOutsideTheList() {
            assertThat(answerTo(NOON + "reduce [none? d/0 none? d/15 none? d/invented]"))
                    .isEqualTo("[#(true) #(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("a day with no time has no clock")
    class ADayAlone {

        @Test
        @DisplayName("time, hour, minute, second and zone are all none")
        void theClockPartsAreNone() {
            assertThat(answerTo(
                    "d: 1-Jan-2000 reduce [none? d/time none? d/hour none? d/minute "
                    + "none? d/second none? d/zone]"))
                    .isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("but the calendar parts all answer")
        void theCalendarPartsAnswer() {
            assertThat(answerTo(
                    "d: 1-Jan-2000 reduce [d/year d/month d/day d/weekday d/yearday]"))
                    .isEqualTo("[2000 1 1 6 1]");
        }

        @Test
        @DisplayName("and so do date, utc and julian")
        void theWholeDayParts() {
            assertThat(answerTo("d: 1-Jan-2000 reduce [d/date d/utc d/julian]"))
                    .isEqualTo("[1-Jan-2000 1-Jan-2000 2451545.0]");
        }
    }
}
