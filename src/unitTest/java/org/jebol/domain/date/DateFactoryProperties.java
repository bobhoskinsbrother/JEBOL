package org.jebol.domain.date;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DateFactoryProperties {

    private static final long EARLIEST_WHOLE_SECOND = -62_167_219_200L;
    private static final long LATEST_WHOLE_SECOND = 454_861_871_999L;

    private static DateValue madeFrom(Value... parts) {
        return (DateValue) DateFactory.fromParts(List.of(parts));
    }

    private static String refusalOf(Value... parts) {
        try {
            DateFactory.fromParts(List.of(parts));
            return "nothing refused";
        } catch (Raised raised) {
            return raised.error().errorId();
        }
    }

    private static DateValue atWholeSeconds(long seconds) {
        return (DateValue) DateFactory.fromWholeSeconds(seconds);
    }

    @Property
    void everyThreeNumberCalendarDayAgreesWithJava(
            @ForAll @IntRange(min = -2, max = 33) int day,
            @ForAll @IntRange(min = -2, max = 14) int month,
            @ForAll @IntRange(max = DateValue.LATEST_YEAR) int year) {

        Optional<LocalDate> javaDay = theDayJavaCalls(year, month, day);
        if (javaDay.isPresent()) {
            assertThat(madeFrom(IntegerValue.of(day), IntegerValue.of(month),
                    IntegerValue.of(year)))
                    .isEqualTo(DateValue.of(year, month, day));
        } else {
            assertThat(refusalOf(IntegerValue.of(day), IntegerValue.of(month),
                    IntegerValue.of(year)))
                    .isEqualTo("bad-make-arg");
        }
    }

    private Optional<LocalDate> theDayJavaCalls(int year, int month, int day) {
        try {
            return Optional.of(LocalDate.of(year, month, day));
        } catch (DateTimeException notADay) {
            return Optional.empty();
        }
    }

    @Property
    void everyWholeSecondInTheSupportedYearsComesBackAsItself(
            @ForAll @LongRange(min = EARLIEST_WHOLE_SECOND,
                    max = LATEST_WHOLE_SECOND) long seconds) {
        assertThat(atWholeSeconds(seconds).wholeSecondsSinceEpoch())
                .isEqualTo(seconds);
    }
}
