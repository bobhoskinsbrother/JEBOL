package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class GobsMadeIntoImagesOnAPageEndToEndTest {

    private final PageBrowsers browsers;

    GobsMadeIntoImagesOnAPageEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("a gob becomes an image of its own size, painted in the worker: its colour, its draw block, and white where nothing is")
    void paintsAGobIntoAnImage(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                coloured: to image! make gob! [size: 3x2 color: 10.20.30]
                print ["coloured" coloured/size mold coloured/1 mold coloured/6]
                drawn: make gob! [size: 4x2]
                drawn/draw: [pen off fill-pen 255.0.0 box 0x0 2x2]
                drawn: to image! drawn
                print ["drawn" drawn/size mold drawn/1 mold drawn/4]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains(
                    "coloured 3x2 10.20.30.255 10.20.30.255",
                    "drawn 4x2 255.0.0.255 255.255.255.255");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("a gob's pane is painted into its image at each child's offset, and a child is clipped to its parent")
    void paintsThePane(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                parent: make gob! [size: 4x4 color: 0.0.255]
                append parent make gob! [offset: 2x2 size: 8x8 color: 0.255.0]
                shot: to image! parent
                print ["pane" shot/size mold shot/1 mold pick shot 11 mold pick shot 16]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("pane 4x4 0.0.255.255 0.255.0.255 0.255.0.255");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("a gob's text is written into its image in the carried font, which the worker loads itself")
    void writesTextIntoTheImage(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                labelled: make gob! [size: 80x30]
                labelled/text: compose [font (make object! [size: 20 color: 0.0.0]) "MMM"]
                shot: to image! labelled
                inked: 0
                repeat at length? shot [if 128 > first pick shot at [inked: inked + 1]]
                print ["inked" inked > 40]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("inked true");
        }
    }
}
