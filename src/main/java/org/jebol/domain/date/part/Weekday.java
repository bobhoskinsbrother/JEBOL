package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

final class Weekday implements DateField {

    @Override
    public Value readFrom(DateValue date) {
        return IntegerValue.of(date.asLocalDate().getDayOfWeek().getValue());
    }
}
