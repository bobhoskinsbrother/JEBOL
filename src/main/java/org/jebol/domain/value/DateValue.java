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
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException("day out of range: " + day);
        }
        if (zoneMinutes.isPresent() && timeOfDay.isEmpty()) {
            throw new IllegalArgumentException(
                    "a zone needs a time: a bare date names no instant to offset");
        }
    }

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

    @Override
    public String toString() {
        String rendered = day + "-" + MONTH_NAMES[month - 1] + "-" + year;
        return timeOfDay.map(time -> rendered + "/" + time).orElse(rendered);
    }
}
