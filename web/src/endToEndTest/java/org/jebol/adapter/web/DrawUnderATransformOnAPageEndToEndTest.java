package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class DrawUnderATransformOnAPageEndToEndTest {

    private final PageBrowsers browsers;

    DrawUnderATransformOnAPageEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    private static String readTheScript() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                DrawUnderATransformOnAPageEndToEndTest.class.getResourceAsStream("/draw/under-a-transform.reb"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("on a page, an image, a clip, a conic gradient and text all follow the transform as R3's AGG does")
    void everythingFollowsTheTransform(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running(readTheScript());
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains(
                    "image under scale in an offset gob: 255.100.40.255",
                    "clip under scale: 0.255.0.255 255.255.255.255 255.255.255.255",
                    "conic under scale, both sides drawn: true true",
                    "vectorial text is scaled, raster text is not: true",
                    "raster text's offset goes through the transform: true");
        }
    }
}
