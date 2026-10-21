package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Value;

final class Second implements WritableDateField {

    @Override
    public boolean needsClock() {
        return true;
    }

    @Override
    public Value readFrom(DateValue date) {
        return date.secondOnClock();
    }

    @Override
    public DateValue writeOn(DateValue date, Assigned given) {
        return date.atTime(
                date.clock().withSecond(given.asSecondsInNanoseconds()));
    }
}
