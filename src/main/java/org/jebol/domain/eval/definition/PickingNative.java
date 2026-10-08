package org.jebol.domain.eval.definition;

import org.jebol.domain.date.part.DatePart;
import org.jebol.domain.eval.BitsetActions;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Value;

public abstract class PickingNative extends DefaultNative {

    protected Value pickedBy(Value subject, Value selector) {
        return switch (subject) {
            case BitsetValue members -> new BitsetActions(members).heldForAPath(selector);
            case DateValue date -> DatePart.readFrom(date, selector);
            case Value anythingElse -> anythingElse.pickedBy(selector);
        };
    }
}
