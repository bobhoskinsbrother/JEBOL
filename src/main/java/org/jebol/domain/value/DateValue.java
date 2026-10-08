package org.jebol.domain.value;

import org.jebol.domain.date.DateMaking;

import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

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

    @Override
    public Value arithmetic(Value right, ArithmeticOperation operation) {
        return new DateArithmetic(this).combinedWith(right, operation);
    }


    @Override
    public boolean equalTo(Value other, Sameness how) {
        return other instanceof DateValue theirs
                && moment().compareTo(theirs.moment()) == 0;
    }

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
        return TYPE;
    }

    public static final Datatype TYPE = new DateDatatype();

    private static final class DateDatatype extends Datatype {

        private static final long MICROSECONDS_A_SECOND = 1_000_000L;

        DateDatatype() {
            super("date", Typeset.SCALAR);
        }

        @Override
        public Value madeFrom(Value spec, Maker maker) {
            return switch (spec) {
                case AnyBlockValue parts -> DateMaking.fromParts(parts.remaining());
                case DateValue already -> DateMaking.fromParts(List.of(already));
                default -> super.madeFrom(spec, maker);
            };
        }

        @Override
        public Value constructedFrom(List<Value> contents, Construction construction) {
            Value specification = theSpecificationIn(contents);
            if (!(specification instanceof AnyBlockValue || specification instanceof DateValue)) {
                throw refusingConstruction(contents);
            }
            return construction.madeOf(this, specification);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case DateValue already -> already;
                case IntegerValue seconds ->
                        DateMaking.atTheTimestamp(seconds.magnitude() * MICROSECONDS_A_SECOND);
                case DecimalValue seconds -> DateMaking.atTheTimestamp(
                        (long) (seconds.quantity() * MICROSECONDS_A_SECOND));
                case AnyBlockValue parts -> DateMaking.fromParts(parts.remaining());
                case AnyStringValue written -> dateReadFrom(written, maker);
                default -> throw refusing(from);
            };
        }

        private Value dateReadFrom(AnyStringValue written, Maker maker) {
            return maker.valuesReadFrom(written.text())
                    .filter(read -> read.size() == 1 && read.getFirst() instanceof DateValue)
                    .map(List::getFirst)
                    .orElseThrow(() -> refusing(written));
        }
    }

    @Override
    public Optional<Value> asDecimal(AnyDecimalValue.AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.of(inHundredths(wanted, secondsSinceTheEpoch()));
    }

    public double secondsSinceTheEpoch() {
        Moment instant = moment();
        return (double) instant.dayNumber() * SECONDS_A_DAY
                + (double) instant.nanosecondsIntoTheDay() / NANOSECONDS_A_SECOND;
    }

    public long wholeSecondsSinceTheEpoch() {
        return Math.round(secondsSinceTheEpoch());
    }

    public long dayNumber() {
        return java.time.LocalDate.of(year, month, day).toEpochDay();
    }

    public TimeValue spanTo(DateValue other) {
        return TimeValue.ofNanoseconds(
                (dayNumber() - other.dayNumber()) * NANOSECONDS_A_DAY);
    }

    public java.time.LocalDate asLocalDate() {
        return java.time.LocalDate.of(year, month, day);
    }

    private static final int MONTHS_A_YEAR = 12;
    private static final int LONGEST_MONTH = 31;
    private static final int WHERE_THE_YEAR_IS_PACKED = 48;
    private static final int WHERE_THE_DAY_OF_THE_YEAR_IS_PACKED = 32;

    @Override
    public Value randomised(RandomDraw draw) {
        java.time.LocalDate drawn = java.time.LocalDate
                .of((int) draw.upTo(year), 1, 1)
                .plusMonths(draw.upTo(MONTHS_A_YEAR))
                .plusDays(draw.upTo(LONGEST_MONTH));
        return timeOfDay.isEmpty()
                ? DateValue.of(drawn.getYear(), drawn.getMonthValue(), drawn.getDayOfMonth())
                : new DateValue(drawn.getYear(), drawn.getMonthValue(), drawn.getDayOfMonth(),
                        Optional.of(TimeValue.ofNanoseconds(
                                draw.upTo(TimeValue.NANOSECONDS_PER_DAY))),
                        zoneMinutes);
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return ((long) year << WHERE_THE_YEAR_IS_PACKED)
                + ((long) asLocalDate().getDayOfYear() << WHERE_THE_DAY_OF_THE_YEAR_IS_PACKED)
                + nanosecondsOnTheClock();
    }

    public TimeValue clock() {
        return timeOfDay.orElseGet(() -> TimeValue.ofNanoseconds(0));
    }

    public long nanosecondsOnTheClock() {
        return timeOfDay.map(TimeValue::nanoseconds).orElse(0L);
    }

    public long nanosecondsInUniversalTime() {
        return nanosecondsOnTheClock()
                - zoneMinutes.orElse(0) * NANOSECONDS_A_MINUTE;
    }

    public int hourOfTheDay() {
        return (int) (nanosecondsOnTheClock() / NANOSECONDS_A_SECOND / 3600);
    }

    public int minuteOfTheHour() {
        return (int) (nanosecondsOnTheClock() / NANOSECONDS_A_SECOND / 60 % 60);
    }

    public Value secondOfTheMinute() {
        long onTheClock = nanosecondsOnTheClock();
        long whole = onTheClock / NANOSECONDS_A_SECOND % 60;
        long fraction = onTheClock % NANOSECONDS_A_SECOND;
        return fraction == 0
                ? IntegerValue.of(whole)
                : DecimalValue.of(whole + (double) fraction / NANOSECONDS_A_SECOND);
    }

    public TimeValue zoneAsATime() {
        return TimeValue.ofNanoseconds(
                zoneMinutes.orElse(0) * NANOSECONDS_A_MINUTE);
    }

    public DateValue atMidnightIfItHasNoClock() {
        return timeOfDay.isPresent()
                ? this
                : new DateValue(year, month, day,
                        Optional.of(TimeValue.ofNanoseconds(0)), Optional.empty());
    }

    public DateValue atTheTime(TimeValue given) {
        return new DateValue(year, month, day, Optional.of(given), zoneMinutes);
    }

    public DateValue onDay(java.time.LocalDate given) {
        return new DateValue(given.getYear(), given.getMonthValue(),
                given.getDayOfMonth(), timeOfDay, zoneMinutes);
    }

    public DateValue onTheDay(int wantedYear, int wantedMonth, int wantedDay) {
        return onDay(java.time.LocalDate.of(wantedYear, 1, 1)
                .plusMonths(wantedMonth - 1L)
                .plusDays(wantedDay - 1L));
    }

    public DateValue onTheDayOfTheYear(int dayOfTheYear) {
        return onDay(java.time.LocalDate.of(year, 1, 1).plusDays(dayOfTheYear - 1L));
    }

    public DateValue onTheDayOf(DateValue other) {
        return new DateValue(other.year, other.month, other.day,
                timeOfDay, other.zoneMinutes);
    }

    public DateValue asJustTheDay() {
        return DateValue.of(year, month, day);
    }

    public DateValue withTheZoneDropped() {
        return new DateValue(year, month, day, Optional.of(clock()), Optional.empty());
    }

    public DateValue withTheZoneForgotten() {
        return new DateValue(year, month, day, timeOfDay, Optional.empty());
    }

    public DateValue withTheSameClockIn(int offsetMinutes) {
        return new DateValue(year, month, day,
                Optional.of(clock()), Optional.of(offsetMinutes));
    }

    public DateValue atTheSameInstantIn(int offsetMinutes) {
        DateValue standing = withTheSameClockIn(zoneMinutes.orElse(0));
        long sinceMidnight = standing.clock().nanoseconds()
                + (offsetMinutes - standing.zoneMinutes.orElse(0)) * NANOSECONDS_A_MINUTE;
        java.time.LocalDate landed = standing.asLocalDate()
                .plusDays(Math.floorDiv(sinceMidnight, NANOSECONDS_A_DAY));
        return new DateValue(landed.getYear(), landed.getMonthValue(),
                landed.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(
                        Math.floorMod(sinceMidnight, NANOSECONDS_A_DAY))),
                Optional.of(offsetMinutes));
    }

    private static final long NANOSECONDS_A_SECOND = 1_000_000_000L;
    private static final long SECONDS_A_DAY = 24L * 60L * 60L;

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
     * The same instant with the offset resolved rather than remembered, which
     * is the form Rebol keeps a date in.
     *
     * <p>{@code Adjust_Date_Zone}, whose own comment says the adjusted form
     * "should be used for output, not stored" -- Rebol stores UTC and puts the
     * offset back on to mold or to answer a path. JEBOL keeps the day and the
     * clock as they were written, so anything wanting the instant asks for it
     * here.
     *
     * <p>The offset moves the day as well as the clock: half past midnight an
     * hour ahead is half past eleven the evening before. A date carrying no
     * clock has no instant to move and answers itself.
     */
    public DateValue asStoredInUtc() {
        int offsetMinutes = zoneMinutes.orElse(0);
        if (timeOfDay.isEmpty()) {
            return this;
        }
        if (offsetMinutes == 0) {
            return new DateValue(year, month, day, timeOfDay, Optional.of(0));
        }
        java.time.LocalDateTime moved = java.time.LocalDate.of(year, month, day)
                .atStartOfDay()
                .plusNanos(timeOfDay.map(TimeValue::nanoseconds).orElse(0L))
                .minusMinutes(offsetMinutes);
        return new DateValue(moved.getYear(), moved.getMonthValue(), moved.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(moved.toLocalTime().toNanoOfDay())),
                Optional.of(0));
    }

    private String writtenYearPaddedToFourSoItReadsBack() {
        return year < 0 || year >= 1000 ? String.valueOf(year) : "%04d".formatted(year);
    }

    @Override
    public String toString() {
        String rendered = day + "-" + MONTH_NAMES[month - 1] + "-"
                + writtenYearPaddedToFourSoItReadsBack();
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
