package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

final class Day implements WritableDateField {

    @Override
    public Value readFrom(DateValue date) {
        return IntegerValue.of(date.day());
    }

    @Override
    public DateValue writtenOn(DateValue date, Assigned given) {
        return date.onTheDay(date.year(), date.month(), given.asWholeNumber());
    }
}
