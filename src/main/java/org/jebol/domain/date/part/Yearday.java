package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

final class Yearday implements WritableDateField {

    @Override
    public Value readFrom(DateValue date) {
        return IntegerValue.of(date.asLocalDate().getDayOfYear());
    }

    @Override
    public DateValue writtenOn(DateValue date, Assigned given) {
        return date.onTheDayOfTheYear(given.asWholeNumber());
    }
}
