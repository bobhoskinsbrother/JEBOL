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

    private String formedIn(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a window refused for want of a grant says so, as no-service")
    void aRefusedWindowSaysWhy() {
        String formed = formedIn(Interpreter.withBounds(Bounds.standard()),
                "form try [view/no-wait make gob! [size: 10x10]]");

        assertThat(formed)
                .contains("** Access error: ")
                .contains("not granted")
                .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
    }

    @Test
    @DisplayName("a host call the bounds forbid says so, as host-access")
    void aForbiddenHostCallSaysWhy() {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard());
        interpreter.defineFunction("ask-the-host", 0, arguments -> 1);

        String formed = formedIn(interpreter, "form try [ask-the-host]");

        assertThat(formed)
                .contains("** Access error: ")
                .contains("may not call out to ask-the-host")
                .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
    }

    @Test
    @DisplayName("a host call that fails says how, as host-error")
    void aFailingHostCallSaysHow() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().withHostAccess(HostAccess.READING_AND_CALLING));
        interpreter.defineFunction("break-the-host", 0, arguments -> {
            throw new IllegalStateException("the host fell over");
        });

        String formed = formedIn(interpreter, "form try [break-the-host]");

        assertThat(formed)
                .contains("break-the-host failed: the host fell over")
                .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
    }

    @Test
    @DisplayName("an error a view raises outside any interpreter still says its own sentence")
    void aViewsErrorSaysItsSentence() {
        String formed = Molder.form(ErrorValue.of(ErrorCategory.SCRIPT, "no-such-action",
                "nothing on this view is called \"go\""));

        assertThat(formed)
                .contains("** Script error: ")
                .contains("nothing on this view is called")
                .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
    }

    @Test
    @DisplayName("an id Rebol names keeps its catalogue's wording")
    void anIdRebolNamesKeepsItsWording() {
        String formed = formedIn(Interpreter.create(), "form try [1 / 0]");

        assertThat(formed).contains("** Math error: attempt to divide by zero");
    }

    @Test
    @DisplayName("each of the four ids only JEBOL raises is worded, never left improperly formatted")
    void noneOfTheFourIsLeftImproperlyFormatted() {
        for (List<String> raised : List.of(
                List.of("ACCESS", "no-service"),
                List.of("ACCESS", "host-access"),
                List.of("USER", "host-error"),
                List.of("SCRIPT", "no-such-action"))) {
            String formed = Molder.form(ErrorValue.of(
                    ErrorCategory.valueOf(raised.getFirst()), raised.getLast(), "the sentence it was raised with"));

            assertThat(formed).as(raised.getLast())
                    .contains("the sentence it was raised with")
                    .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
        }
    }
}
