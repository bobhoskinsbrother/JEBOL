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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(180)
class TheCommandLinesPageAsksEndToEndTest {

    private static final Pattern PAGES_ADDRESS = Pattern.compile("http://[^ ]+/");

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

    private CompletableFuture<Integer> startCommandLine(String script) throws IOException {
        Path written = directory.resolve("asks.r3");
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
    @DisplayName("with --screen=web the page's own controls answer the dialogs: a colour, a file and a password typed into JEBOL's form")
    void thePageAnswersTheDialogs() throws Exception {
        Path notes = directory.resolve("notes.txt");
        Files.writeString(notes, "picked on the page");
        CompletableFuture<Integer> running = startCommandLine("""
                Rebol []
                print ["colour" mold request-color]
                chosen: request-file
                print ["file" read/string chosen]
                print ["secret" request-password]
                print ["declined" mold request-dir]
                """);

        try (AnOperatorAtThePage operator =
                     new AnOperatorAtThePage("chrome", awaitAddress(), downloads).opensThePage()) {
            operator.waitForThePromptSaying("colour");
            operator.choosesColour("#102030");
            operator.waitForThePromptSaying("file");
            operator.choosesFiles(notes);
            operator.types("s3cret");
            operator.pressesEnter();
            operator.waitForThePromptSaying("folder");
            operator.declines();

            assertThat(running.get(60, TimeUnit.SECONDS)).isZero();
        } catch (AssertionError stuck) {
            throw new AssertionError(stuck.getMessage() + "; the command line printed: " + readPrinted(), stuck);
        }
        assertThat(readPrinted()).contains(
                "colour 16.32.48", "file picked on the page", "secret s3cret", "declined _");
    }
}
