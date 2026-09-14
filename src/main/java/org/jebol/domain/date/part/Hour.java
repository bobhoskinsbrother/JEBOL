package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

final class Hour implements WritableDateField {

    @Override
    public boolean needsAClock() {
        return true;
    }

    @Override
    public Value readFrom(DateValue date) {
        return IntegerValue.of(date.hourOfTheDay());
    }

    @Override
    public DateValue writtenOn(DateValue date, Assigned given) {
        return date.atTheTime(date.clock().withTheHour(given.asWholeNumber()));
    }
}
