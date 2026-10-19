package org.jebol.domain.value;

public interface PathTarget {

    Value stepIntoBy(Value selector);

    default Slot placeSteppedIntoBy(Value selector) {
        return new ComputedSlot(stepIntoBy(selector), selector);
    }

    default void writeThrough(Slot place, Value selector, Value written) {
        throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                place.value().datatype().literalSpelling());
    }
}
