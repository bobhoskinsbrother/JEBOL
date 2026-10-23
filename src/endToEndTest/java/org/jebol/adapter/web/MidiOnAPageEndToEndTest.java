package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class MidiOnAPageEndToEndTest {

    private final PageBrowsers browsers;

    MidiOnAPageEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @Test
    @DisplayName("in Chrome with MIDI allowed and nothing plugged in, QUERY lists two empty blocks")
    void chromeListsNothingPluggedIn() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["in" mold query midi:// 'devices-in "out" mold query midi:// 'devices-out]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,"chrome", runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("in [] out []");
        }
    }

    @Test
    @DisplayName("in Chrome with MIDI denied, QUERY lists none, so a script can tell no from nothing")
    void chromeDeniedIsNone() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["in" mold query midi:// 'devices-in "out" mold query midi:// 'devices-out]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,"chrome", runner)
                     .whoseBrowserDenies("midi").opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("in _ out _");
        }
    }

    @Test
    @DisplayName("in Firefox a note sent to its test device comes back on its input, through the page")
    void firefoxSendsAndHearsANote() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["in" mold query midi:// 'devices-in]
                port: open midi://1/1
                write port #{90007F}
                heard: copy #{}
                loop 40 [
                    wait 0.05
                    append heard read port
                    if find heard #{90007F} [break]
                ]
                close port
                print ["heard" mold copy/part heard 3]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,"firefox", runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains(
                    """
                            in ["Test Control MIDI Device Input Port"]""",
                    "heard #{90007F}");
        }
    }
}
