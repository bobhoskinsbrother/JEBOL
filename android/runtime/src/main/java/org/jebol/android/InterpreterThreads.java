package org.jebol.android;

public final class InterpreterThreads {

    public static final long STACK_BYTES = 64L * 1024 * 1024;

    private InterpreterThreads() {
    }

    public static Thread start(String name, Runnable work) {
        Thread started = new Thread(null, work, name, STACK_BYTES);
        started.setDaemon(true);
        started.start();
        return started;
    }
}
