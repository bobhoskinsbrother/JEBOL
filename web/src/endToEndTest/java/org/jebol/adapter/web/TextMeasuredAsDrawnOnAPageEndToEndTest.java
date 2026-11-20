package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class TextMeasuredAsDrawnOnAPageEndToEndTest {

    private static final List<String> SHOWN = List.of(
            "The quick brown fox jumps over the lazy dog",
            "AVATAR To Wa Yo fi fl office",
            "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii",
            "a😀b",
            "😀😃😄");

    private static final String PRINT_HOW_FAR_EACH_CARET_IS_FROM_ITS_INK = """
            foreach shown [%s] [
                plain: make gob! [size: 600x40 text: shown]
                caret: caret-to-offset plain 1 1 + length? shown
                picture: to image! plain
                inked: 0
                repeat across picture/size/x [
                    repeat down picture/size/y [
                        seen: pick picture as-pair across down
                        if 230 > min min seen/1 seen/2 seen/3 [inked: across]
                    ]
                ]
                print ["caret" to integer! caret/x - inked "past the ink of" shown]
            ]
            print "done"
            """;

    private final PageBrowsers browsers;

    TextMeasuredAsDrawnOnAPageEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    private static String asRebolStrings(List<String> texts) {
        return String.join(" ", texts.stream().map(text -> "{" + text + "}").toList());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("the caret after a text is where the browser's ink for it ends, kerning and emoji included")
    void theCaretAfterTheTextIsWhereItsInkEnds(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running(
                PRINT_HOW_FAR_EACH_CARET_IS_FROM_ITS_INK.formatted(asRebolStrings(SHOWN)));
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {

            operator.waitUntilOutputContains("done");
            List<String> answered = operator.readOutput().stream().filter(line -> line.startsWith("caret ")).toList();
            assertThat(answered).hasSize(SHOWN.size());
            assertThat(answered).allSatisfy(line -> assertThat(Integer.parseInt(line.split(" ")[1]))
                    .as(line).isBetween(-1, 2));
        }
    }
}
