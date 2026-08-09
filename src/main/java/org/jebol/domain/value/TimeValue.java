package org.jebol.domain.value;

/**
 * A span of time, written {@code 10:30} or {@code 2:25:24}.
 *
 * <p>Signed, and free to exceed twenty-four hours: REBOL's {@code time!} is a
 * duration as much as a clock reading, so {@code 30:00} is a legal fifty-hour
 * span rather than an error.
 */
public record TimeValue(long nanoseconds) implements Value {

    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MINUTES_PER_HOUR = 60L;

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
        return (isNegative() ? "-" : "") + hours() + ":" + String.format("%02d", minutes())
                + (seconds() != 0 || subsecondNanoseconds() != 0
                        ? ":" + String.format("%02d", seconds())
                        : "");
    }
}
