package org.jebol.adapter.web;

import org.jebol.domain.host.FilePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThePageConsoleFromTheSourceTest {

    @Test
    @DisplayName("a line the page posted is a line the script reads")
    void aPostedLineIsRead() {
        WebConsole console = new WebConsole(() -> { });
        console.aLineWasTyped("typed here");

        assertThat(console.readLine()).isEqualTo("typed here");
    }

    @Test
    @DisplayName("lines are read in the order they were typed")
    void linesKeepTheirOrder() {
        WebConsole console = new WebConsole(() -> { });
        console.aLineWasTyped("first");
        console.aLineWasTyped("second");

        assertThat(console.readLine()).isEqualTo("first");
        assertThat(console.readLine()).isEqualTo("second");
    }

    @Test
    @DisplayName("an empty line is a line, and none of the ending")
    void anEmptyLineIsALine() {
        WebConsole console = new WebConsole(() -> { });
        console.aLineWasTyped("");

        assertThat(console.readLine()).isEmpty();
    }

    @Test
    @Timeout(10)
    @DisplayName("reading waits for the page rather than answering nothing")
    void readingWaitsForThePage() throws Exception {
        CountDownLatch taken = new CountDownLatch(1);
        WebConsole console = new WebConsole(taken::countDown);
        Thread typist = new Thread(() -> {
            awaitCountdown(taken);
            console.aLineWasTyped("arrived");
        });
        typist.start();

        assertThat(console.readLine()).isEqualTo("arrived");
        assertThat(taken.await(Duration.ofSeconds(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS))
                .isTrue();
        typist.join();
    }

    private static void awaitCountdown(CountDownLatch taken) {
        try {
            taken.await();
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("a hidden line is refused rather than read in the open")
    void aHiddenLineIsRefused() {
        WebConsole console = new WebConsole(() -> { });

        assertThatThrownBy(console::readHiddenLine)
                .isInstanceOf(FilePort.Denied.class)
                .hasMessageContaining("without showing it");
    }

    @Test
    @DisplayName("a page is not a terminal and reads no single keys")
    void notATerminal() {
        WebConsole console = new WebConsole(() -> { });

        assertThat(console.readKey()).isEqualTo(-1);
        assertThat(console.isATerminal()).isFalse();
    }
}
