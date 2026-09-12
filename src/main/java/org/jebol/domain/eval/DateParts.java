package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;

final class DateParts {

    private static final List<String> IN_THE_ORDER_A_NUMBER_COUNTS_THEM = List.of(
            "year", "month", "day", "time", "date", "zone", "hour", "minute",
            "second", "weekday", "yearday", "timezone", "utc", "julian");

    private static final long NANOSECONDS_A_DAY = 24L * 60L * 60L * 1_000_000_000L;

    private DateParts() {
    }

    static List<String> partNames() {
        return IN_THE_ORDER_A_NUMBER_COUNTS_THEM;
    }

    static DateValue written(DateValue date, Value selector, Value given) {
        if (!(selector instanceof WordValue named)
                || !IN_THE_ORDER_A_NUMBER_COUNTS_THEM.contains(named.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    selector instanceof WordValue word ? word.spelling() : "date");
        }
        return switch (named.canonical()) {
            case "zone" -> withTheSameClockIn(startedAtMidnightIfItHadNoClock(date),
                    offsetAskedFor(given));
            case "timezone" -> atTheSameInstantIn(startedAtMidnightIfItHadNoClock(date),
                    offsetAskedFor(given));
            case "year" -> onTheDay(date, wholeNumberIn(given), date.month(), date.day());
            case "month" -> onTheDay(date, date.year(), wholeNumberIn(given), date.day());
            case "day" -> onTheDay(date, date.year(), date.month(), wholeNumberIn(given));
            case "hour" -> atTheTime(date, withTheHour(clockOf(date), wholeNumberIn(given)));
            case "minute" ->
                    atTheTime(date, withTheMinute(clockOf(date), wholeNumberIn(given)));
            case "second" -> atTheTime(date, withTheSecond(clockOf(date), given));
            case "time" -> atTheTimeGiven(date, given);
            case "date" -> theDayOfAnotherDateKeepingThisClock(given, date);
            case "utc" -> theWholeDateWithItsZoneCalledNothing(given);
            case "yearday" -> theYearAndDayOf(date, wholeNumberIn(given));
            default -> throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                    named.spelling());
        };
    }

    private static DateValue startedAtMidnightIfItHadNoClock(DateValue date) {
        return date.timeOfDay().isPresent()
                ? date
                : new DateValue(date.year(), date.month(), date.day(),
                        java.util.Optional.of(TimeValue.ofNanoseconds(0)),
                        java.util.Optional.empty());
    }

    private static TimeValue clockOf(DateValue date) {
        return date.timeOfDay().orElseGet(() -> TimeValue.ofNanoseconds(0));
    }

    private static int wholeNumberIn(Value given) {
        return switch (given) {
            case IntegerValue number -> Math.toIntExact(number.magnitude());
            case DecimalValue number -> (int) number.quantity();
            case NoneValue nothing -> 0;
            default -> throw Raised.of(EvaluationFailure.BAD_FIELD_SET, given);
        };
    }

    private static DateValue onTheDay(DateValue was, int year, int month, int day) {
        return sameClockOn(was, aMonthOrDayPastItsRangeRollsOn(year, month, day));
    }

    private static java.time.LocalDate aMonthOrDayPastItsRangeRollsOn(
            int year, int month, int day) {
        return java.time.LocalDate.of(year, 1, 1)
                .plusMonths(month - 1L)
                .plusDays(day - 1L);
    }

    private static DateValue sameClockOn(DateValue was, java.time.LocalDate day) {
        return new DateValue(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                was.timeOfDay(), was.zoneMinutes());
    }

    private static DateValue atTheTime(DateValue was, TimeValue clock) {
        return new DateValue(was.year(), was.month(), was.day(),
                java.util.Optional.of(clock), was.zoneMinutes());
    }

    private static TimeValue withTheHour(TimeValue clock, int hours) {
        return TimeValue.ofNanoseconds(clock.nanoseconds()
                - hoursPartOf(clock) * NANOSECONDS_AN_HOUR
                + (long) hours * NANOSECONDS_AN_HOUR);
    }

    private static TimeValue withTheMinute(TimeValue clock, int minutes) {
        return TimeValue.ofNanoseconds(clock.nanoseconds()
                - minutesPartOf(clock) * NANOSECONDS_A_MINUTE
                + (long) minutes * NANOSECONDS_A_MINUTE);
    }

    private static TimeValue withTheSecond(TimeValue clock, Value given) {
        long asked = given instanceof DecimalValue fraction
                ? (long) (fraction.quantity() * NANOSECONDS_IN_A_SECOND)
                : (long) wholeNumberIn(given) * NANOSECONDS_IN_A_SECOND;
        long secondsPart = clock.nanoseconds()
                - hoursPartOf(clock) * NANOSECONDS_AN_HOUR
                - minutesPartOf(clock) * NANOSECONDS_A_MINUTE;
        return TimeValue.ofNanoseconds(clock.nanoseconds() - secondsPart + asked);
    }

    private static long hoursPartOf(TimeValue clock) {
        return clock.nanoseconds() / NANOSECONDS_AN_HOUR;
    }

    private static long minutesPartOf(TimeValue clock) {
        return clock.nanoseconds() % NANOSECONDS_AN_HOUR / NANOSECONDS_A_MINUTE;
    }

    private static final long NANOSECONDS_IN_A_SECOND = 1_000_000_000L;

    private static final long NANOSECONDS_A_MINUTE = 60L * NANOSECONDS_IN_A_SECOND;

    private static final long NANOSECONDS_AN_HOUR = 60L * NANOSECONDS_A_MINUTE;

    private static DateValue atTheTimeGiven(DateValue was, Value given) {
        return switch (given) {
            case NoneValue nothing -> noneTakesTheZoneWithIt(was);
            case TimeValue clock -> atTheTime(was, clock);
            case DateValue other -> atTheTime(was,
                    other.timeOfDay().orElseGet(() -> TimeValue.ofNanoseconds(0)));
            case IntegerValue seconds -> atTheTime(was,
                    TimeValue.ofNanoseconds(seconds.magnitude() * NANOSECONDS_IN_A_SECOND));
            case DecimalValue seconds -> atTheTime(was, TimeValue.ofNanoseconds(
                    (long) (seconds.quantity() * NANOSECONDS_IN_A_SECOND)));
            default -> throw Raised.of(EvaluationFailure.BAD_FIELD_SET, given);
        };
    }

    private static DateValue noneTakesTheZoneWithIt(DateValue was) {
        return DateValue.of(was.year(), was.month(), was.day());
    }

    private static DateValue theDayOfAnotherDateKeepingThisClock(
            Value given, DateValue was) {
        if (!(given instanceof DateValue other)) {
            throw Raised.of(EvaluationFailure.BAD_FIELD_SET, given);
        }
        return new DateValue(other.year(), other.month(), other.day(),
                was.timeOfDay(), was.zoneMinutes());
    }

    private static DateValue theWholeDateWithItsZoneCalledNothing(Value given) {
        if (!(given instanceof DateValue other)) {
            throw Raised.of(EvaluationFailure.BAD_FIELD_SET, given);
        }
        return new DateValue(other.year(), other.month(), other.day(),
                other.timeOfDay(),
                other.timeOfDay().isPresent()
                        ? java.util.Optional.of(0)
                        : java.util.Optional.empty());
    }

    private static DateValue theYearAndDayOf(DateValue was, int dayOfYear) {
        return sameClockOn(was, java.time.LocalDate.of(was.year(), 1, 1)
                .plusDays(dayOfYear - 1L));
    }

    private static int offsetAskedFor(Value given) {
        if (given instanceof IntegerValue aBareNumberMeansHours) {
            return withinReach(Math.toIntExact(aBareNumberMeansHours.magnitude()) * 60);
        }
        if (given instanceof DecimalValue aBareNumberMeansHours) {
            return withinReach((int) aBareNumberMeansHours.quantity() * 60);
        }
        if (given instanceof TimeValue clock) {
            return withinReach(
                    (int) (clock.nanoseconds() / (60L * NANOSECONDS_A_SECOND)));
        }
        throw Raised.of(EvaluationFailure.BAD_FIELD_SET, Molder.mold(given));
    }

    private static int withinReach(int offsetMinutes) {
        if (Math.abs(offsetMinutes) > MOST_A_ZONE_MAY_BE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    IntegerValue.of(offsetMinutes));
        }
        return offsetMinutes;
    }

    private static final int MOST_A_ZONE_MAY_BE = 15 * 60 + 45;

    private static DateValue withTheSameClockIn(DateValue date, int offsetMinutes) {
        return new DateValue(date.year(), date.month(), date.day(),
                Optional.of(date.timeOfDay().orElseGet(() -> TimeValue.ofNanoseconds(0))),
                Optional.of(offsetMinutes));
    }

    private static DateValue atTheSameInstantIn(DateValue date, int offsetMinutes) {
        DateValue standing = withTheSameClockIn(date, date.zoneMinutes().orElse(0));
        long sinceMidnight = standing.timeOfDay().orElseThrow().nanoseconds()
                + (offsetMinutes - standing.zoneMinutes().orElse(0))
                        * 60L * NANOSECONDS_A_SECOND;
        long daysOver = Math.floorDiv(sinceMidnight, NANOSECONDS_A_DAY);
        java.time.LocalDate day = java.time.LocalDate
                .of(standing.year(), standing.month(), standing.day())
                .plusDays(daysOver);
        return new DateValue(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(
                        Math.floorMod(sinceMidnight, NANOSECONDS_A_DAY))),
                Optional.of(offsetMinutes));
    }



    static Value of(DateValue date, Value selector) {
        String part = switch (selector) {
            case WordValue named -> named.canonical();
            case IntegerValue position -> position.magnitude() >= 1
                    && position.magnitude() <= IN_THE_ORDER_A_NUMBER_COUNTS_THEM.size()
                    ? IN_THE_ORDER_A_NUMBER_COUNTS_THEM.get((int) position.magnitude() - 1)
                    : "";
            default -> "";
        };
        boolean aboutTheClock = List.of("time", "zone", "timezone", "hour", "minute", "second")
                .contains(part);
        if (date.timeOfDay().isEmpty() && aboutTheClock) {
            return NoneValue.none();
        }
        return switch (part) {
            case "year" -> IntegerValue.of(date.year());
            case "month" -> IntegerValue.of(date.month());
            case "day" -> IntegerValue.of(date.day());
            case "time" -> date.timeOfDay().get();
            case "date" -> DateValue.of(date.year(), date.month(), date.day());
            case "zone", "timezone" -> TimeValue.ofNanoseconds(
                    date.zoneMinutes().orElse(0) * 60L * NANOSECONDS_A_SECOND);
            case "hour" -> IntegerValue.of(hoursOf(date));
            case "minute" -> IntegerValue.of(minutesOf(date));
            case "second" -> wholeSecondOrDecimalWhereThereIsAFraction(date);
            case "weekday" -> IntegerValue.of(asLocalDate(date).getDayOfWeek().getValue());
            case "yearday" -> IntegerValue.of(asLocalDate(date).getDayOfYear());
            case "utc" -> date.asStoredInUtc();
            case "julian" -> DecimalValue.of(julianDayCountedFromNoon(date));
            default -> NoneValue.none();
        };
    }

    private static final long NANOSECONDS_A_SECOND = 1_000_000_000L;

    private static java.time.LocalDate asLocalDate(DateValue date) {
        return java.time.LocalDate.of(date.year(), date.month(), date.day());
    }

    private static long nanosecondsOf(DateValue date) {
        return date.timeOfDay().map(TimeValue::nanoseconds).orElse(0L);
    }

    private static int hoursOf(DateValue date) {
        return (int) (nanosecondsOf(date) / NANOSECONDS_A_SECOND / 3600);
    }

    private static int minutesOf(DateValue date) {
        return (int) (nanosecondsOf(date) / NANOSECONDS_A_SECOND / 60 % 60);
    }

    private static Value wholeSecondOrDecimalWhereThereIsAFraction(DateValue date) {
        long nanoseconds = nanosecondsOf(date);
        long whole = nanoseconds / NANOSECONDS_A_SECOND % 60;
        long fraction = nanoseconds % NANOSECONDS_A_SECOND;
        return fraction == 0
                ? IntegerValue.of(whole)
                : DecimalValue.of(whole + (double) fraction / NANOSECONDS_A_SECOND);
    }

    private static double julianDayCountedFromNoon(DateValue date) {
        long nanoseconds = date.timeOfDay().isEmpty()
                ? NOON_GIVEN_TO_A_DATE_WITH_NO_CLOCK
                : inUniversalTime(date);
        long seconds = Math.abs(nanoseconds) / NANOSECONDS_A_SECOND;
        long hours = seconds / 3600;
        long minutes = seconds / 60 % 60;
        long wholeSeconds = seconds % 60;
        java.time.LocalDate day = asLocalDate(date);
        if (hours <= 12) {
            day = day.minusDays(1);
            hours += 12;
        } else {
            hours -= 12;
        }
        long year = day.getYear() + 8000L;
        long month = day.getMonthValue();
        long dayOfMonth = day.getDayOfMonth() - 1L;
        if (month < 3) {
            year--;
            month += 12;
        }
        long julian = year * 365 + year / 4 - year / 100 + year / 400 - 1200820;
        julian += (month * 153 + 3) / 5 - 92;
        julian += dayOfMonth;
        return julian + hours / 24.0 + minutes / 1440.0 + wholeSeconds / 86400.0;
    }

    private static final long NOON_GIVEN_TO_A_DATE_WITH_NO_CLOCK =
            12L * 3600 * NANOSECONDS_A_SECOND;

    private static long inUniversalTime(DateValue date) {
        return nanosecondsOf(date)
                - date.zoneMinutes().orElse(0) * 60L * NANOSECONDS_A_SECOND;
    }
}
