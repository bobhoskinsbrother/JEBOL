package org.jebol.domain.eval;

import org.jebol.domain.date.part.DatePart;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Slot;
import org.jebol.domain.value.Value;

final class DateDispatcher implements Dispatcher {

    @Override
    public Value readFrom(Value target, Value selector) {
        return DatePart.readFrom(dateIn(target), selector);
    }

    @Override
    public void writeTo(Slot place, Value selector, Value written) {
        place.setValue(DatePart.writtenOn(dateIn(place.value()), selector, written));
    }

    private static DateValue dateIn(Value target) {
        if (target instanceof DateValue date) {
            return date;
        }
        throw Raised.of(EvaluationFailure.BAD_PATH_TYPE,
                target.datatype().literalSpelling());
    }
}
