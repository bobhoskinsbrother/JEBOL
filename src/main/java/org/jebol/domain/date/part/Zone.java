package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Value;

final class Zone implements WritableDateField {

    @Override
    public boolean needsClock() {
        return true;
    }

    @Override
    public Value readFrom(DateValue date) {
        return date.zoneAsTime();
    }

    @Override
    public DateValue writeOn(DateValue date, Assigned given) {
        return given.isNothing()
                ? date.withZoneDropped()
                : date.atMidnightIfItHasNoClock()
                        .withSameClockIn(given.zoneOffsetInMinutes());
    }
}
