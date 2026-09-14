package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Value;

final class Timezone implements WritableDateField {

    @Override
    public boolean needsAClock() {
        return true;
    }

    @Override
    public Value readFrom(DateValue date) {
        return date.zoneAsATime();
    }

    @Override
    public DateValue writtenOn(DateValue date, Assigned given) {
        return date.atMidnightIfItHasNoClock()
                .atTheSameInstantIn(given.asAZoneOffsetInMinutes());
    }
}
