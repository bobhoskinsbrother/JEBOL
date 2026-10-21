package org.jebol.domain.date.part;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.WordValue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum DatePart implements DateField {

    YEAR(new Year()),
    MONTH(new Month()),
    DAY(new Day()),
    TIME(new Clock()),
    DATE(new CalendarDay()),
    ZONE(new Zone()),
    HOUR(new Hour()),
    MINUTE(new Minute()),
    SECOND(new Second()),
    WEEKDAY(new Weekday()),
    YEARDAY(new Yearday()),
    TIMEZONE(new Timezone()),
    UTC(new UniversalTime()),
    JULIAN(new Julian());

    private final DateField field;

    DatePart(DateField field) {
        this.field = field;
    }

    @Override
    public boolean needsClock() {
        return field.needsClock();
    }

    @Override
    public Value readFrom(DateValue date) {
        return needsClock() && date.timeOfDay().isEmpty()
                ? NoneValue.none()
                : field.readFrom(date);
    }

    public DateValue writeOn(DateValue date, Value given) {
        if (field instanceof WritableDateField writable) {
            return writable.writeOn(date, new Assigned(given, WordValue.of(spelling())));
        }
        throw Raised.of(EvaluationFailure.BAD_PATH_SET, spelling());
    }

    public static Value readFrom(DateValue date, Value selector) {
        return findThePartNamed(selector)
                .map(part -> part.readFrom(date))
                .orElseGet(NoneValue::none);
    }

    public static DateValue writeOn(DateValue date, Value selector, Value given) {
        return findThePartNamed(selector)
                .orElseThrow(() -> refuseUnknownPart(selector))
                .writeOn(date, given);
    }

    public static List<String> partNames() {
        return Arrays.stream(values()).map(DatePart::spelling).toList();
    }

    private static Optional<DatePart> findThePartNamed(Value selector) {
        if (selector instanceof AnyWordValue asked) {
            return Arrays.stream(values())
                    .filter(part -> part.spelling().equals(asked.canonical()))
                    .findFirst();
        }
        if (!(selector instanceof IntegerValue(long counted))) {
            return Optional.empty();
        }
        return counted >= 1 && counted <= values().length
                ? Optional.of(values()[(int) counted - 1])
                : Optional.empty();
    }

    private String spelling() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static Raised refuseUnknownPart(Value selector) {
        return Raised.of(EvaluationFailure.INVALID_PATH,
                selector instanceof AnyWordValue word ? word.spelling() : "date");
    }
}
