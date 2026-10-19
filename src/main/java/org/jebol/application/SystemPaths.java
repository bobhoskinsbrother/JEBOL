package org.jebol.application;

import org.jebol.domain.value.Context;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

final class SystemPaths {

    private final Context systemContext;

    SystemPaths(Context systemContext) {
        this.systemContext = systemContext;
    }

    Value follow(String... names) {
        Value here = systemContext.knows(names[0])
                ? systemContext.findSlotFor(names[0]).value()
                : UnsetValue.unset();
        for (int at = 1; at < names.length; at++) {
            if (!(here instanceof ObjectValue(Context context))
                    || !context.holds(names[at])) {
                return UnsetValue.unset();
            }
            here = context.ownSlotFor(names[at]).value();
        }
        return here;
    }
}
