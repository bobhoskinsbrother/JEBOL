package org.jebol.domain.eval;

import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleFields;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.Optional;

final class HandlePath {

    private static final String TYPE = "type";

    private HandlePath() {
    }

    static Value read(HandleValue handle, Value selector) {
        if (!(selector instanceof AnyWordValue field)) {
            throw Raised.cannotSelect(selector);
        }
        if (!handle.isContext()) {
            return NoneValue.none();
        }
        Optional<Value> answered = findFields(handle).flatMap(fields -> fields.readField(field.canonical()));
        if (answered.isPresent()) {
            return answered.get();
        }
        if (field.canonical().equals(TYPE)) {
            return WordValue.of(handle.typeName());
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH);
    }

    static void write(HandleValue handle, AnyWordValue field, Value written) {
        Optional<HandleFields> fields = handle.isContext() ? findFields(handle) : Optional.empty();
        HandleFields.FieldWriting outcome = fields
                .map(held -> held.writeField(field.canonical(), written))
                .orElse(HandleFields.FieldWriting.NO_SUCH_FIELD);
        switch (outcome) {
            case WRITTEN -> {
            }
            case WRONG_TYPE -> throw Raised.of(EvaluationFailure.BAD_FIELD_SET, field, written.datatype());
            case NO_SUCH_FIELD -> throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        }
    }

    private static Optional<HandleFields> findFields(HandleValue handle) {
        return handle.payload() instanceof JavaObjectValue carried
                ? carried.held().filter(HandleFields.class::isInstance).map(HandleFields.class::cast)
                : Optional.empty();
    }
}
