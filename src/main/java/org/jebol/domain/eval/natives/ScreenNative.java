package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.host.ScreenPort;

import java.util.function.Supplier;

public abstract class ScreenNative extends WindowNative {

    protected ScreenNative(GrantedServices granted) {
        super(granted);
    }

    protected void throughTheScreen(Runnable operation) {
        answeredThroughTheScreen(() -> {
            operation.run();
            return null;
        });
    }

    protected <T> T answeredThroughTheScreen(Supplier<T> asking) {
        try {
            return asking.get();
        } catch (ScreenPort.Denied denied) {
            throw refusedByTheHost(denied.errorId(), denied.getMessage());
        }
    }
}
