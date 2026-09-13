package org.jebol.domain.value;

/**
 * A span of time, written {@code 10:30} or {@code 2:25:24}.
 *
 * <p>Signed, and free to exceed twenty-four hours: REBOL's {@code time!} is a
 * duration as much as a clock reading, so {@code 30:00} is a legal fifty-hour
 * span rather than an error.
 */
public record TimeValue(long nanoseconds) implements Value {

    /** Nanoseconds in one second, which is the unit a time! counts in. */
    public static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MINUTES_PER_HOUR = 60L;

    /** Nanoseconds in one hour, the unit money-per-time arithmetic bills in. */
    public static final long NANOSECONDS_PER_HOUR =
            MINUTES_PER_HOUR * SECONDS_PER_MINUTE * NANOSECONDS_PER_SECOND;

    private static final long HOURS_PER_DAY = 24L;

    /** Nanoseconds in one day, the unit a date! shifts by. */
    public static final long NANOSECONDS_PER_DAY = HOURS_PER_DAY * NANOSECONDS_PER_HOUR;

    /**
     * The longest span a time! holds, which is a whole number of hours
     * rather than a whole number of nanoseconds: REBOL molds a time as
     * hours, minutes and seconds, so the limit is the last hour boundary
     * that fits in a signed sixty-four bit nanosecond count.
     */
    public static final long LONGEST =
            (Long.MAX_VALUE / NANOSECONDS_PER_HOUR) * NANOSECONDS_PER_HOUR;

    public static TimeValue ofNanoseconds(long nanoseconds) {
        return new TimeValue(nanoseconds);
    }

    public static TimeValue of(long hours, long minutes, long seconds, long nanoseconds) {
        long total = ((hours * MINUTES_PER_HOUR + minutes) * SECONDS_PER_MINUTE + seconds)
                * NANOSECONDS_PER_SECOND + nanoseconds;
        return new TimeValue(total);
    }

    public boolean isNegative() {
        return nanoseconds < 0;
    }

    public long hours() {
        return Math.abs(nanoseconds) / NANOSECONDS_PER_SECOND
                / SECONDS_PER_MINUTE / MINUTES_PER_HOUR;
    }

    public long minutes() {
        return Math.abs(nanoseconds) / NANOSECONDS_PER_SECOND
                / SECONDS_PER_MINUTE % MINUTES_PER_HOUR;
    }

    public long seconds() {
        return Math.abs(nanoseconds) / NANOSECONDS_PER_SECOND % SECONDS_PER_MINUTE;
    }

    public long subsecondNanoseconds() {
        return Math.abs(nanoseconds) % NANOSECONDS_PER_SECOND;
    }

    @Override
    public Datatype datatype() {
        return Datatype.TIME;
    }

    @Override
    public String toString() {
        String written = (isNegative() ? "-" : "")
                + hours() + ":" + String.format("%02d", minutes());
        if (seconds() == 0 && subsecondNanoseconds() == 0) {
            return written;
        }
        return written + ":" + String.format("%02d", seconds()) + writtenFraction();
    }

    private String writtenFraction() {
        long fraction = subsecondNanoseconds();
        if (fraction == 0) {
            return "";
        }
        String digits = String.format("%09d", fraction);
        return "." + digits.replaceFirst("0+$", "");
    }
}
