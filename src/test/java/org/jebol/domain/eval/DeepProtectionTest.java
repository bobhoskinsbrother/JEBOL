package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * /DEEP reaches everything inside, including an object inside an object.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Stopping at the outer object leaves free exactly the case a caller
 * reached for /DEEP to cover, and it does so quietly -- the shallow guard
 * still refuses the shallow write, so the refinement looks like it works.
 */
class DeepProtectionTest {

    private static String errorIdOf(String setup, String attempt) {
        String source = setup + " e: try [" + attempt + "] "
                + "either error? e [e/id] ['no-error]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String NESTED =
            "o: make object! [b: make object! [c: 3]] ignore: protect/deep o ";

    @Test
    @DisplayName("a nested object's field is protected too")
    void deepReachesANestedObject() {
        assertThat(errorIdOf(NESTED, "o/b/c: 4")).isEqualTo("locked-word");
    }

    @Test
    @DisplayName("the outer object is still protected as well")
    void theOuterObjectIsStillGuarded() {
        assertThat(errorIdOf(NESTED, "o/b: 9")).isEqualTo("locked-word");
    }

    @Test
    @DisplayName("without /deep only the outer object is protected")
    void shallowProtectionStopsAtTheOuterObject() {
        assertThat(errorIdOf("o: make object! [b: make object! [c: 3]] ignore: protect o ",
                "o/b/c: 4")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("a series inside an object is reached too")
    void deepReachesASeriesField() {
        assertThat(errorIdOf("o: make object! [b: \"abc\"] ignore: protect/deep o ",
                "insert o/b \"x\"")).isEqualTo("protected");
    }

    @Test
    @DisplayName("/words without /deep guards only the name")
    void wordsAloneGuardsOnlyTheName() {
        String setup = "a: make object! [b: \"abc\"] ignore: protect/words [a] ";
        assertThat(errorIdOf(setup, "a: 9")).isEqualTo("locked-word");
        assertThat(errorIdOf(setup, "insert a/b \"x\"")).isEqualTo("no-error");
    }

    private static String allowedAfter(String unprotecting) {
        String setup = "o: " + unprotecting
                + " protect/deep o: make object! [a: 10 b: [20]] ";
        return errorIdOf(setup, "append o 'd") + " "
                + errorIdOf(setup, "o/a: 0") + " "
                + errorIdOf(setup, "append o/b 0");
    }

    @Test
    @DisplayName("the four UNPROTECT shapes release three different things")
    void theRefinementsSeparateTheThreeThings() {
        assertThat(allowedAfter("unprotect"))
                .as("frees the object and its words, not the values")
                .isEqualTo("no-error no-error protected");
        assertThat(allowedAfter("unprotect/words"))
                .as("frees the words only")
                .isEqualTo("protected no-error protected");
        assertThat(allowedAfter("unprotect/deep"))
                .as("frees all three")
                .isEqualTo("no-error no-error no-error");
        assertThat(allowedAfter("unprotect/words/deep"))
                .as("frees the words and the values, not the object")
                .isEqualTo("protected no-error no-error");
    }

    @Test
    @DisplayName("UNPROTECT/DEEP releases the whole of it again")
    void unprotectingDeeplyReleasesEverything() {
        assertThat(errorIdOf(NESTED + "ignore: unprotect/deep o ", "o/b/c: 4"))
                .isEqualTo("no-error");
    }
}
