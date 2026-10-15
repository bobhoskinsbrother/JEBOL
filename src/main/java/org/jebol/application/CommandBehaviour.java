package org.jebol.application;

import org.jebol.domain.value.Value;

@FunctionalInterface
public interface CommandBehaviour {

    Value answer(CommandCall call);
}
