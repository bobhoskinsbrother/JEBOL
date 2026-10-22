package org.jebol.adapter.cli;

import org.jebol.adapter.web.AnOperatorAtThePage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(180)
class NavigatingFromTheCommandLineEndToEndTest {

    private static final Pattern PAGES_ADDRESS = Pattern.compile("http://[^ ]+/");

    private static final Duration PATIENCE = Duration.ofSeconds(60);

    private static final Duration POLLING = Duration.ofMillis(50);

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

    @TempDir
    Path directory;

    @TempDir
    Path downloads;

    private String readPrinted() {
        synchronized (printed) {
            return printed.toString(StandardCharsets.UTF_8);
        }
    }

    private CompletableFuture<Integer> startCommandLine(String... arguments) {
        PrintStream out = new PrintStream(printed, true, StandardCharsets.UTF_8);
        return CompletableFuture.supplyAsync(() -> Repl.runCommandLine(arguments, out, out, directory.toString()));
    }

    private String awaitAddress() throws InterruptedException {
        waitUntil("the page's address was printed", () -> PAGES_ADDRESS.matcher(readPrinted()).find());
        return PAGES_ADDRESS.matcher(readPrinted()).results().findFirst().orElseThrow().group();
    }

    private void waitUntilPrinted(String line) throws InterruptedException {
        waitUntil("the command line printed " + line, () -> readPrinted().contains(line));
    }

    private void waitUntil(String hoped, BooleanSupplier condition) throws InterruptedException {
        long givingUpAt = System.nanoTime() + PATIENCE.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > givingUpAt) {
                throw new AssertionError(hoped + " never happened; the command line printed: " + readPrinted());
            }
            Thread.sleep(POLLING.toMillis());
        }
    }

    @Test
    @DisplayName("with --screen=web the page's address sets the app's place, and its Back arrives as an event")
    void thePageCarriesTheHistory() throws IOException, InterruptedException {
        Path script = directory.resolve("places.r3");
        Files.writeString(script, """
                Rebol []
                print ["at" mold current-place]
                view/no-wait/options make gob! [size: 100x100] [title: {Places}]
                handle-events [
                    name: 'moves
                    priority: 100
                    handler: func [event] [
                        if find [back forward] event/type [print ["moved" event/type mold event/data]]
                        event
                    ]
                ]
                go 'next-one
                print "ready"
                do-events
                """);
        startCommandLine("--screen=web", script.toString());

        try (AnOperatorAtThePage operator =
                     new AnOperatorAtThePage("chrome", awaitAddress(), downloads).opensThePageAt("#survey%203")) {
            waitUntilPrinted("ready");
            operator.waitUntilTheAddressIs("#next-one");

            operator.pressesBack();

            waitUntilPrinted("moved back [survey 3]");
        } catch (AssertionError stuck) {
            throw new AssertionError(stuck.getMessage() + "; the command line printed: " + readPrinted(), stuck);
        }
        assertThat(readPrinted()).contains("at [survey 3]");
    }

    @Test
    @DisplayName("the page going away for good closes the app's windows, so the run ends")
    void thePageGoingAwayEndsTheRun() throws Exception {
        Path script = directory.resolve("closing.r3");
        Files.writeString(script, """
                Rebol []
                view/no-wait/options make gob! [size: 100x100] [title: {Closing}]
                unsaved-work false
                print "ready"
                do-events
                print "windows closed"
                """);
        CompletableFuture<Integer> running = startCommandLine("--screen=web", script.toString());

        try (AnOperatorAtThePage operator =
                     new AnOperatorAtThePage("chrome", awaitAddress(), downloads).opensThePage()) {
            waitUntilPrinted("ready");

            operator.goesElsewhere("/elsewhere-that-is-not-served");

            assertThat(running.get(60, TimeUnit.SECONDS)).isZero();
        } catch (AssertionError stuck) {
            throw new AssertionError(stuck.getMessage() + "; the command line printed: " + readPrinted(), stuck);
        }
        assertThat(readPrinted()).contains("windows closed");
    }

    @Test
    @DisplayName("with no screen a move is refused as not present, never as not granted")
    void noScreenIsNotPresent() {
        startCommandLine("--do", """
                print ["go:" mold try [go 'a]]
                print ["place:" mold try [current-place]]
                print "done"
                """).join();

        assertThat(readPrinted()).contains("done").contains("not present").doesNotContain("not granted");
    }
}
