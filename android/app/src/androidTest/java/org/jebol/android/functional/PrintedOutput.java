package org.jebol.android.functional;

import org.jebol.domain.host.OutputPort;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

final class PrintedOutput implements OutputPort {

    private final StringBuilder printed = new StringBuilder();

    @Override
    public synchronized void write(String text) {
        printed.append(text);
        notifyAll();
    }

    synchronized boolean awaitPrinted(String mark, Duration atMost) throws InterruptedException {
        long givenUpAt = System.nanoTime() + atMost.toNanos();
        while (printed.indexOf(mark) < 0) {
            long left = givenUpAt - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        return true;
    }

    synchronized String text() {
        return printed.toString();
    }
}
