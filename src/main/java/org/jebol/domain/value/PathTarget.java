package org.jebol.domain.value;

public interface PathTarget {

    Value steppedIntoBy(Value selector);

    default Slot placeSteppedIntoBy(Value selector) {
        return new ComputedSlot(steppedIntoBy(selector), selector);
    }

    default void writeThrough(Slot place, Value selector, Value written) {
        throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                place.value().datatype().literalSpelling());
    }
}
