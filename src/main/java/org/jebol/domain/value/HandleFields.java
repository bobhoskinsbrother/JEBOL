package org.jebol.domain.value;

import java.util.Optional;

public interface HandleFields {

    enum FieldWriting { WRITTEN, NO_SUCH_FIELD, WRONG_TYPE }

    Optional<Value> readField(String field);

    FieldWriting writeField(String field, Value written);
}
