package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A construct that names a series and where in it to stand.
 *
 * <p>Specified in {@code spec/load.allium}, confirmed against a real R3.
 *
 * <p>It is how MOLD/ALL writes a series that was not at its head. Without
 * reading the position back, such a mold does not round-trip: the value
 * comes back at its head and the position is silently lost.
 *
 * <p>The boundaries are the positions: the head, one along, the tail, and
 * past either end.
 */
class ConstructWithPositionTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a string construct stands where it was told to")
    void aStringStandsAtItsPosition() {
        assertThat(answerTo("load {#(string! \"ab\" 2)}")).isEqualTo("\"b\"");
    }

    @Test
    @DisplayName("position one is the head, which is the same as no position")
    void positionOneIsTheHead() {
        assertThat(answerTo("load {#(string! \"ab\" 1)}")).isEqualTo("\"ab\"");
        assertThat(answerTo("load {#(string! \"ab\")}")).isEqualTo("\"ab\"");
    }

    @Test
    @DisplayName("a file construct takes a position too")
    void aFileTakesAPosition() {
        assertThat(answerTo("mold load {#(file! \"ab\" 2)}")).isEqualTo("\"%b\"");
    }

    @Test
    @DisplayName("a block construct stands where it was told to")
    void aBlockStandsAtItsPosition() {
        assertThat(answerTo("mold load {#(block! [1 2 3] 2)}")).isEqualTo("\"[2 3]\"");
    }

    @Test
    @DisplayName("the block family converts within itself")
    void theBlockFamilyConverts() {
        assertThat(answerTo("mold load {#(paren! [1 2])}")).isEqualTo("\"(1 2)\"");
    }

    @Test
    @DisplayName("a construct naming a block and holding something else is refused")
    void aWrongContentIsRefused() {
        // Not a block of one item. Guessing here would make a typo read
        // as something plausible.
        assertThat(answerTo("e: try [load {#(block! 1)}] either error? e [e/id] ['no-error]"))
                .isEqualTo("malconstruct");
    }

    @Test
    @DisplayName("a position past the tail clamps rather than failing")
    void aPositionPastTheEndClamps() {
        assertThat(answerTo("load {#(string! \"ab\" 9)}")).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("a position below the head clamps to the head")
    void aPositionBelowTheHeadClamps() {
        assertThat(answerTo("load {#(string! \"ab\" 0)}")).isEqualTo("\"ab\"");
    }
}
