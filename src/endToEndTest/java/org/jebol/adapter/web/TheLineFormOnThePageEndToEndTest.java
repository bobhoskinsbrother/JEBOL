package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.Keys;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class TheLineFormOnThePageEndToEndTest {

    private final PageBrowsers browsers;

    TheLineFormOnThePageEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("INPUT on the shipped page draws a line to type into, takes the operator's keys, and answers the line")
    void inputTakesALineTypedIntoTheForm(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["got" input]
                print ["edited" input]
                print ["declined" mold input]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,browserName, runner).opensThePage()) {

            operator.types("café line");
            operator.pressesEnter();
            operator.waitUntilOutputContains("got café line");
            operator.types("abcx", Keys.BACK_SPACE);
            operator.types("d");
            operator.pressesEnter();
            operator.waitUntilOutputContains("edited abcd");
            operator.pressesEscape();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("declined _");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("ASK prints its question to the page's output and reads the answer through the form")
    void askReadsThroughTheForm(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                name: ask "Name? "
                print ["hello" name]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,browserName, runner).opensThePage()) {

            operator.waitUntilOutputHas("Name?");
            operator.waitUntilTypingIsNamed("Name?");
            assertThat(operator.isOutputInView()).isTrue();
            operator.types("Ada");
            operator.pressesEnter();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).anyMatch(line -> line.endsWith("hello Ada"));
        }
    }

    @Test
    @DisplayName("text an input method composes reaches the form as the characters it settled on")
    void composedTextArrivesAsItsCharacters() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["got" input]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,"chrome", runner).opensThePage()) {

            operator.types("");
            operator.composes("漢字");
            operator.pressesEnter();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("got 漢字");
        }
    }
}
