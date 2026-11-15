package org.jebol.adapter.cli;

import org.jebol.adapter.web.AnOperatorAtThePage;
import org.jebol.adapter.web.PageBrowsers;
import org.jebol.adapter.web.TheSuitesPageBrowsers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(180)
@ExtendWith(TheSuitesPageBrowsers.class)
class TheCursorFromTheCommandLineEndToEndTest {

    private static final Pattern PAGES_ADDRESS = Pattern.compile("http://[^ ]+/");

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

    @TempDir
    Path directory;

    private final PageBrowsers browsers;

    TheCursorFromTheCommandLineEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    private String readPrinted() {
        synchronized (printed) {
            return printed.toString(StandardCharsets.UTF_8);
        }
    }

    private CompletableFuture<Integer> startCommandLine(String script) throws IOException {
        Path written = directory.resolve("cursor.r3");
        Files.writeString(written, script);
        PrintStream out = new PrintStream(printed, true, StandardCharsets.UTF_8);
        return CompletableFuture.supplyAsync(() -> Repl.runCommandLine(
                new String[] {"--screen=web", written.toString()}, out, out, directory.toString()));
    }

    private String awaitAddress() {
        while (true) {
            Matcher found = PAGES_ADDRESS.matcher(readPrinted());
            if (found.find()) {
                return found.group();
            }
            Thread.onSpinWait();
        }
    }

    @Test
    @DisplayName("with --screen=web a script's cursor reaches the served page")
    void reachesTheServedPage() throws Exception {
        CompletableFuture<Integer> running = startCommandLine("""
                Rebol []
                cursor 32649
                request-dir
                """);

        try (AnOperatorAtThePage operator =
                     new AnOperatorAtThePage(browsers, "chrome", awaitAddress()).opensThePage()) {
            operator.waitForThePromptSaying("folder");
            assertThat(operator.readTheAttributeOf("screen", "style")).contains("cursor: pointer");
            operator.declines();

            assertThat(running.get(60, TimeUnit.SECONDS)).isZero();
        } catch (AssertionError stuck) {
            throw new AssertionError(stuck.getMessage() + "; the command line printed: " + readPrinted(), stuck);
        }
    }
}
