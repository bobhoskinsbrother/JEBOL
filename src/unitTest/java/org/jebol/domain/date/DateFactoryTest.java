package org.jebol.domain.date;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DateFactoryTest {

    private static DateValue madeFrom(Value... parts) {
        return (DateValue) DateFactory.fromParts(List.of(parts));
    }

    private static String refusalOf(Value... parts) {
        try {
            DateFactory.fromParts(List.of(parts));
            return "nothing refused";
        } catch (Raised raised) {
            return raised.error().errorId();
        }
    }

    private static DateValue atMicroseconds(long microseconds) {
        return (DateValue) DateFactory.fromTimestamp(microseconds);
    }

    private static DateValue atWholeSeconds(long seconds) {
        return (DateValue) DateFactory.fromWholeSeconds(seconds);
    }

    private static String refusalOfMicroseconds(long microseconds) {
        try {
            DateFactory.fromTimestamp(microseconds);
            return "nothing refused";
        } catch (Raised raised) {
            return raised.error().errorId();
        }
    }

    private static String refusalOfWholeSeconds(long seconds) {
        try {
            DateFactory.fromWholeSeconds(seconds);
            return "nothing refused";
        } catch (Raised raised) {
            return raised.error().errorId();
        }
    }

    private static final long EARLIEST_WHOLE_SECOND = -62_167_219_200L;
    private static final long LATEST_WHOLE_SECOND = 454_861_871_999L;

    @Nested
    @DisplayName("a calendar day from three numbers")
    class TheCalendarDay {

        @Test
        @DisplayName("the first number is the day until it passes ninety-nine, then it is the year")
        void theFirstNumberIsTheDayUntilNinetyNine() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(2), IntegerValue.of(3)))
                    .isEqualTo(DateValue.of(3, 2, 1));
            assertThat(madeFrom(IntegerValue.of(100), IntegerValue.of(2), IntegerValue.of(1)))
                    .isEqualTo(DateValue.of(100, 2, 1));
            assertThat(refusalOf(
                    IntegerValue.of(99), IntegerValue.of(2), IntegerValue.of(1)))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("the first and last supported years are kept")
        void keepsTheFirstAndLastSupportedYears() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(0)))
                    .isEqualTo(DateValue.of(0, 1, 1));
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(16_383)))
                    .isEqualTo(DateValue.of(16_383, 1, 1));
        }

        @Test
        @DisplayName("a year below nought or above sixteen thousand three hundred and eighty-three is refused")
        void refusesAYearOutsideTheSupportedSpan() {
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(-1)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(16_384)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1),
                    IntegerValue.of(Integer.MAX_VALUE))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1),
                    IntegerValue.of((long) Integer.MAX_VALUE + 1L))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1),
                    IntegerValue.of(Long.MAX_VALUE))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1),
                    IntegerValue.of(Long.MIN_VALUE))).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a first number that would become the year is held to the same span")
        void holdsTheFirstNumberToTheSameYearSpan() {
            assertThat(madeFrom(IntegerValue.of(16_383), IntegerValue.of(1), IntegerValue.of(1)))
                    .isEqualTo(DateValue.of(16_383, 1, 1));
            assertThat(refusalOf(
                    IntegerValue.of(16_384), IntegerValue.of(1), IntegerValue.of(1)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(
                    IntegerValue.of(Long.MAX_VALUE), IntegerValue.of(1), IntegerValue.of(1)))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a month runs from one to twelve and nowhere else")
        void aMonthRunsFromOneToTwelve() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 1, 1));
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(12), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 12, 31));
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(0), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(13), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(-1), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(Long.MAX_VALUE),
                    IntegerValue.of(2000))).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a day runs from one to the length of its month")
        void aDayRunsToTheLengthOfItsMonth() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(4), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 4, 1));
            assertThat(madeFrom(IntegerValue.of(30), IntegerValue.of(4), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 4, 30));
            assertThat(refusalOf(IntegerValue.of(31), IntegerValue.of(4), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(31), IntegerValue.of(6), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(31), IntegerValue.of(9), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(31), IntegerValue.of(11), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(1), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 1, 31));
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(3), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 3, 31));
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(5), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 5, 31));
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(7), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 7, 31));
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(8), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 8, 31));
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(10), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 10, 31));
            assertThat(refusalOf(IntegerValue.of(0), IntegerValue.of(1), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(-1), IntegerValue.of(1), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(Long.MAX_VALUE), IntegerValue.of(1),
                    IntegerValue.of(2000))).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("February takes a twenty-ninth only in a leap year")
        void februaryTakesATwentyNinthOnlyInALeapYear() {
            assertThat(madeFrom(IntegerValue.of(29), IntegerValue.of(2), IntegerValue.of(2000)))
                    .isEqualTo(DateValue.of(2000, 2, 29));
            assertThat(madeFrom(IntegerValue.of(29), IntegerValue.of(2), IntegerValue.of(1600)))
                    .isEqualTo(DateValue.of(1600, 2, 29));
            assertThat(madeFrom(IntegerValue.of(29), IntegerValue.of(2), IntegerValue.of(0)))
                    .isEqualTo(DateValue.of(0, 2, 29));
            assertThat(madeFrom(IntegerValue.of(29), IntegerValue.of(2), IntegerValue.of(4)))
                    .isEqualTo(DateValue.of(4, 2, 29));
            assertThat(refusalOf(IntegerValue.of(29), IntegerValue.of(2), IntegerValue.of(1900)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(29), IntegerValue.of(2), IntegerValue.of(2100)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(29), IntegerValue.of(2), IntegerValue.of(2001)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(29), IntegerValue.of(2), IntegerValue.of(100)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(30), IntegerValue.of(2), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a first number over ninety-nine is the year, and the third is then the day")
        void aFirstNumberOverNinetyNineIsTheYear() {
            assertThat(madeFrom(IntegerValue.of(2000), IntegerValue.of(2), IntegerValue.of(29)))
                    .isEqualTo(DateValue.of(2000, 2, 29));
            assertThat(refusalOf(
                    IntegerValue.of(2000), IntegerValue.of(2), IntegerValue.of(30)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(
                    IntegerValue.of(2000), IntegerValue.of(2), IntegerValue.of(-1)))
                    .isEqualTo("bad-make-arg");
        }

    }

    @Nested
    @DisplayName("a clock from three numbers")
    class TheClockFromThreeNumbers {

        @Test
        @DisplayName("the hour runs from nought to twenty-three")
        void theHourRunsFromNoughtToTwentyThree() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(0), IntegerValue.of(0), IntegerValue.of(0))
                    .timeOfDay()).contains(TimeValue.ofNanoseconds(0));
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(23), IntegerValue.of(59), IntegerValue.of(59))
                    .timeOfDay()).contains(TimeValue.of(23, 59, 59, 0));
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(-1), IntegerValue.of(0), IntegerValue.of(0)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(24), IntegerValue.of(0), IntegerValue.of(0)))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("the minute runs from nought to fifty-nine")
        void theMinuteRunsFromNoughtToFiftyNine() {
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(0), IntegerValue.of(-1), IntegerValue.of(0)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(0), IntegerValue.of(60), IntegerValue.of(0)))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("the second runs from nought up to but not including sixty")
        void theSecondRunsUpToButNotIncludingSixty() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(0), IntegerValue.of(0), IntegerValue.of(59))
                    .timeOfDay()).contains(TimeValue.of(0, 0, 59, 0));
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(0), IntegerValue.of(0), DecimalValue.of(59.999999999))
                    .timeOfDay()).contains(TimeValue.ofNanoseconds(59_999_999_999L));
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(0), IntegerValue.of(0), IntegerValue.of(-1)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(0), IntegerValue.of(0), IntegerValue.of(60)))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a fraction that rounds up to sixty carries the clock into the next minute")
        void aFractionThatRoundsToSixtyCarriesTheClock() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(0), IntegerValue.of(0), DecimalValue.of(59.9999999999)))
                    .isEqualTo(DateValue.of(2000, 1, 1, TimeValue.of(0, 1, 0, 0)));
        }

        @Test
        @DisplayName("a clock ending the day carries the calendar into the next day")
        void aClockEndingTheDayCarriesTheCalendar() {
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(12), IntegerValue.of(2000),
                    IntegerValue.of(23), IntegerValue.of(59), DecimalValue.of(59.9999999999)))
                    .isEqualTo(DateValue.of(2001, 1, 1, TimeValue.ofNanoseconds(0)));
        }
    }

    @Nested
    @DisplayName("a clock given as a time")
    class TheClockGivenAsATime {

        @Test
        @DisplayName("keeps the clock when it is inside a day")
        void keepsAClockInsideADay() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2000, 1, 1, TimeValue.of(4, 0, 0, 0)));
        }

        @Test
        @DisplayName("carries the calendar forward for every whole day in the time")
        void carriesTheCalendarForward() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(24, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2000, 1, 2, TimeValue.ofNanoseconds(0)));
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(25, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2000, 1, 2, TimeValue.of(1, 0, 0, 0)));
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(100, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2000, 1, 5, TimeValue.of(4, 0, 0, 0)));
        }

        @Test
        @DisplayName("carries the calendar backward for a time before nought")
        void carriesTheCalendarBackward() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(-1, 0, 0, 0)))
                    .isEqualTo(DateValue.of(1999, 12, 31, TimeValue.of(23, 0, 0, 0)));
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(-24, 0, 0, 0)))
                    .isEqualTo(DateValue.of(1999, 12, 31, TimeValue.ofNanoseconds(0)));
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(-100, 0, 0, 0)))
                    .isEqualTo(DateValue.of(1999, 12, 27, TimeValue.of(20, 0, 0, 0)));
        }

        @Test
        @DisplayName("crosses the end of a month, a year and a leap day")
        void crossesAMonthAYearAndALeapDay() {
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(24, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2000, 2, 1, TimeValue.ofNanoseconds(0)));
            assertThat(madeFrom(IntegerValue.of(31), IntegerValue.of(12), IntegerValue.of(2000),
                    TimeValue.of(24, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2001, 1, 1, TimeValue.ofNanoseconds(0)));
            assertThat(madeFrom(IntegerValue.of(28), IntegerValue.of(2), IntegerValue.of(2000),
                    TimeValue.of(24, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2000, 2, 29, TimeValue.ofNanoseconds(0)));
            assertThat(madeFrom(IntegerValue.of(28), IntegerValue.of(2), IntegerValue.of(2001),
                    TimeValue.of(24, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2001, 3, 1, TimeValue.ofNanoseconds(0)));
        }

        @Test
        @DisplayName("a time that would leave the supported years is refused")
        void refusesATimeThatLeavesTheSupportedYears() {
            assertThat(refusalOf(IntegerValue.of(31), IntegerValue.of(12), IntegerValue.of(16_383),
                    TimeValue.of(24, 0, 0, 0))).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a whole day comes to rest at midnight and a nanosecond short stays inside the day")
        void aWholeDayComesToRestAtMidnight() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.ofNanoseconds(TimeValue.NANOSECONDS_PER_DAY - 1)))
                    .isEqualTo(DateValue.of(2000, 1, 1,
                            TimeValue.ofNanoseconds(TimeValue.NANOSECONDS_PER_DAY - 1)));
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.ofNanoseconds(-1)))
                    .isEqualTo(DateValue.of(1999, 12, 31,
                            TimeValue.ofNanoseconds(TimeValue.NANOSECONDS_PER_DAY - 1)));
        }
    }

    @Nested
    @DisplayName("a zone after the clock")
    class TheZone {

        @Test
        @DisplayName("is absent when no second time is given")
        void isAbsentWithoutASecondTime() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0)).zoneMinutes()).isEmpty();
        }

        @Test
        @DisplayName("keeps the sign and the minute it was given")
        void keepsTheSignAndTheMinute() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0), TimeValue.of(2, 0, 0, 0)).zoneMinutes())
                    .contains(120);
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0), TimeValue.of(-2, 0, 0, 0)).zoneMinutes())
                    .contains(-120);
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0), TimeValue.of(0, 0, 0, 0)).zoneMinutes())
                    .contains(0);
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0), TimeValue.of(2, 37, 30, 0)).zoneMinutes())
                    .contains(157);
        }

        @Test
        @DisplayName("reaches fifteen hours and no further")
        void reachesFifteenHoursAndNoFurther() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0), TimeValue.of(15, 0, 59, 0)).zoneMinutes())
                    .contains(900);
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0), TimeValue.of(-15, 0, 0, 0)).zoneMinutes())
                    .contains(-900);
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0), TimeValue.of(15, 1, 0, 0)))
                    .isEqualTo("out-of-range");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0), TimeValue.ofNanoseconds(
                            -901L * 60L * TimeValue.NANOSECONDS_PER_SECOND)))
                    .isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("is read only after a clock, so a lone second time is refused")
        void isReadOnlyAfterAClock() {
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(4, 0, 0, 0), TimeValue.of(2, 0, 0, 0), TimeValue.of(3, 0, 0, 0)))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a single time after the calendar is the clock, not a zone")
        void aSingleTimeAfterTheCalendarIsTheClock() {
            assertThat(madeFrom(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(2, 30, 0, 0)))
                    .isEqualTo(DateValue.of(2000, 1, 1, TimeValue.of(2, 30, 0, 0)));
        }
    }

    @Nested
    @DisplayName("a date given as its own first part")
    class ADateGivenAsItsFirstPart {

        private final DateValue given = new DateValue(2000, 1, 2,
                Optional.of(TimeValue.of(10, 30, 0, 0)), Optional.of(120));

        @Test
        @DisplayName("comes back whole when it is the only part")
        void comesBackWholeAlone() {
            assertThat(madeFrom(given)).isEqualTo(given);
        }

        @Test
        @DisplayName("keeps its day and takes the clock that follows it")
        void keepsItsDayAndTakesTheFollowingClock() {
            assertThat(madeFrom(given, TimeValue.of(11, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2000, 1, 2, TimeValue.of(11, 0, 0, 0)));
            assertThat(madeFrom(given, IntegerValue.of(11), IntegerValue.of(0),
                    IntegerValue.of(0)))
                    .isEqualTo(DateValue.of(2000, 1, 2, TimeValue.of(11, 0, 0, 0)));
        }

        @Test
        @DisplayName("takes the zone that follows the new clock")
        void takesTheFollowingZone() {
            assertThat(madeFrom(given, TimeValue.of(11, 0, 0, 0), TimeValue.of(3, 0, 0, 0)))
                    .isEqualTo(new DateValue(2000, 1, 2,
                            Optional.of(TimeValue.of(11, 0, 0, 0)), Optional.of(180)));
            assertThat(madeFrom(given, IntegerValue.of(11), IntegerValue.of(0),
                    IntegerValue.of(0), TimeValue.of(3, 0, 0, 0)))
                    .isEqualTo(new DateValue(2000, 1, 2,
                            Optional.of(TimeValue.of(11, 0, 0, 0)), Optional.of(180)));
        }

        @Test
        @DisplayName("carries the day when the new clock passes midnight")
        void carriesTheDayForANewClock() {
            assertThat(madeFrom(given, TimeValue.of(25, 0, 0, 0)))
                    .isEqualTo(DateValue.of(2000, 1, 3, TimeValue.of(1, 0, 0, 0)));
        }

        @Test
        @DisplayName("refuses a part after it that is neither a clock nor its three numbers")
        void refusesAnythingElseAfterIt() {
            assertThat(refusalOf(given, StringValue.of("x"))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(given, DateValue.of(2001, 2, 3))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(given, IntegerValue.of(11))).isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("a timestamp counted in microseconds")
    class AMicrosecondTimestamp {

        @Test
        @DisplayName("nought is the start of nineteen seventy")
        void noughtIsTheStartOfNineteenSeventy() {
            assertThat(atMicroseconds(0))
                    .isEqualTo(DateValue.of(1970, 1, 1, TimeValue.ofNanoseconds(0)));
            assertThat(atMicroseconds(1))
                    .isEqualTo(DateValue.of(1970, 1, 1, TimeValue.ofNanoseconds(1_000)));
            assertThat(atMicroseconds(999_999))
                    .isEqualTo(DateValue.of(1970, 1, 1, TimeValue.ofNanoseconds(999_999_000)));
            assertThat(atMicroseconds(1_000_000))
                    .isEqualTo(DateValue.of(1970, 1, 1, TimeValue.ofNanoseconds(1_000_000_000)));
        }

        @Test
        @DisplayName("a negative count stays on the day before, with the clock at its end")
        void aNegativeCountStaysOnTheDayBefore() {
            assertThat(atMicroseconds(-1))
                    .isEqualTo(DateValue.of(1969, 12, 31,
                            TimeValue.ofNanoseconds(86_399_999_999_000L)));
            assertThat(atMicroseconds(-1_000_000))
                    .isEqualTo(DateValue.of(1969, 12, 31, TimeValue.of(23, 59, 59, 0)));
        }

        @Test
        @DisplayName("a whole day moves the calendar by exactly one")
        void aWholeDayMovesTheCalendarByOne() {
            assertThat(atMicroseconds(86_400_000_000L))
                    .isEqualTo(DateValue.of(1970, 1, 2, TimeValue.ofNanoseconds(0)));
            assertThat(atMicroseconds(86_400_000_000L - 1))
                    .isEqualTo(DateValue.of(1970, 1, 1,
                            TimeValue.ofNanoseconds(86_399_999_999_000L)));
            assertThat(atMicroseconds(-86_400_000_000L))
                    .isEqualTo(DateValue.of(1969, 12, 31, TimeValue.ofNanoseconds(0)));
            assertThat(atMicroseconds(-86_400_000_000L - 1))
                    .isEqualTo(DateValue.of(1969, 12, 30,
                            TimeValue.ofNanoseconds(86_399_999_999_000L)));
        }

        @Test
        @DisplayName("keeps the first and last microsecond of the supported years")
        void keepsTheFirstAndLastMicrosecond() {
            long earliest = EARLIEST_WHOLE_SECOND * 1_000_000L;
            long latest = LATEST_WHOLE_SECOND * 1_000_000L + 999_999L;
            assertThat(atMicroseconds(earliest))
                    .isEqualTo(DateValue.of(0, 1, 1, TimeValue.ofNanoseconds(0)));
            assertThat(atMicroseconds(latest))
                    .isEqualTo(DateValue.of(16_383, 12, 31,
                            TimeValue.ofNanoseconds(86_399_999_999_000L)));
        }

        @Test
        @DisplayName("refuses a microsecond before or after the supported years rather than wrapping")
        void refusesOutsideTheSupportedYears() {
            assertThat(refusalOfMicroseconds(EARLIEST_WHOLE_SECOND * 1_000_000L - 1))
                    .isEqualTo("out-of-range");
            assertThat(refusalOfMicroseconds(
                    LATEST_WHOLE_SECOND * 1_000_000L + 1_000_000L))
                    .isEqualTo("out-of-range");
            assertThat(refusalOfMicroseconds(Long.MIN_VALUE)).isEqualTo("out-of-range");
            assertThat(refusalOfMicroseconds(Long.MAX_VALUE)).isEqualTo("out-of-range");
        }
    }

    @Nested
    @DisplayName("a timestamp counted in whole seconds")
    class AWholeSecondTimestamp {

        @Test
        @DisplayName("counts forward and backward from the start of nineteen seventy")
        void countsForwardAndBackward() {
            assertThat(atWholeSeconds(0))
                    .isEqualTo(DateValue.of(1970, 1, 1, TimeValue.ofNanoseconds(0)));
            assertThat(atWholeSeconds(1))
                    .isEqualTo(DateValue.of(1970, 1, 1, TimeValue.ofNanoseconds(1_000_000_000)));
            assertThat(atWholeSeconds(-1))
                    .isEqualTo(DateValue.of(1969, 12, 31, TimeValue.of(23, 59, 59, 0)));
            assertThat(atWholeSeconds(86_400))
                    .isEqualTo(DateValue.of(1970, 1, 2, TimeValue.ofNanoseconds(0)));
            assertThat(atWholeSeconds(-86_400))
                    .isEqualTo(DateValue.of(1969, 12, 31, TimeValue.ofNanoseconds(0)));
        }

        @Test
        @DisplayName("a leap second is not a second the count knows: the moments either side are adjacent")
        void aLeapSecondIsNotInTheCount() {
            assertThat(atWholeSeconds(78_796_799))
                    .isEqualTo(DateValue.of(1972, 6, 30, TimeValue.of(23, 59, 59, 0)));
            assertThat(atWholeSeconds(78_796_800))
                    .isEqualTo(DateValue.of(1972, 7, 1, TimeValue.ofNanoseconds(0)));
            assertThat(atWholeSeconds(1_483_228_799))
                    .isEqualTo(DateValue.of(2016, 12, 31, TimeValue.of(23, 59, 59, 0)));
            assertThat(atWholeSeconds(1_483_228_800))
                    .isEqualTo(DateValue.of(2017, 1, 1, TimeValue.ofNanoseconds(0)));
        }

        @Test
        @DisplayName("keeps the first and last second of the supported years")
        void keepsTheFirstAndLastSecond() {
            assertThat(atWholeSeconds(EARLIEST_WHOLE_SECOND))
                    .isEqualTo(DateValue.of(0, 1, 1, TimeValue.ofNanoseconds(0)));
            assertThat(atWholeSeconds(LATEST_WHOLE_SECOND))
                    .isEqualTo(DateValue.of(16_383, 12, 31, TimeValue.of(23, 59, 59, 0)));
        }

        @Test
        @DisplayName("refuses a second before or after the supported years rather than wrapping")
        void refusesOutsideTheSupportedYears() {
            assertThat(refusalOfWholeSeconds(EARLIEST_WHOLE_SECOND - 1))
                    .isEqualTo("out-of-range");
            assertThat(refusalOfWholeSeconds(LATEST_WHOLE_SECOND + 1))
                    .isEqualTo("out-of-range");
            assertThat(refusalOfWholeSeconds(Long.MIN_VALUE)).isEqualTo("out-of-range");
            assertThat(refusalOfWholeSeconds(Long.MAX_VALUE)).isEqualTo("out-of-range");
        }

    }

    @Nested
    @DisplayName("wrong and missing parts")
    class WrongAndMissingParts {

        @Test
        @DisplayName("an empty block is refused")
        void anEmptyBlockIsRefused() {
            assertThat(refusalOf()).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("the calendar needs three integers")
        void theCalendarNeedsThreeIntegers() {
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(StringValue.of("1"), IntegerValue.of(1), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), StringValue.of("1"), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), StringValue.of("2000")))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(DecimalValue.of(1), IntegerValue.of(1), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), DecimalValue.of(1), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), DecimalValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(BlockValue.of(IntegerValue.of(1), IntegerValue.of(1),
                    IntegerValue.of(2000)))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(TimeValue.of(1, 0, 0, 0), IntegerValue.of(1),
                    IntegerValue.of(2000))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(LogicValue.yes(), IntegerValue.of(1), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(NoneValue.none(), IntegerValue.of(1), IntegerValue.of(2000)))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("the clock needs two integers and a number")
        void theClockNeedsTwoIntegersAndANumber() {
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    DecimalValue.of(1), IntegerValue.of(0), IntegerValue.of(0)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(1), DecimalValue.of(0), IntegerValue.of(0)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(1), IntegerValue.of(0), StringValue.of("0")))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(1), IntegerValue.of(0), TimeValue.of(0, 0, 0, 0)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(1), IntegerValue.of(0), LogicValue.yes()))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(1), IntegerValue.of(0),
                    BlockValue.of(IntegerValue.of(0)))).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a clock of three integers may not stop short")
        void aClockMayNotStopShort() {
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(1), IntegerValue.of(0))).isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    IntegerValue.of(1))).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a zone is a time and nothing else")
        void aZoneIsATimeAndNothingElse() {
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(1, 0, 0, 0), IntegerValue.of(2)))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(1, 0, 0, 0), StringValue.of("2:00")))
                    .isEqualTo("bad-make-arg");
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(1, 0, 0, 0), BlockValue.of(TimeValue.of(2, 0, 0, 0))))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("parts left over after the zone are refused")
        void partsLeftOverAreRefused() {
            assertThat(refusalOf(IntegerValue.of(1), IntegerValue.of(1), IntegerValue.of(2000),
                    TimeValue.of(1, 0, 0, 0), TimeValue.of(2, 0, 0, 0),
                    IntegerValue.of(3))).isEqualTo("bad-make-arg");
        }
    }
}
