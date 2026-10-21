package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Value;

final class CalendarDay implements WritableDateField {

    @Override
    public Value readFrom(DateValue date) {
        return date.asJustDay();
    }

    @Override
    public DateValue writeOn(DateValue date, Assigned given) {
        return date.onDayFrom(given.asDate());
    }
}
