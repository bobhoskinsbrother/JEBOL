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
}
