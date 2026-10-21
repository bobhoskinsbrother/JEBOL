package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Value;

final class Timezone implements WritableDateField {

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
        return date.atMidnightIfItHasNoClock()
                .atSameInstantIn(given.zoneOffsetInMinutes());
    }
}
