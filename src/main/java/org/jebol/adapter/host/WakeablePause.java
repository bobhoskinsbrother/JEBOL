package org.jebol.adapter.host;

import org.jebol.domain.host.PausePort;

public final class WakeablePause implements PausePort {

    private static final long NANOSECONDS_IN_MILLISECOND = 1_000_000L;

    private final Object bell = new Object();
    private boolean rung;

    @Override
    public boolean pauseFor(long milliseconds) {
        long deadline = System.nanoTime() + milliseconds * NANOSECONDS_IN_MILLISECOND;
        synchronized (bell) {
            try {
                long remaining = milliseconds;
                while (!rung && remaining > 0) {
                    bell.wait(remaining);
                    remaining = (deadline - System.nanoTime()) / NANOSECONDS_IN_MILLISECOND;
                }
                rung = false;
                return true;
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    @Override
    public void wake() {
        synchronized (bell) {
            rung = true;
            bell.notifyAll();
        }
    }
}
