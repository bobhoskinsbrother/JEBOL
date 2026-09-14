package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Value;

interface DateField {

    Value readFrom(DateValue date);

    default boolean needsAClock() {
        return false;
    }
}
