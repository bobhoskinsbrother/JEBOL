package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Value;

final class UniversalTime implements WritableDateField {

    @Override
    public Value readFrom(DateValue date) {
        return date.asStoredInUtc();
    }

    @Override
    public DateValue writeOn(DateValue date, Assigned given) {
        DateValue other = given.asDate();
        return other.zoneMinutes().orElse(0) == 0
                ? other.withTheZoneForgotten()
                : other.atSameInstantIn(0).withTheZoneForgotten();
    }
}
