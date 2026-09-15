package org.jebol.domain.eval;

import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Slot;
import org.jebol.domain.value.TaskValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

final class ContextDispatcher implements Dispatcher {

    @Override
    public Value readFrom(Value target, Value selector) {
        return fieldNamedBy(target, selector).value();
    }

    @Override
    public Slot placeWithin(Slot holder, Value selector) {
        return fieldNamedBy(holder.value(), selector);
    }

    @Override
    public void writeTo(Slot place, Value selector, Value written) {
        ContextSlot field = fieldNamedBy(place.value(), selector);
        if (field.isProtected()) {
            throw Raised.of(EvaluationFailure.LOCKED_WORD, "the field is protected");
        }
        field.setValue(written);
    }

    private static ContextSlot fieldNamedBy(Value target, Value selector) {
        if (!(selector instanceof WordValue asked)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "a field is named by a word, not "
                            + selector.datatype().literalSpelling());
        }
        Context fields = contextOf(target);
        if (!fields.holds(asked.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, asked.spelling());
        }
        return fields.ownSlotFor(asked.canonical());
    }

    private static Context contextOf(Value target) {
        return switch (target) {
            case ObjectValue object -> object.context();
            case PortValue port -> port.context();
            case ModuleValue module -> module.context();
            case TaskValue task -> task.context();
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_TYPE,
                    target.datatype().literalSpelling());
        };
    }
}
