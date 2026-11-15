package org.jebol.application;

public final class TestThreads {

    private TestThreads() {
    }

    public static Thread start(Runnable work) {
        Thread thread = new Thread(work, "jebol-test");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
