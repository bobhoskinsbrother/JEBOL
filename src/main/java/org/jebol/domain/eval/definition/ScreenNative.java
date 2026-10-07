package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ScreenPort;

public abstract class ScreenNative extends WindowNative {

    protected ScreenNative(GrantedServices granted) {
        super(granted);
    }

    protected void throughTheScreen(Runnable operation) {
        try {
            operation.run();
        } catch (ScreenPort.Denied denied) {
            throw refusedByTheHost(denied.errorId(), denied.getMessage());
        }
    }
}
