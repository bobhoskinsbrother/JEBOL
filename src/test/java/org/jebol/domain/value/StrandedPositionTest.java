package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A series value left standing past the end of its own storage.
 *
 * <p>Specified in {@code spec/values.allium}, confirmed against a real R3.
 *
 * <p>Two values can share storage at different positions, so shortening it
 * through one strands the other. That is an ordinary state -- PAST? exists
 * to ask about it -- and every operation has to cope. JEBOL threw a Java
 * exception straight out of the interpreter instead.
 */
class StrandedPositionTest {

    private static final String STRANDED =
            "a: [1 2 3 4] b: skip a 3 ignore: remove/part a 3 ";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("%s must not escape as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return interpreter.display(outcome);
    }

    @Test
    @DisplayName("a stranded block reads as empty")
    void aStrandedBlockIsEmpty() {
        assertThat(answerTo(STRANDED + "mold b")).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("it answers true to EMPTY? and TAIL?")
    void itIsEmptyAndAtTheTail() {
        assertThat(answerTo(STRANDED + "empty? b")).isEqualTo("#(true)");
        assertThat(answerTo(STRANDED + "tail? b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("PAST? is what tells it apart from an ordinary tail")
    void pastIsTheDistinguishingQuestion() {
        assertThat(answerTo(STRANDED + "past? b")).isEqualTo("#(true)");
        assertThat(answerTo("past? tail [1 2 3]"))
                .as("a value at the tail is not past it")
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("an ordinary position is never past the end")
    void ordinaryPositionsAreNotPast() {
        assertThat(answerTo("past? [1 2 3]")).isEqualTo("#(false)");
        assertThat(answerTo("past? skip [1 2 3] 5"))
                .as("SKIP clamps, so it cannot strand anything")
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a stranded string behaves the same way")
    void aStrandedStringIsEmptyToo() {
        String stranded = "s: \"1234\" t: skip s 3 ignore: remove/part s 3 ";
        assertThat(answerTo(stranded + "t")).isEqualTo("\"\"");
        assertThat(answerTo(stranded + "tail? t")).isEqualTo("#(true)");
        assertThat(answerTo(stranded + "past? t")).isEqualTo("#(true)");
    }
}
