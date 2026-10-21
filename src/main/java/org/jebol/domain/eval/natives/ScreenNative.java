package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.host.ScreenPort;

import java.util.function.Supplier;

public abstract class ScreenNative extends WindowNative {

    protected ScreenNative(GrantedServices granted) {
        super(granted);
    }

    protected void runThroughTheScreen(Runnable operation) {
        askThroughScreen(() -> {
            operation.run();
            return null;
        });
    }

    protected <T> T askThroughScreen(Supplier<T> asking) {
        try {
            return asking.get();
        } catch (ScreenPort.Denied denied) {
            throw refuseForTheHost(denied.errorId(), denied.getMessage());
        }
    }
}
