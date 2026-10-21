package org.jebol.domain.date;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class DateFactory {

    private DateFactory() {
    }

    private static final long SECONDS_IN_MINUTE = 60L;
    private static final long SECONDS_IN_HOUR = 3600L;
    private static final long SECONDS_IN_DAY = 86_400L;
    private static final long MICROSECONDS_IN_SECOND = 1_000_000L;
    private static final long MICROSECONDS_IN_DAY = SECONDS_IN_DAY * MICROSECONDS_IN_SECOND;

    private static final int FURTHEST_ZONE_MINUTES = 15 * 60;

    public static Value fromTimestamp(long microseconds) {
        return onTheDayOfTheEpoch(
                Math.floorDiv(microseconds, MICROSECONDS_IN_DAY),
                Math.floorMod(microseconds, MICROSECONDS_IN_DAY) * 1_000L,
                microseconds);
    }

    public static Value fromWholeSeconds(long seconds) {
        return onTheDayOfTheEpoch(
                Math.floorDiv(seconds, SECONDS_IN_DAY),
                Math.floorMod(seconds, SECONDS_IN_DAY)
                        * TimeValue.NANOSECONDS_PER_SECOND,
                seconds);
    }

    private static Value onTheDayOfTheEpoch(
            long dayNumber, long nanosecondsIntoTheDay, long about) {
        try {
            LocalDate day = LocalDate.ofEpochDay(dayNumber);
            requireSupportedYear(day.getYear(), about);
            return DateValue.of(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                    TimeValue.ofNanoseconds(nanosecondsIntoTheDay));
        } catch (DateTimeException outsideTheSupportedYears) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(about));
        }
    }

    private static void requireSupportedYear(int year, long about) {
        if (year < DateValue.EARLIEST_YEAR || year > DateValue.LATEST_YEAR) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(about));
        }
    }

    public static Value fromParts(List<Value> parts) {
        if (parts.isEmpty()) {
            throw refuse(parts);
        }
        List<Value> after;
        DateValue calendar;
        if (parts.getFirst() instanceof DateValue already) {
            calendar = already;
            if (parts.size() == 1) {
                return calendar;
            }
            if (!(parts.get(1) instanceof TimeValue || parts.get(1) instanceof IntegerValue)) {
                throw refuse(parts);
            }
            after = parts.subList(1, parts.size());
        } else {
            if (parts.size() < 3) {
                throw refuse(parts);
            }
            calendar = readCalendarDay(parts);
            after = parts.subList(3, parts.size());
        }
        int clockTakes = countParts(after);
        if (after.size() < clockTakes) {
            throw refuse(parts);
        }
        if (clockTakes == 0) {
            if (after.isEmpty()) {
                return calendar;
            }
            throw refuse(parts);
        }
        TimeValue clock = readClock(after.subList(0, clockTakes), parts);
        Optional<Integer> zone =
                readZoneAfterClock(after.subList(clockTakes, after.size()), parts);
        return withClockNormalised(calendar, clock, zone, parts);
    }

    private static DateValue withClockNormalised(
            DateValue calendar, TimeValue clock, Optional<Integer> zone, List<Value> parts) {
        long wholeDays = Math.floorDiv(clock.nanoseconds(), TimeValue.NANOSECONDS_PER_DAY);
        long withinTheDay = Math.floorMod(clock.nanoseconds(), TimeValue.NANOSECONDS_PER_DAY);
        try {
            LocalDate day = calendar.asLocalDate().plusDays(wholeDays);
            if (day.getYear() < DateValue.EARLIEST_YEAR || day.getYear() > DateValue.LATEST_YEAR) {
                throw refuse(parts);
            }
            return new DateValue(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                    Optional.of(TimeValue.ofNanoseconds(withinTheDay)), zone);
        } catch (DateTimeException outsideTheSupportedYears) {
            throw refuse(parts);
        }
    }

    private static DateValue readCalendarDay(List<Value> parts) {
        if (parts.get(0) instanceof IntegerValue(long first)
                && parts.get(1) instanceof IntegerValue(long month)
                && parts.get(2) instanceof IntegerValue(long third)) {
            long day = first;
            long year = third;
            if (first > MOST_A_DAY_OF_THE_MONTH_COULD_BE) {
                year = first;
                day = third;
            }
            if (year < DateValue.EARLIEST_YEAR || year > DateValue.LATEST_YEAR
                    || day < 1 || day > Integer.MAX_VALUE
                    || month < 1 || month > Integer.MAX_VALUE) {
                throw refuse(parts);
            }
            try {
                return DateValue.of((int) year, (int) month, (int) day);
            } catch (IllegalArgumentException namesNoDay) {
                throw refuse(parts);
            }
        }
        throw refuse(parts);
    }

    private static final int MOST_A_DAY_OF_THE_MONTH_COULD_BE = 99;

    private static int countParts(List<Value> after) {
        if (after.isEmpty()) {
            return 0;
        }
        if (after.getFirst() instanceof TimeValue) {
            return 1;
        }
        return after.getFirst() instanceof IntegerValue ? 3 : 0;
    }

    private static TimeValue readClock(List<Value> written, List<Value> whole) {
        if (written.size() == 1 && written.getFirst() instanceof TimeValue already) {
            return already;
        }
        if (!(written.get(0) instanceof IntegerValue(long magnitude1))
                || !(written.get(1) instanceof IntegerValue(long magnitude))
                || !(written.get(2) instanceof IntegerValue
                        || written.get(2) instanceof AnyDecimalValue)) {
            throw refuse(whole);
        }
        double second = Comparison.asDouble(written.get(2));
        if (magnitude1 < 0 || magnitude1 > 23
                || magnitude < 0 || magnitude >= 60
                || second < 0 || second >= 60.0) {
            throw refuse(whole);
        }
        return TimeValue.ofNanoseconds(
                magnitude1 * SECONDS_IN_HOUR * TimeValue.NANOSECONDS_PER_SECOND
                        + magnitude * SECONDS_IN_MINUTE
                                * TimeValue.NANOSECONDS_PER_SECOND
                        + Math.round(second * TimeValue.NANOSECONDS_PER_SECOND));
    }

    private static Optional<Integer> readZoneAfterClock(
            List<Value> left, List<Value> whole) {

        if (left.isEmpty()) {
            return Optional.empty();
        }
        if (left.size() > 1 || !(left.getFirst() instanceof TimeValue(long nanoseconds))) {
            throw refuse(whole);
        }
        long minutes = nanoseconds
                / (SECONDS_IN_MINUTE * TimeValue.NANOSECONDS_PER_SECOND);
        if (Math.abs(minutes) > FURTHEST_ZONE_MINUTES) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a zone reaches fifteen hours either side of UTC");
        }
        return Optional.of((int) minutes);
    }

    private static Raised refuse(List<Value> parts) {
        return DateValue.TYPE.refuse(BlockValue.of(parts));
    }
}
