package org.jebol.domain.eval;

import org.jebol.domain.value.Slot;
import org.jebol.domain.value.Value;

interface Dispatcher {

    Value readFrom(Value target, Value selector);

    default Slot placeWithin(Slot holder, Value selector) {
        return new ComputedSlot(readFrom(holder.value(), selector), selector);
    }

    default void writeTo(Slot place, Value selector, Value written) {
        throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                place.value().datatype().literalSpelling());
    }
}
