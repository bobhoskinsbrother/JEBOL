package org.jebol.adapter.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DaemonThreadsTest {

    private record Seen(String name, boolean daemon, boolean virtual) {
        static Seen ofCurrentThread() {
            Thread running = Thread.currentThread();
            return new Seen(running.getName(), running.isDaemon(), running.isVirtual());
        }
    }

    @Test
    @DisplayName("a started thread runs the work, named, as a platform thread that never keeps the process alive")
    void startsANamedDaemon() throws Exception {
        CompletableFuture<Seen> seen = new CompletableFuture<>();

        Thread started = DaemonThreads.start("jebol-test", () -> seen.complete(Seen.ofCurrentThread()));
        started.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(seen.get(5, TimeUnit.SECONDS)).isEqualTo(new Seen("jebol-test", true, false));
    }

    @Test
    @DisplayName("a factory's threads are the same, for an executor")
    void aFactoryMakesNamedDaemons() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(DaemonThreads.factory("jebol-reading"));
        try {
            Seen seen = executor.submit(Seen::ofCurrentThread).get(5, TimeUnit.SECONDS);
            assertThat(seen).isEqualTo(new Seen("jebol-reading", true, false));
        } finally {
            executor.shutdownNow();
        }
    }
}
