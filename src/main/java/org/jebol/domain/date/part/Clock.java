package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

final class Clock implements WritableDateField {

    @Override
    public boolean needsClock() {
        return true;
    }

    @Override
    public Value readFrom(DateValue date) {
        return date.clock();
    }

    @Override
    public DateValue writeOn(DateValue date, Assigned given) {
        return switch (given.value()) {
            case NoneValue _ -> date.asJustDay();
            case TimeValue clock -> date.atTime(clock);
            case DateValue other -> date.atTime(other.clock());
            case IntegerValue _ -> date.atTime(given.asClock());
            case AnyDecimalValue _ -> date.atTime(given.asClock());
            default -> throw given.refusal();
        };
    }
}
