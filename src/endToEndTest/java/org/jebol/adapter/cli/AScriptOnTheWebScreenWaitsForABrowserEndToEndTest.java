package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AScriptOnTheWebScreenWaitsForABrowserEndToEndTest {

    private static final Pattern THE_PAGES_ADDRESS = Pattern.compile("http://[^ ]+/");

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

    private String whatHasBeenPrinted() {
        synchronized (printed) {
            return printed.toString(StandardCharsets.UTF_8);
        }
    }

    private CompletableFuture<Integer> theCommandLineRunning(Path directory, String script) throws IOException {
        Path written = directory.resolve("on-the-page.r3");
        Files.writeString(written, script);
        PrintStream out = new PrintStream(printed, true, StandardCharsets.UTF_8);
        return CompletableFuture.supplyAsync(() -> Repl.runTheCommandLine(
                new String[] {"--screen=web", written.toString()}, out, out, directory.toString()));
    }

    private String theAddressOnceItIsPrinted() {
        while (true) {
            Matcher found = THE_PAGES_ADDRESS.matcher(whatHasBeenPrinted());
            if (found.find()) {
                return found.group();
            }
            Thread.onSpinWait();
        }
    }

    private InputStream aBrowserOpens(String address) throws Exception {
        InputStream pictures = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(address + "paint")).build(),
                HttpResponse.BodyHandlers.ofInputStream()).body();
        pictures.read();
        return pictures;
    }

    @Test
    @Timeout(30)
    @DisplayName("the script does not start until a browser has opened the page, then runs and finishes")
    void theScriptWaitsForABrowser(@TempDir Path directory) throws Exception {
        CompletableFuture<Integer> running = theCommandLineRunning(directory, """
                print {started}
                """);
        String address = theAddressOnceItIsPrinted();

        assertThat(whatHasBeenPrinted()).doesNotContain("started");
        assertThat(running).isNotDone();

        InputStream thePictures = aBrowserOpens(address);
        try {
            assertThat(running.get()).isZero();
        } finally {
            thePictures.close();
        }
        assertThat(whatHasBeenPrinted()).contains("started");
    }

    @Test
    @Timeout(30)
    @DisplayName("and a script that views a window, once the page is open, opens it rather than being refused")
    void aViewOnceThePageIsOpenSucceeds(@TempDir Path directory) throws Exception {
        CompletableFuture<Integer> running = theCommandLineRunning(directory, """
                view/no-wait make gob! [size: 100x100 color: 1.1.1]
                print [{showing} length? system/view/screen-gob]
                """);
        String address = theAddressOnceItIsPrinted();

        InputStream thePictures = aBrowserOpens(address);
        try {
            assertThat(running.get()).isZero();
        } finally {
            thePictures.close();
        }
        assertThat(whatHasBeenPrinted())
                .contains("showing 1")
                .doesNotContain("not present");
    }
}
