package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Functions ported from a real R3 because they were missing, not because
 * anything failed.
 *
 * <p>Found by comparing JEBOL's whole library against the binary's, and
 * their definitions read out of the binary with BODY-OF. Specified in
 * {@code spec/natives.allium} and measured against R3 3.22.1.
 */
class PortedLibraryFunctionsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("FRACTION keeps the sign of what it came from")
    void fractionIsSigned() {
        // The fraction of -1.25 is -0.25 and not 0.75, which is the one
        // that goes the other way if you reach for a modulus.
        assertThat(answerTo("(fraction 1.25) = 0.25")).isEqualTo("#(true)");
        assertThat(answerTo("(fraction -1.25) = -0.25")).isEqualTo("#(true)");
        assertThat(answerTo("(fraction 2.0) = 0.0")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("DEFAULT sets a word only when it has not got a value")
    void defaultFillsTheGap() {
        assertThat(answerTo("unset 'q default q 5 q")).isEqualTo("5");
        assertThat(answerTo("q: 1 default q 5 q"))
                .as("a word that already has one is left alone")
                .isEqualTo("1");
        assertThat(answerTo("q: none default q 5 q"))
                .as("none counts as not having one")
                .isEqualTo("5");
    }

    @Test
    @DisplayName("DEFAULT answers the default whether or not it was needed")
    void defaultAlwaysAnswersTheDefault() {
        // It reads oddly and it is what a real R3 does.
        assertThat(answerTo("q: 1 default q 5")).isEqualTo("5");
    }

    @Test
    @DisplayName("HAS makes a function with locals and no arguments")
    void hasTakesNoArguments() {
        assertThat(answerTo("f: has [x] [x: 3 x] f")).isEqualTo("3");
        assertThat(answerTo("x: 1 f: has [x] [x: 3] f x"))
                .as("its locals do not reach outside")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("ALL-OF answers true only when every value passes")
    void allOfNeedsThemAll() {
        assertThat(answerTo("all-of v [1 2 3] [v > 0]")).isEqualTo("#(true)");
        assertThat(answerTo("none? all-of v [1 2 3] [v > 1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ANY-OF answers the first value that passes")
    void anyOfAnswersTheValue() {
        // The value and not true, which is what makes it worth having.
        assertThat(answerTo("(any-of v [1 2 3] [v > 2]) = 3")).isEqualTo("#(true)");
        assertThat(answerTo("none? any-of v [1 2 3] [v > 9]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("both answer none for none, and neither walks it")
    void theDegenerateInputIsNone() {
        assertThat(answerTo("none? all-of v none [true]")).isEqualTo("#(true)");
        assertThat(answerTo("none? any-of v none [true]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty series passes ALL-OF and fails ANY-OF")
    void theEmptySeriesGoesBothWays() {
        // Nothing failed, so ALL-OF holds; nothing passed, so ANY-OF
        // does not. The pair is the only way to pin that.
        assertThat(answerTo("all-of v [] [false]")).isEqualTo("#(true)");
        assertThat(answerTo("none? any-of v [] [true]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FORSKIP evaluates the body every nth position")
    void forskipStepsBySize() {
        assertThat(answerTo("b: [1 2 3 4] r: copy [] forskip b 2 [append r first b] r = [1 3]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FORSKIP puts the word back where it found it")
    void forskipRestoresTheWord() {
        assertThat(answerTo("b: [1 2 3 4] forskip b 2 [] b = [1 2 3 4]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TO PAREN! makes a paren from a block")
    void aParenCanBeMade() {
        // Needed by ALL-OF and ANY-OF, which build their loop body with
        // the caller's test inside it.
        assertThat(answerTo("paren? to paren! [1 + 1]")).isEqualTo("#(true)");
        assertThat(answerTo("(to paren! [1 2]) = quote (1 2)")).isEqualTo("#(true)");
    }
}
