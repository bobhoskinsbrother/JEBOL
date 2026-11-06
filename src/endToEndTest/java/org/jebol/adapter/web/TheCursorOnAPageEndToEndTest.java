package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class TheCursorOnAPageEndToEndTest {

    private static final String THE_SCREENS_CURSOR = "return document.getElementById('screen').style.cursor;";

    private final PageBrowsers browsers;

    TheCursorOnAPageEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    private String cursorAfterRunning(String script) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running(script + """

                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {
            operator.waitUntilOutputContains("done");
            return String.valueOf(operator.runsInThePage(THE_SCREENS_CURSOR));
        }
    }

    @Test
    @DisplayName("a system id becomes the browser's cursor of the same meaning")
    void showsASystemCursor() throws IOException {
        assertThat(cursorAfterRunning("cursor 32649")).isEqualTo("pointer");
    }

    @Test
    @DisplayName("none hides the pointer, as SetCursor(NULL) does")
    void hidesThePointerForNone() throws IOException {
        assertThat(cursorAfterRunning("cursor none")).isEqualTo("none");
    }

    @Test
    @DisplayName("an image becomes a picture of itself, with its hot spot at the top left")
    void showsAnImage() throws IOException {
        assertThat(cursorAfterRunning("cursor make image! [2x2 255.0.0]"))
                .startsWith("url(")
                .contains("data:image/png;base64,", " 0 0");
    }

    @Test
    @DisplayName("and the last one asked for is the one that stays")
    void keepsTheLastCursor() throws IOException {
        assertThat(cursorAfterRunning("""
                cursor 32649
                cursor 32513""")).isEqualTo("text");
    }
}
