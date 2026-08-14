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

    /** Nothing to construct: the parts of a date are a question, not a thing. */
    private DateParts() {
    }

    static List<String> partNames() {
        return IN_ORDER;
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
