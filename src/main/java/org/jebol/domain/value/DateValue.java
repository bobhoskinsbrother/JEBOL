package org.jebol.domain.value;

import java.util.Optional;

/**
 * A date, optionally with a time and a zone, written {@code 15-May-2000} or
 * {@code 4/july/1996}.
 *
 * <p>A zone only exists alongside a time, because a bare date names no instant
 * to offset. That is enforced here rather than left to callers.
 */
public record DateValue(
        int year,
        int month,
        int day,
        Optional<TimeValue> timeOfDay,
        Optional<Integer> zoneMinutes) implements Value {

    private static final String[] MONTH_NAMES = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    public DateValue {
        if (timeOfDay == null || zoneMinutes == null) {
            throw new IllegalArgumentException("optional fields are empty, never null");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month out of range: " + month);
        }
        if (day < 1 || day > daysIn(month, year)) {
            throw new IllegalArgumentException("day out of range: " + day);
        }
        if (zoneMinutes.isPresent() && timeOfDay.isEmpty()) {
            throw new IllegalArgumentException(
                    "a zone needs a time: a bare date names no instant to offset");
        }
    }

    /**
     * How long a month is, which February makes a question about the year.
     *
     * <p>{@code Month_Length} in {@code t-date.c}, leap rule included, and it
     * is what makes {@code 29-Feb-2001} an {@code invalid} rather than a date.
     * Checking the day against a flat 31 accepted it.
     */
    private static int daysIn(int month, int year) {
        if (month != 2) {
            return LENGTH_OF_MONTH[month - 1];
        }
        boolean leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
        return leap ? 29 : 28;
    }

    private static final int[] LENGTH_OF_MONTH =
            {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    public static DateValue of(int year, int month, int day) {
        return new DateValue(year, month, day, Optional.empty(), Optional.empty());
    }

    public static DateValue of(int year, int month, int day, TimeValue timeOfDay) {
        return new DateValue(year, month, day, Optional.of(timeOfDay), Optional.empty());
    }

    @Override
    public Datatype datatype() {
        return Datatype.DATE;
    }

    /**
     * Where a date sits on the line of instants: which day, and how far into
     * it. Two longs rather than one, because a nanosecond count that reached
     * Rebol's last year would need more room than a long has.
     */
    public record Moment(long dayNumber, long nanosecondsIntoTheDay)
            implements Comparable<Moment> {

        @Override
        public int compareTo(Moment other) {
            int acrossTheDays = Long.compare(dayNumber, other.dayNumber);
            return acrossTheDays != 0
                    ? acrossTheDays
                    : Long.compare(nanosecondsIntoTheDay, other.nanosecondsIntoTheDay);
        }
    }

    /**
     * The instant this date names, zone taken off.
     *
     * <p>A zone says how far ahead of UTC the written time is, so reaching the
     * instant means taking it off again: {@code 12:58:32+2:00} is
     * {@code 10:58:32} where the count starts, and taking two hours off
     * {@code 1:00} moves the day as well as the clock. A date carrying no time
     * is its midnight, so it lands on a whole day.
     *
     * <p>This is what orders one date against another, and it is the same
     * comparison Rebol makes from the other end. Rebol stores a date already
     * in UTC and remembers the zone only to write it back out, so its own
     * {@code Cmp_Date} compares the stored times as they are. JEBOL keeps the
     * time as it was written, so the zone comes off here instead.
     */
    public Moment moment() {
        long sinceMidnight = timeOfDay.map(TimeValue::nanoseconds).orElse(0L)
                - zoneMinutes.orElse(0) * NANOSECONDS_A_MINUTE;
        return new Moment(
                java.time.LocalDate.of(year, month, day).toEpochDay()
                        + Math.floorDiv(sinceMidnight, NANOSECONDS_A_DAY),
                Math.floorMod(sinceMidnight, NANOSECONDS_A_DAY));
    }

    private static final long NANOSECONDS_A_MINUTE = 60L * 1_000_000_000L;
    private static final long NANOSECONDS_A_DAY = 24L * 60L * NANOSECONDS_A_MINUTE;

    /**
     * The year as REBOL writes it, which is four digits wide below 1000.
     *
     * <p>{@code 1-Feb-0003} rather than {@code 1-Feb-3}. The padding is not
     * decoration: a molded date has to read back as the same date, and
     * {@code 1-Feb-3} reads back as 2003 because a year of one or two digits
     * is the shorthand form.
     */
    private String writtenYear() {
        return year < 0 || year >= 1000 ? String.valueOf(year) : "%04d".formatted(year);
    }

    /**
     * The written form: the day, then the time, then the offset.
     *
     * <p>An offset of zero is written as nothing at all, which is what a real
     * R3 does: {@code 1-Jan-2000/12:00+0:00} molds as {@code 1-Jan-2000/12:00}.
     * So the written form does not distinguish an offset of zero from a date
     * that never carried one, and reading either back gives a zone of 0:00.
     */
    @Override
    public String toString() {
        String rendered = day + "-" + MONTH_NAMES[month - 1] + "-" + writtenYear();
        if (timeOfDay.isEmpty()) {
            return rendered;
        }
        return rendered + "/" + timeOfDay.get() + writtenOffset();
    }

    private String writtenOffset() {
        int minutes = zoneMinutes.orElse(0);
        if (minutes == 0) {
            return "";
        }
        int size = Math.abs(minutes);
        return "%s%d:%02d".formatted(minutes < 0 ? "-" : "+", size / 60, size % 60);
    }

    /**
     * The written form MOLD/ALL asks for, which is ISO 8601.
     *
     * <p>{@code Emit_Date} writes this whenever {@code MOPT_MOLD_ALL} is set,
     * and it is a different shape rather than a decoration: the year comes
     * first, the parts are all padded, and a T stands where the slash does.
     * A zone that {@code 1-Feb-2000/10:30+2:00} writes as {@code +2:00} is
     * {@code +02:00} here.
     *
     * <p>The seconds are always written even when they are nothing, and a
     * fraction has its trailing zeros trimmed -- {@code Trim_Tail(series,
     * '0')} after the nine digits it pads to.
     */
    public String isoForm() {
        String calendar = "%04d-%02d-%02d".formatted(year, month, day);
        if (timeOfDay.isEmpty()) {
            return calendar;
        }
        return calendar + "T" + isoClock() + isoOffset();
    }

    private String isoClock() {
        long nanoseconds = timeOfDay.orElseThrow().nanoseconds();
        long seconds = nanoseconds / A_SECOND;
        String written = "%02d:%02d:%02d".formatted(
                seconds / 3600, seconds / 60 % 60, seconds % 60);
        long fraction = nanoseconds % A_SECOND;
        if (fraction == 0) {
            return written;
        }
        String digits = "%09d".formatted(fraction).replaceAll("0+$", "");
        return written + "." + digits;
    }

    /**
     * The offset, or nothing at all where it is nothing.
     *
     * <p>The same rule the ordinary written form follows, and for the same
     * reason: an offset of zero and no offset at all are one thing, so
     * writing {@code +00:00} would claim a distinction the value does not
     * carry.
     */
    private String isoOffset() {
        int minutes = zoneMinutes.orElse(0);
        if (minutes == 0) {
            return "";
        }
        int size = Math.abs(minutes);
        return "%s%02d:%02d".formatted(minutes < 0 ? "-" : "+", size / 60, size % 60);
    }

    private static final long A_SECOND = 1_000_000_000L;
}
