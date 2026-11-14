package org.jebol.adapter.host;

import java.util.concurrent.ThreadFactory;

final class DaemonThreads {

    private DaemonThreads() {
    }

    static Thread start(String name, Runnable work) {
        Thread thread = factory(name).newThread(work);
        thread.start();
        return thread;
    }

    static ThreadFactory factory(String name) {
        return work -> {
            Thread thread = new Thread(work, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
