package org.jebol.adapter.web;

import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.FilePort;

import java.util.ArrayDeque;
import java.util.Deque;

public final class WebConsole implements ConsolePort {

    private static final long A_SLICE = 20;

    private final Runnable runnable;
    private final Deque<String> typed = new ArrayDeque<>();

    public WebConsole(Runnable runnable) {
        this.runnable = runnable;
    }

    public void takeTypedLine(String text) {
        typed.add(text);
    }

    @Override
    public String readLine() {
        while (typed.isEmpty()) {
            runnable.run();
            letSlicePass();
        }
        return typed.poll();
    }

    @Override
    public String readHiddenLine() {
        throw new FilePort.Denied("no-port",
                "this page has no way to read a line without showing it");
    }

    private static void letSlicePass() {
        try {
            Thread.sleep(A_SLICE);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }
}
