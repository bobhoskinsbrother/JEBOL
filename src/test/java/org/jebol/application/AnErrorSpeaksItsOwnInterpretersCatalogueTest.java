package org.jebol.application;

import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.ErrorWording;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnErrorSpeaksItsOwnInterpretersCatalogueTest {

    private static final String REWORD_DIVIDING_BY_ZERO = """
            unprotect/deep system/catalog/errors
            append system/catalog/errors/math/zero-divide { now}""";

    private static final String FORM_A_DIVISION_BY_ZERO = """
            form try [1 / 0]""";

    private static final String THE_CATALOGUE_SAYS = "attempt to divide by zero";

    private static final String THE_REWORDED_CATALOGUE_SAYS = "attempt to divide by zero now";

    private static String messageLineOf(String formed) {
        return formed.lines()
                .filter(line -> line.startsWith("** Math error: "))
                .findFirst()
                .orElseThrow()
                .substring("** Math error: ".length());
    }

    @Nested
    @DisplayName("an interpreter that rewords its catalogue changes only its own messages")
    class RewordingStaysAtHome {

        @Test
        @DisplayName("when the rewording interpreter was made first")
        void theRewordingOneMadeFirst() {
            Interpreter rewording = Interpreter.create();
            Interpreter untouched = Interpreter.create();
            rewording.run(REWORD_DIVIDING_BY_ZERO);

            assertThat(messageLineOf(rewording.run(FORM_A_DIVISION_BY_ZERO).display()))
                    .isEqualTo(THE_REWORDED_CATALOGUE_SAYS);
            assertThat(messageLineOf(untouched.run(FORM_A_DIVISION_BY_ZERO).display()))
                    .isEqualTo(THE_CATALOGUE_SAYS);
        }

        @Test
        @DisplayName("and when it was made last")
        void theRewordingOneMadeLast() {
            Interpreter untouched = Interpreter.create();
            Interpreter rewording = Interpreter.create();
            rewording.run(REWORD_DIVIDING_BY_ZERO);

            assertThat(messageLineOf(rewording.run(FORM_A_DIVISION_BY_ZERO).display()))
                    .isEqualTo(THE_REWORDED_CATALOGUE_SAYS);
            assertThat(messageLineOf(untouched.run(FORM_A_DIVISION_BY_ZERO).display()))
                    .isEqualTo(THE_CATALOGUE_SAYS);
        }

        @Test
        @DisplayName("an error caught before the rewording speaks the rewording when formed after it")
        void theCatalogueIsReadWhenFormingNotWhenRaising() {
            Interpreter interpreter = Interpreter.create();
            interpreter.run("caught: try [1 / 0]");
            interpreter.run(REWORD_DIVIDING_BY_ZERO);

            assertThat(messageLineOf(interpreter.run("form caught").display()))
                    .isEqualTo(THE_REWORDED_CATALOGUE_SAYS);
        }
    }

    @Nested
    @DisplayName("an error that was never raised still speaks its interpreter's catalogue")
    class NeverRaised {

        @Test
        @DisplayName("a made error forms from the catalogue")
        void aMadeErrorFormsFromTheCatalogue() {
            String formed = Interpreter.create().run("""
                    form make error! [type: 'math id: 'zero-divide]""").display();

            assertThat(formed).contains(THE_CATALOGUE_SAYS);
        }

        @Test
        @DisplayName("and from its own interpreter's rewording")
        void aMadeErrorFormsFromItsOwnRewording() {
            Interpreter rewording = Interpreter.create();
            Interpreter.create();
            rewording.run(REWORD_DIVIDING_BY_ZERO);

            String formed = rewording.run("""
                    form make error! [type: 'math id: 'zero-divide]""").display();

            assertThat(formed).contains(THE_REWORDED_CATALOGUE_SAYS);
        }
    }

    @Nested
    @DisplayName("an error built outside any interpreter has no catalogue to speak from")
    class OutsideAnyInterpreter {

        @Test
        @DisplayName("it forms as Rebol forms an id its catalogue lacks, whatever interpreters exist")
        void itFormsAsAnUnknownId() {
            Interpreter.create().run(REWORD_DIVIDING_BY_ZERO);

            String formed = Molder.form(
                    ErrorValue.of(ErrorCategory.MATH, "zero-divide", "attempt to divide by zero"));

            assertThat(formed).contains(ErrorWording.NOTHING_IN_THE_CATALOGUE);
        }
    }
}
