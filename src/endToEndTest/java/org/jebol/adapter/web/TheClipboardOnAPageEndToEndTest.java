package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheClipboardOnAPageEndToEndTest {

    @TempDir
    Path downloads;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("a script writes the clipboard, and reads it back behind the prompt")
    void writesAndReadsTheClipboard(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["wrote" port? write clipboard:// "copied from a page"]
                print ["read" read clipboard://]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browserName, runner, downloads).opensThePage()) {

            operator.waitForThePromptSaying("copied");
            operator.accepts();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("wrote true", "read copied from a page");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("an operator who dismisses the paste prompt has declined, and the read is none")
    void dismissingTheReadIsNone(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["read" mold read clipboard://]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browserName, runner, downloads).opensThePage()) {

            operator.waitForThePromptSaying("copied");
            operator.declines();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("read _");
        }
    }

    @Test
    @DisplayName("a clipboard permission the operator denied in Chrome answers none for a read")
    void aDeniedPermissionIsNone() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["read" mold read clipboard://]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage("chrome", runner, downloads)
                     .whoseBrowserDenies("clipboard-read").opensThePage()) {

            operator.waitForThePromptSaying("copied");
            operator.accepts();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("read _");
        }
    }
}
