package org.jebol.application;

import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.ErrorWording;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnIdOnlyJebolRaisesIsWordedByItsOwnSentenceTest {

    private String formIn(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a window refused for want of a grant says so, as no-service")
    void saysWhyAWindowWasRefused() {
        String formed = formIn(AnInterpreter.withBounds(Bounds.standard()),
                "form try [view/no-wait make gob! [size: 10x10]]");

        assertThat(formed)
                .contains("** Access error: ")
                .contains("not granted")
                .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
    }

    @Test
    @DisplayName("a command whose extension fails says how, in the catalogue's words for command-fail")
    void saysHowACommandFailed() {
        Extension breaking = new Extension("breaking", "1.0.0", List.of(new ExtensionCommand(
                "break-the-host", "", call -> {
                    throw new IllegalStateException("the host fell over");
                })));

        String formed = formIn(AnInterpreter.withBounds(Bounds.standard().add(breaking)),
                "form try [break-the-host]");

        assertThat(formed)
                .contains("""
                        ** Command error: "the host fell over"
                        """)
                .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
    }

    @Test
    @DisplayName("an error a view raises outside any interpreter still says its own sentence")
    void saysAViewErrorsSentence() {
        String formed = Molder.form(ErrorValue.of(ErrorCategory.SCRIPT, "no-such-action",
                "nothing on this view is called \"go\""));

        assertThat(formed)
                .contains("** Script error: ")
                .contains("nothing on this view is called")
                .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
    }

    @Test
    @DisplayName("an id Rebol names keeps its catalogue's wording")
    void keepsTheWordingOfAnIdRebolNames() {
        String formed = formIn(AnInterpreter.create(), "form try [1 / 0]");

        assertThat(formed).contains("** Math error: attempt to divide by zero");
    }

    @Test
    @DisplayName("each of the two ids only JEBOL raises is worded, never left improperly formatted")
    void leavesNeitherImproperlyFormatted() {
        for (List<String> raised : List.of(
                List.of("ACCESS", "no-service"),
                List.of("SCRIPT", "no-such-action"))) {
            String formed = Molder.form(ErrorValue.of(
                    ErrorCategory.valueOf(raised.getFirst()), raised.getLast(), "the sentence it was raised with"));

            assertThat(formed).as(raised.getLast())
                    .contains("the sentence it was raised with")
                    .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
        }
    }
}
