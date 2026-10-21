package org.jebol.application;

import org.jebol.domain.host.PausePort;

public final class ThreadPause implements PausePort {

    @Override
    public boolean pauseFor(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
            return true;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
