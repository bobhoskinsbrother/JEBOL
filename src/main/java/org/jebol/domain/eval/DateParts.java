package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;

/**
 * The parts a date answers to, read out of {@code PD_Date} in {@code t-date.c}.
 *
 * <p>Fourteen names, and a number names one of them by position. The list is
 * the C's own word order, which is why the positions are what they are:
 * {@code sym = SYM_YEAR + Int32(arg) - 1}, checked against
 * {@code SYM_YEAR .. SYM_JULIAN}.
 *
 * <p>Three of the answers are not the datatype a reader would assume. SECOND is
 * a whole number until there is a fraction, and then it is a decimal. JULIAN is
 * always a decimal, and it counts from noon rather than from midnight. And every
 * clock part of a date that carries no time is none rather than zero, because a
 * day names no instant to read a clock off.
 */
final class DateParts {

    /**
     * The C's word order, and the reason a number can name a part at all.
     *
     * <p>{@code week} and {@code isoweek} sit between YEARDAY and TIMEZONE in
     * {@code words.reb} and are commented out there, so they take no positions
     * and the numbering runs straight past them.
     */
    private static final List<String> IN_ORDER = List.of(
            "year", "month", "day", "time", "date", "zone", "hour", "minute",
            "second", "weekday", "yearday", "timezone", "utc", "julian");

    private static final long NANOSECONDS_A_DAY = 24L * 60L * 60L * 1_000_000_000L;

    /** Nothing to construct: the parts of a date are a question, not a thing. */
    private DateParts() {
    }

    static List<String> partNames() {
        return IN_ORDER;
    }

    /**
     * A date with one of its parts written, which is a new date rather than a
     * change to this one.
     *
     * <p>A date is a value and not a series, so {@code d/zone: 2} replaces
     * what the word holds. That is why this answers a date instead of taking
     * one apart in place.
     *
     * <p>ZONE and TIMEZONE both name the offset and mean opposite things.
     * ZONE keeps the clock and changes what it is an offset from, so
     * {@code 1-Jan-2000} becomes {@code 1-Jan-2000/0:00+2:00} -- midnight, in
     * a place two hours ahead. TIMEZONE keeps the instant and moves the clock
     * to suit, so the same date read in a place four hours ahead is
     * {@code 1-Jan-2000/2:00+4:00}. TIMEZONE moves the clock by the difference
     * between the offsets, so the two agree only where that difference is
     * nothing -- setting the offset a date already has. On a date with no
     * offset they still differ, because going from none to two hours is a
     * change of two.
     *
     * <p>A part that exists but cannot be written is {@code bad-field-set},
     * and a word that is no part at all is {@code invalid-path}. Two errors
     * because they are two different mistakes -- one is asking for something
     * impossible, the other is a typo.
     */
    static DateValue written(DateValue date, Value selector, Value given) {
        if (!(selector instanceof WordValue named)
                || !IN_ORDER.contains(named.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    selector instanceof WordValue word ? word.spelling() : "date");
        }
        return switch (named.canonical()) {
            case "zone" -> withTheSameClockIn(withAClockIfItHadNone(date),
                    offsetAskedFor(given));
            case "timezone" -> atTheSameInstantIn(withAClockIfItHadNone(date),
                    offsetAskedFor(given));
            case "year" -> onTheDay(date, wholeNumberIn(given), date.month(), date.day());
            case "month" -> onTheDay(date, date.year(), wholeNumberIn(given), date.day());
            case "day" -> onTheDay(date, date.year(), date.month(), wholeNumberIn(given));
            case "hour" -> atTheTime(date, withTheHour(clockOf(date), wholeNumberIn(given)));
            case "minute" ->
                    atTheTime(date, withTheMinute(clockOf(date), wholeNumberIn(given)));
            case "second" -> atTheTime(date, withTheSecond(clockOf(date), given));
            case "time" -> atTheTimeGiven(date, given);
            case "date" -> theDayOf(given, date);
            case "utc" -> asTheSameInstant(given);
            case "yearday" -> theYearAndDayOf(date, wholeNumberIn(given));
            default -> throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                    named.spelling());
        };
    }

    /**
     * Midnight, for a date that had no time and is about to be given one.
     *
     * <p>{@code if (secs == NO_TIME && ((sym >= SYM_HOUR && sym <= SYM_SECOND)
     * || sym == SYM_TIME || sym == SYM_ZONE)) { time.h = 0; ... }} -- the C
     * starts the clock rather than refusing, so {@code d/hour: 2} on a bare
     * date makes it two in the morning.
     */
    private static DateValue withAClockIfItHadNone(DateValue date) {
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

    /**
     * The same date with one of its three numbers replaced.
     *
     * <p>A month or a day outside its range rolls into the next one rather
     * than failing, which is {@code Normalize_Time} and {@code Date_Of_Days}
     * running over the numbers the C has just written. So {@code d/month: 13}
     * is January of the year after.
     */
    private static DateValue onTheDay(DateValue was, int year, int month, int day) {
        return sameClockOn(was, java.time.LocalDate.of(year, 1, 1)
                .plusMonths(month - 1L)
                .plusDays(day - 1L));
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

    /**
     * TIME written, which none clears rather than sets.
     *
     * <p>{@code if (IS_NONE(val)) { secs = NO_TIME; tz = 0; }} -- so
     * {@code d/time: none} takes the zone away with it, a date without a
     * clock naming no instant to offset.
     */
    private static DateValue atTheTimeGiven(DateValue was, Value given) {
        return switch (given) {
            case NoneValue nothing -> DateValue.of(was.year(), was.month(), was.day());
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

    /** DATE written, which is the day from another date and the clock kept. */
    private static DateValue theDayOf(Value given, DateValue was) {
        if (!(given instanceof DateValue other)) {
            throw Raised.of(EvaluationFailure.BAD_FIELD_SET, given);
        }
        return new DateValue(other.year(), other.month(), other.day(),
                was.timeOfDay(), was.zoneMinutes());
    }

    /** UTC written, which takes the whole date and calls its zone nothing. */
    private static DateValue asTheSameInstant(Value given) {
        if (!(given instanceof DateValue other)) {
            throw Raised.of(EvaluationFailure.BAD_FIELD_SET, given);
        }
        return new DateValue(other.year(), other.month(), other.day(),
                other.timeOfDay(),
                other.timeOfDay().isPresent()
                        ? java.util.Optional.of(0)
                        : java.util.Optional.empty());
    }

    /** YEARDAY written: that many days into the year the date is already in. */
    private static DateValue theYearAndDayOf(DateValue was, int dayOfYear) {
        return sameClockOn(was, java.time.LocalDate.of(was.year(), 1, 1)
                .plusDays(dayOfYear - 1L));
    }

    /**
     * An offset in minutes, from the hours or the time a caller named.
     *
     * <p>{@code d/zone: 2} is two hours and {@code d/zone: 2:30} is two and a
     * half, because a number naming an offset has always meant hours.
     */
    private static int offsetAskedFor(Value given) {
        if (given instanceof IntegerValue hours) {
            return withinReach(Math.toIntExact(hours.magnitude()) * 60);
        }
        if (given instanceof DecimalValue hours) {
            return withinReach((int) hours.quantity() * 60);
        }
        if (given instanceof TimeValue clock) {
            return withinReach(
                    (int) (clock.nanoseconds() / (60L * NANOSECONDS_A_SECOND)));
        }
        throw Raised.of(EvaluationFailure.BAD_FIELD_SET, Molder.mold(given));
    }

    /**
     * An offset a date can hold, or {@code out-of-range}.
     *
     * <p>Fifteen hours and three quarters either way, which is what seven
     * signed bits of quarter-hours reach and is the same ceiling the lexer
     * applies to a written one. So {@code d/timezone: 16} is refused rather
     * than wrapping round to somewhere on the other side of the world.
     */
    private static int withinReach(int offsetMinutes) {
        if (Math.abs(offsetMinutes) > MOST_A_ZONE_MAY_BE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    IntegerValue.of(offsetMinutes));
        }
        return offsetMinutes;
    }

    private static final int MOST_A_ZONE_MAY_BE = 15 * 60 + 45;

    /** The clock as written, said to belong to another place. */
    private static DateValue withTheSameClockIn(DateValue date, int offsetMinutes) {
        return new DateValue(date.year(), date.month(), date.day(),
                Optional.of(date.timeOfDay().orElseGet(() -> TimeValue.ofNanoseconds(0))),
                Optional.of(offsetMinutes));
    }

    /** The same instant, with the clock moved to read correctly there. */
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



    /**
     * What a date answers for one part, or none for a part it has not got.
     *
     * <p>{@code return (val) ? PE_BAD_SELECT : PE_NONE;} -- a read answers none
     * and only a write refuses, so asking a date for a part it may not have is
     * an ordinary question.
     */
    static Value of(DateValue date, Value selector) {
        String part = switch (selector) {
            case WordValue named -> named.canonical();
            case IntegerValue position -> position.magnitude() >= 1
                    && position.magnitude() <= IN_ORDER.size()
                    ? IN_ORDER.get((int) position.magnitude() - 1)
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
            case "second" -> secondOf(date);
            case "weekday" -> IntegerValue.of(asLocalDate(date).getDayOfWeek().getValue());
            case "yearday" -> IntegerValue.of(asLocalDate(date).getDayOfYear());
            case "utc" -> atZoneZero(date);
            case "julian" -> DecimalValue.of(julianDayOf(date));
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

    /**
     * The second, as a whole number or as a decimal where there is a fraction.
     *
     * <p>{@code if (time.n == 0) num = time.s; else { SET_DECIMAL(val,
     * (REBDEC)time.s + (time.n * NANO)); ... }}. The datatype of the answer
     * depends on the value, so code comparing it against a whole number is
     * right until the first fractional second reaches it.
     */
    private static Value secondOf(DateValue date) {
        long nanoseconds = nanosecondsOf(date);
        long whole = nanoseconds / NANOSECONDS_A_SECOND % 60;
        long fraction = nanoseconds % NANOSECONDS_A_SECOND;
        return fraction == 0
                ? IntegerValue.of(whole)
                : DecimalValue.of(whole + (double) fraction / NANOSECONDS_A_SECOND);
    }

    /**
     * The same instant with the offset dropped: {@code VAL_ZONE(val) = 0;}.
     *
     * <p>The instant and not the wall time, so a date two hours ahead answers a
     * time two hours earlier. The two agree only where the offset is zero.
     */
    private static Value atZoneZero(DateValue date) {
        int offsetMinutes = date.zoneMinutes().orElse(0);
        if (date.timeOfDay().isEmpty() || offsetMinutes == 0) {
            return new DateValue(date.year(), date.month(), date.day(),
                    date.timeOfDay(),
                    date.timeOfDay().isEmpty() ? Optional.empty() : Optional.of(0));
        }
        java.time.LocalDateTime moved = asLocalDate(date)
                .atStartOfDay()
                .plusNanos(nanosecondsOf(date))
                .minusMinutes(offsetMinutes);
        return new DateValue(moved.getYear(), moved.getMonthValue(), moved.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(moved.toLocalTime().toNanoOfDay())),
                Optional.of(0));
    }

    /**
     * The Julian day, counted from noon.
     *
     * <p>{@code Gregorian_To_Julian_Date}, arithmetic and all. Two parts of it
     * are worth naming because nothing about the name suggests them. A date
     * carrying no time is given twelve hours before the conversion starts --
     * {@code if (secs == NO_TIME) { time.h = 12; // Julian date is counted from
     * noon }} -- and the conversion then adds twelve again, so a bare day comes
     * out a whole number. And where there is a time it is converted to universal
     * time first, so the answer moves with the offset while the date part of it
     * does not.
     */
    private static double julianDayOf(DateValue date) {
        long nanoseconds = date.timeOfDay().isEmpty()
                ? 12L * 3600 * NANOSECONDS_A_SECOND
                : nanosecondsOf(date) - date.zoneMinutes().orElse(0) * 60L * NANOSECONDS_A_SECOND;
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
}
