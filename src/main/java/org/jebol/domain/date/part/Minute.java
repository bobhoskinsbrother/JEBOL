package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

final class Minute implements WritableDateField {

    @Override
    public boolean needsClock() {
        return true;
    }

    @Override
    public Value readFrom(DateValue date) {
        return IntegerValue.of(date.minuteOfTheHour());
    }

    @Override
    public DateValue writeOn(DateValue date, Assigned given) {
        return date.atTime(date.clock().withMinute(given.asWholeNumber()));
    }
}
