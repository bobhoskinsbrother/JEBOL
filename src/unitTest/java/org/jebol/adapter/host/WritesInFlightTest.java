package org.jebol.adapter.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class WritesInFlightTest {

    private static final WritesInFlight.Ends SERVER = new WritesInFlight.Ends(4000, 51000);
    private static final WritesInFlight.Ends CLIENT = new WritesInFlight.Ends(51000, 4000);
    private static final WritesInFlight.Ends ANOTHER_CLIENT = new WritesInFlight.Ends(52000, 4000);

    private final WritesInFlight inFlight = new WritesInFlight(Duration.ofSeconds(10));

    private static long millisecondsTaken(Runnable work) {
        long started = System.nanoTime();
        work.run();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    @Test
    @DisplayName("a read with no write in flight from its peer goes straight on")
    void nothingInFlightGoesStraightOn() {
        assertThat(millisecondsTaken(() -> inFlight.awaitPeerOf(CLIENT))).isLessThan(1000);
    }

    @Test
    @DisplayName("a read waits until its peer's write has ended")
    void waitsForThePeersWrite() throws Exception {
        CountDownLatch reported = inFlight.begin(SERVER);
        CompletableFuture<Void> reading = CompletableFuture.runAsync(() -> inFlight.awaitPeerOf(CLIENT));

        Thread.sleep(200);
        assertThat(reading).isNotDone();

        inFlight.end(SERVER, reported);
        reading.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("a write between other ends never holds a read")
    void anotherPairNeverHolds() {
        inFlight.begin(new WritesInFlight.Ends(4000, 52000));

        assertThat(millisecondsTaken(() -> inFlight.awaitPeerOf(CLIENT))).isLessThan(1000);
        assertThat(ANOTHER_CLIENT.peer()).isEqualTo(new WritesInFlight.Ends(4000, 52000));
    }

    @Test
    @DisplayName("a write that never ends holds a read no longer than the longest wait")
    void theWaitIsBounded() {
        WritesInFlight brief = new WritesInFlight(Duration.ofMillis(200));
        brief.begin(SERVER);

        assertThat(millisecondsTaken(() -> brief.awaitPeerOf(CLIENT))).isBetween(150L, 5000L);
    }

    @Test
    @DisplayName("an ended write holds nothing afterwards")
    void anEndedWriteHoldsNothing() {
        inFlight.end(SERVER, inFlight.begin(SERVER));

        assertThat(millisecondsTaken(() -> inFlight.awaitPeerOf(CLIENT))).isLessThan(1000);
    }
}
