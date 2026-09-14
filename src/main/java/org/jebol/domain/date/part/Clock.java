package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

final class Clock implements WritableDateField {

    @Override
    public boolean needsAClock() {
        return true;
    }

    @Override
    public Value readFrom(DateValue date) {
        return date.clock();
    }

    @Override
    public DateValue writtenOn(DateValue date, Assigned given) {
        return switch (given.value()) {
            case NoneValue _ -> date.asJustTheDay();
            case TimeValue clock -> date.atTheTime(clock);
            case DateValue other -> date.atTheTime(other.clock());
            case IntegerValue _ -> date.atTheTime(given.asAClock());
            case DecimalValue _ -> date.atTheTime(given.asAClock());
            default -> throw given.refusal();
        };
    }
}
