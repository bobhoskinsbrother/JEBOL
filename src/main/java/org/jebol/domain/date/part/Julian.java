package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.Value;

final class Julian implements WritableDateField {

    @Override
    public Value readFrom(DateValue date) {
        return DecimalValue.of(JulianDay.countedFromNoon(date));
    }

    @Override
    public DateValue writtenOn(DateValue date, Assigned given) {
        return JulianDay.asADate(given.asAJulianDayCount());
    }
}
