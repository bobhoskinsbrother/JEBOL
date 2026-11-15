package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class ASitePrintsToTheBrowsersConsoleEndToEndTest {

    private final PageBrowsers browsers;

    ASitePrintsToTheBrowsersConsoleEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @Test
    @DisplayName("each finished line is one console.log, and a part line waits for the print that finishes it")
    void logsEachFinishedLine() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print "one"
                prin "two "
                prin "and"
                print " a half"
                print "three^/four"
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput())
                    .containsSubsequence("one", "two and a half", "three", "four", "done")
                    .doesNotContain("two ", "and");
        }
    }

    @Test
    @DisplayName("a part line left when the run ends is logged then")
    void logsAPartLineAtTheEnd() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print "started"
                prin "left unfinished"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {

            operator.waitUntilOutputHas("left unfinished");
            assertThat(operator.readOutput()).contains("started", "left unfinished");
        }
    }

    @Test
    @DisplayName("with error on, lines go to console.error, and back to console.log once it is off")
    void sendsLinesToConsoleErrorWhileErrorIsOn() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print "ordinary"
                modify system/ports/output 'error true
                print "complained"
                modify system/ports/output 'error false
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("ordinary", "done").doesNotContain("complained");
            assertThat(operator.readErrorOutput()).contains("complained");
        }
    }

    @Test
    @DisplayName("the page's start-up timings go to the console, and nothing printed is drawn on the page")
    void drawsNothingPrinted() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).anyMatch(line -> line.startsWith("booted in "));
            assertThat(operator.readOutput()).anyMatch(line -> line.startsWith("worker loaded in "));
            assertThat(operator.showsAnOutputPanel()).isFalse();
        }
    }
}
