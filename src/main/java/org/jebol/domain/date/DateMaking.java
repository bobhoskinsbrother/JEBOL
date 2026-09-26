package org.jebol.domain.date;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class DateMaking {

    private DateMaking() {
    }

    private static final long SECONDS_A_MINUTE = 60L;
    private static final long SECONDS_AN_HOUR = 3600L;
    private static final long MICROSECONDS_A_SECOND = 1_000_000L;
    private static final long MICROSECONDS_A_DAY = 86_400L * MICROSECONDS_A_SECOND;

    private static final int FURTHEST_ZONE_MINUTES = 15 * 60;

    public static Value atTheTimestamp(long microseconds) {
        long dayNumber = Math.floorDiv(microseconds, MICROSECONDS_A_DAY);
        long withinTheDay = Math.floorMod(microseconds, MICROSECONDS_A_DAY);
        LocalDate day = LocalDate.ofEpochDay(dayNumber);
        return DateValue.of(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                TimeValue.ofNanoseconds(withinTheDay * 1_000L));
    }

    public static long microsecondsInASecond() {
        return MICROSECONDS_A_SECOND;
    }

    public static Value fromParts(List<Value> parts) {
        if (parts.isEmpty()) {
            throw refuse(parts);
        }
        int afterTheCalendar = parts.getFirst() instanceof DateValue ? 1 : 3;
        if (parts.size() < afterTheCalendar) {
            throw refuse(parts);
        }
        DateValue calendar = parts.getFirst() instanceof DateValue already
                ? already
                : calendarDayIn(parts);
        List<Value> after = parts.subList(afterTheCalendar, parts.size());
        int clockTakes = howManyPartsTheClockTakes(after);
        if (after.size() < clockTakes) {
            throw refuse(parts);
        }
        Optional<TimeValue> clock = clockTakes == 0
                ? Optional.empty()
                : Optional.of(clockIn(after.subList(0, clockTakes), parts));
        return new DateValue(calendar.year(), calendar.month(), calendar.day(), clock,
                zoneAfterTheClock(after.subList(clockTakes, after.size()), parts));
    }

    private static DateValue calendarDayIn(List<Value> parts) {
        if (parts.get(0) instanceof IntegerValue(long magnitude2)
                && parts.get(1) instanceof IntegerValue(long magnitude1)
                && parts.get(2) instanceof IntegerValue(long magnitude)) {
            int day = (int) magnitude2;
            int year = (int) magnitude;
            if (day > MOST_A_DAY_OF_THE_MONTH_COULD_BE) {
                year = day;
                day = (int) magnitude;
            }
            try {
                return DateValue.of(year, (int) magnitude1, day);
            } catch (IllegalArgumentException namesNoDay) {
                throw refuse(parts);
            }
        }
        throw refuse(parts);
    }

    private static final int MOST_A_DAY_OF_THE_MONTH_COULD_BE = 99;

    private static int howManyPartsTheClockTakes(List<Value> after) {
        if (after.isEmpty()) {
            return 0;
        }
        if (after.getFirst() instanceof TimeValue) {
            return 1;
        }
        return after.getFirst() instanceof IntegerValue ? 3 : 0;
    }

    private static TimeValue clockIn(List<Value> written, List<Value> whole) {
        if (written.size() == 1 && written.getFirst() instanceof TimeValue already) {
            return already;
        }
        if (!(written.get(0) instanceof IntegerValue(long magnitude1))
                || !(written.get(1) instanceof IntegerValue(long magnitude))
                || !(written.get(2) instanceof IntegerValue
                        || written.get(2) instanceof DecimalValue)) {
            throw refuse(whole);
        }
        double second = Comparison.asDouble(written.get(2));
        if (magnitude1 < 0 || magnitude1 > 23
                || magnitude < 0 || magnitude >= 60
                || second < 0 || second >= 60.0) {
            throw refuse(whole);
        }
        return TimeValue.ofNanoseconds(
                magnitude1 * SECONDS_AN_HOUR * TimeValue.NANOSECONDS_PER_SECOND
                        + magnitude * SECONDS_A_MINUTE
                                * TimeValue.NANOSECONDS_PER_SECOND
                        + Math.round(second * TimeValue.NANOSECONDS_PER_SECOND));
    }

    private static Optional<Integer> zoneAfterTheClock(
            List<Value> left, List<Value> whole) {

        if (left.isEmpty()) {
            return Optional.empty();
        }
        if (left.size() > 1 || !(left.getFirst() instanceof TimeValue(long nanoseconds))) {
            throw refuse(whole);
        }
        long minutes = nanoseconds
                / (SECONDS_A_MINUTE * TimeValue.NANOSECONDS_PER_SECOND);
        if (Math.abs(minutes) > FURTHEST_ZONE_MINUTES) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a zone reaches fifteen hours either side of UTC");
        }
        return Optional.of((int) minutes);
    }

    private static Raised refuse(List<Value> parts) {
        return Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                DatatypeValue.of(Datatype.DATE), BlockValue.block(parts));
    }
}
