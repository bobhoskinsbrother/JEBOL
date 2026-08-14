package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Searching a binary for a number no byte could hold.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 * A byte holds 0 to 255, so asking for anything outside that is a mistake
 * in the caller rather than a search that happened to miss, and answering
 * none would hide it.
 *
 * <p>The boundaries are the ends of a byte: one below, the bottom, the top,
 * and one above.
 */
class BinarySearchBoundsTest {

    private static String errorIdOf(String source) {
        String probe = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(probe);
        ScriptOutcome outcome = interpreter.run(probe);
        assertThat(outcome.conclusion())
                .as("%s must not escape as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return interpreter.display(outcome);
    }

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("one below the smallest byte raises")
    void minusOneRaises() {
        assertThat(errorIdOf("find #{0063} -1")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("one above the largest byte raises")
    void twoFiftySixRaises() {
        assertThat(errorIdOf("find #{0063} 256")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("well past the largest byte raises")
    void sevenHundredRaises() {
        assertThat(errorIdOf("find/tail #{0063} 700")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("SELECT raises on the same input")
    void selectRaisesToo() {
        assertThat(errorIdOf("select #{0063} 700")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("the smallest byte is an ordinary search")
    void zeroIsOrdinary() {
        assertThat(answerTo("mold find #{0063} 0")).isEqualTo("\"#{0063}\"");
    }

    @Test
    @DisplayName("the largest byte is inside the range and is not refused")
    void twoFiftyFiveIsNotRefused() {
        assertThat(errorIdOf("find #{00FF} 255")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("a byte in the middle is found where it sits")
    void anOrdinaryByteIsFound() {
        assertThat(answerTo("mold find #{0063} 99")).isEqualTo("\"#{63}\"");
    }

    @Test
    @DisplayName("a byte that is not there answers none")
    void aMissAnswersNone() {
        assertThat(answerTo("mold find #{0063} 7")).isEqualTo("\"_\"");
    }
}
