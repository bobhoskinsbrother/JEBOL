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
        if (!(selector instanceof AnyWordValue asked) || !context().holds(asked.canonical())) {
            throw Raised.cannotSelect(selector);
        }
        return context().ownSlotFor(asked.canonical());
    }
}
