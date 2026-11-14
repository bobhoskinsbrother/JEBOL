package org.jebol.adapter.host;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class WritesInFlight {

    record Ends(int localPort, int remotePort) {

        Ends peer() {
            return new Ends(remotePort, localPort);
        }
    }

    private final Map<Ends, CountDownLatch> writing = new ConcurrentHashMap<>();
    private final Duration longestWait;

    WritesInFlight(Duration longestWait) {
        this.longestWait = longestWait;
    }

    CountDownLatch begin(Ends writer) {
        CountDownLatch reported = new CountDownLatch(1);
        writing.put(writer, reported);
        return reported;
    }

    void end(Ends writer, CountDownLatch reported) {
        reported.countDown();
        writing.remove(writer, reported);
    }

    void awaitPeerOf(Ends reader) {
        CountDownLatch peersWrite = writing.get(reader.peer());
        if (peersWrite == null) {
            return;
        }
        try {
            peersWrite.await(longestWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }
}
