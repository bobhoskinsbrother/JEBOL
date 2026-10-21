package org.jebol.domain.value;

public interface PathTargetWithFields extends PathTarget {

    Context context();

    @Override
    default Value stepIntoBy(Value selector) {
        return findFieldNamedBy(selector).value();
    }

    @Override
    default Slot placeSteppedIntoBy(Value selector) {
        return findFieldNamedBy(selector);
    }

    @Override
    default void writeThrough(Slot place, Value selector, Value written) {
        ContextSlot field = findFieldNamedBy(selector);
        if (field.isProtected()) {
            throw Raised.of(EvaluationFailure.LOCKED_WORD, (Value) WordValue.of(field.spelling()));
        }
        field.setValue(written);
    }

    private ContextSlot findFieldNamedBy(Value selector) {
        if (!(selector instanceof AnyWordValue asked)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "a field is named by a word, not "
                            + selector.datatype().literalSpelling());
        }
        if (!context().holds(asked.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, asked.spelling());
        }
        return context().ownSlotFor(asked.canonical());
    }
}
