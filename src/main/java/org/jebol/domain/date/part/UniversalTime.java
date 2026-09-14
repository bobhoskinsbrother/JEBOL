package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Value;

final class UniversalTime implements WritableDateField {

    @Override
    public Value readFrom(DateValue date) {
        return date.asStoredInUtc();
    }

    @Override
    public DateValue writtenOn(DateValue date, Assigned given) {
        DateValue other = given.asADate();
        return other.zoneMinutes().orElse(0) == 0
                ? other.withTheZoneForgotten()
                : other.atTheSameInstantIn(0).withTheZoneForgotten();
    }
}
