package org.jebol.domain.value;

public record TimeValue(long nanoseconds) implements Value {

    public static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MINUTES_PER_HOUR = 60L;

    public static final long NANOSECONDS_PER_HOUR =
            MINUTES_PER_HOUR * SECONDS_PER_MINUTE * NANOSECONDS_PER_SECOND;

    private static final long HOURS_PER_DAY = 24L;

    public static final long NANOSECONDS_PER_DAY = HOURS_PER_DAY * NANOSECONDS_PER_HOUR;

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
    public java.util.Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return java.util.Optional.of(inHundredths(
                wanted, (double) nanoseconds / NANOSECONDS_PER_SECOND));
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
