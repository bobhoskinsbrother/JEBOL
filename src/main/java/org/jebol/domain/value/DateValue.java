package org.jebol.domain.value;

import org.jebol.domain.date.DateFactory;
import org.jebol.domain.date.part.DatePart;

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
        Optional<Integer> zoneMinutes) implements Value, PathTarget {

    @Override
    public Value stepIntoBy(Value selector) {
        return DatePart.readFrom(this, selector);
    }

    @Override
    public void writeThrough(Slot place, Value selector, Value written) {
        place.setValue(DatePart.writeOn(this, selector, written));
    }

    @Override
    public Value calculate(Value right, ArithmeticOperation operation) {
        return new DateArithmetic(this).combineWith(right, operation);
    }


    @Override
    public boolean isEqualTo(Value other, Sameness how) {
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
        if (day < 1 || day > countDaysIn(month, year)) {
            throw new IllegalArgumentException("day out of range: " + day);
        }
        if (zoneMinutes.isPresent() && timeOfDay.isEmpty()) {
            throw new IllegalArgumentException(
                    "a zone needs a time: a bare date names no instant to offset");
        }
    }

    private static int countDaysIn(int month, int year) {
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

    public static final int EARLIEST_YEAR = 0;
    public static final int LATEST_YEAR = 0x3FFF;

    private static final class DateDatatype extends ScalarDatatype {

        private static final long MICROSECONDS_SECOND = 1_000_000L;

        private static final int MOST_DATE_CHARACTERS = 45;

        DateDatatype() {
            super("date");
        }

        @Override
        public Value makeFrom(Value spec, Maker maker) {
            return switch (spec) {
                case AnyBlockValue parts -> DateFactory.fromParts(parts.remaining());
                case DateValue already -> DateFactory.fromParts(List.of(already));
                default -> super.makeFrom(spec, maker);
            };
        }

        @Override
        public Value construct(List<Value> contents, Construction construction) {
            Value specification = readSpecificationIn(contents);
            if (!(specification instanceof AnyBlockValue || specification instanceof DateValue)) {
                throw refuseConstruction(contents);
            }
            return construction.make(this, specification);
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case DateValue already -> already;
                case IntegerValue seconds ->
                        DateFactory.fromWholeSeconds(seconds.magnitude());
                case DecimalValue seconds -> DateFactory.fromTimestamp(
                        (long) (seconds.quantity() * MICROSECONDS_SECOND));
                case AnyBlockValue parts -> DateFactory.fromParts(parts.remaining());
                case AnyStringValue written -> readDateFrom(written, maker);
                default -> throw refuse(from);
            };
        }

        private Value readDateFrom(AnyStringValue written, Maker maker) {
            String token = new WrittenText(written.text())
                    .findOneToken(MOST_DATE_CHARACTERS);
            return maker.readTheValuesFrom(token)
                    .filter(read -> read.size() == 1 && read.getFirst() instanceof DateValue)
                    .map(List::getFirst)
                    .orElseThrow(() -> refuse(written));
        }
    }

    @Override
    public Optional<Value> asDecimal(AnyDecimalValue.AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.of(countInHundredths(wanted, countSecondsSinceEpoch()));
    }

    public double countSecondsSinceEpoch() {
        Moment instant = moment();
        return (double) instant.dayNumber() * SECONDS_DAY
                + (double) instant.nanosecondsIntoTheDay() / NANOSECONDS_SECOND;
    }

    public long wholeSecondsSinceEpoch() {
        return Math.round(countSecondsSinceEpoch());
    }

    public long dayNumber() {
        return java.time.LocalDate.of(year, month, day).toEpochDay();
    }

    public TimeValue measureSpanTo(DateValue other) {
        Moment here = moment();
        Moment there = other.moment();
        return TimeValue.ofNanoseconds(
                (here.dayNumber() - there.dayNumber()) * NANOSECONDS_DAY
                        + here.nanosecondsIntoTheDay() - there.nanosecondsIntoTheDay());
    }

    public java.time.LocalDate asLocalDate() {
        return java.time.LocalDate.of(year, month, day);
    }

    private static final int MONTHS_YEAR = 12;
    private static final int LONGEST_MONTH = 31;
    private static final int WHERE_YEAR_IS_PACKED = 48;
    private static final int DAY_OF_YEAR_SHIFT = 32;

    @Override
    public Value randomise(RandomDraw draw) {
        java.time.LocalDate drawn = java.time.LocalDate
                .of((int) draw.upTo(year), 1, 1)
                .plusMonths(draw.upTo(MONTHS_YEAR))
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
        return ((long) year << WHERE_YEAR_IS_PACKED)
                + ((long) asLocalDate().getDayOfYear() << DAY_OF_YEAR_SHIFT)
                + nanosecondsOnClock();
    }

    public TimeValue clock() {
        return timeOfDay.orElseGet(() -> TimeValue.ofNanoseconds(0));
    }

    public long nanosecondsOnClock() {
        return timeOfDay.map(TimeValue::nanoseconds).orElse(0L);
    }

    public long nanosecondsInUniversalTime() {
        return nanosecondsOnClock()
                - zoneMinutes.orElse(0) * NANOSECONDS_MINUTE;
    }

    public int hourOnClock() {
        return (int) (nanosecondsOnClock() / NANOSECONDS_SECOND / 3600);
    }

    public int minuteOnClock() {
        return (int) (nanosecondsOnClock() / NANOSECONDS_SECOND / 60 % 60);
    }

    public Value secondOnClock() {
        long onTheClock = nanosecondsOnClock();
        long whole = onTheClock / NANOSECONDS_SECOND % 60;
        long fraction = onTheClock % NANOSECONDS_SECOND;
        return fraction == 0
                ? IntegerValue.of(whole)
                : DecimalValue.of(whole + (double) fraction / NANOSECONDS_SECOND);
    }

    public TimeValue zoneAsTime() {
        return TimeValue.ofNanoseconds(
                zoneMinutes.orElse(0) * NANOSECONDS_MINUTE);
    }

    public DateValue atMidnightIfItHasNoClock() {
        return timeOfDay.isPresent()
                ? this
                : new DateValue(year, month, day,
                        Optional.of(TimeValue.ofNanoseconds(0)), Optional.empty());
    }

    public DateValue atTime(TimeValue given) {
        return new DateValue(year, month, day, Optional.of(given), zoneMinutes);
    }

    public DateValue onDay(java.time.LocalDate given) {
        return new DateValue(given.getYear(), given.getMonthValue(),
                given.getDayOfMonth(), timeOfDay, zoneMinutes);
    }

    public DateValue onDay(int wantedYear, int wantedMonth, int wantedDay) {
        return onDay(java.time.LocalDate.of(wantedYear, 1, 1)
                .plusMonths(wantedMonth - 1L)
                .plusDays(wantedDay - 1L));
    }

    public DateValue onDayOfYear(int dayOfTheYear) {
        return onDay(java.time.LocalDate.of(year, 1, 1).plusDays(dayOfTheYear - 1L));
    }

    public DateValue onDayFrom(DateValue other) {
        return new DateValue(other.year, other.month, other.day,
                timeOfDay, other.zoneMinutes);
    }

    public DateValue asJustDay() {
        return DateValue.of(year, month, day);
    }

    public DateValue withZoneDropped() {
        return new DateValue(year, month, day, Optional.of(clock()), Optional.empty());
    }

    public DateValue withZoneForgotten() {
        return new DateValue(year, month, day, timeOfDay, Optional.empty());
    }

    public DateValue withSameClockIn(int offsetMinutes) {
        return new DateValue(year, month, day,
                Optional.of(clock()), Optional.of(offsetMinutes));
    }

    public DateValue atSameInstantIn(int offsetMinutes) {
        DateValue standing = withSameClockIn(zoneMinutes.orElse(0));
        long sinceMidnight = standing.clock().nanoseconds()
                + (offsetMinutes - standing.zoneMinutes.orElse(0)) * NANOSECONDS_MINUTE;
        java.time.LocalDate landed = standing.asLocalDate()
                .plusDays(Math.floorDiv(sinceMidnight, NANOSECONDS_DAY));
        return new DateValue(landed.getYear(), landed.getMonthValue(),
                landed.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(
                        Math.floorMod(sinceMidnight, NANOSECONDS_DAY))),
                Optional.of(offsetMinutes));
    }

    private static final long NANOSECONDS_SECOND = 1_000_000_000L;
    private static final long SECONDS_DAY = 24L * 60L * 60L;

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
                - zoneMinutes.orElse(0) * NANOSECONDS_MINUTE;
        return new Moment(
                java.time.LocalDate.of(year, month, day).toEpochDay()
                        + Math.floorDiv(sinceMidnight, NANOSECONDS_DAY),
                Math.floorMod(sinceMidnight, NANOSECONDS_DAY));
    }

    private static final long NANOSECONDS_MINUTE = 60L * 1_000_000_000L;
    private static final long NANOSECONDS_DAY = 24L * 60L * NANOSECONDS_MINUTE;

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
        return rendered + "/" + timeOfDay.get() + offsetText();
    }

    private String offsetText() {
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
    public String writeInIsoForm() {
        String calendar = "%04d-%02d-%02d".formatted(year, month, day);
        if (timeOfDay.isEmpty()) {
            return calendar;
        }
        return calendar + "T" + writeIsoClock() + isoOffsetText();
    }

    private String writeIsoClock() {
        long nanoseconds = timeOfDay.orElseThrow().nanoseconds();
        long seconds = nanoseconds / SECOND;
        String written = "%02d:%02d:%02d".formatted(
                seconds / 3600, seconds / 60 % 60, seconds % 60);
        long fraction = nanoseconds % SECOND;
        if (fraction == 0) {
            return written;
        }
        String digits = "%09d".formatted(fraction).replaceAll("0+$", "");
        return written + "." + digits;
    }

    private String isoOffsetText() {
        int minutes = zoneMinutes.orElse(0);
        if (minutes == 0) {
            return "";
        }
        int size = Math.abs(minutes);
        return "%s%02d:%02d".formatted(minutes < 0 ? "-" : "+", size / 60, size % 60);
    }

    private static final long SECOND = 1_000_000_000L;
}
