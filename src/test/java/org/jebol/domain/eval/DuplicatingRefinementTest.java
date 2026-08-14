package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * /DUP, which repeats what is being added.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Its count had no parameter, so the number was never consumed: it
 * leaked out as the expression's own value and the operation happened
 * once. Every assertion here answered a bare number before.
 */
class DuplicatingRefinementTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("APPEND/DUP adds the value that many times")
    void appendRepeats() {
        assertThat(answerTo("mold append/dup copy [1] 2 3")).isEqualTo("\"[1 2 2 2]\"");
    }

    @Test
    @DisplayName("INSERT/DUP does too")
    void insertRepeats() {
        assertThat(answerTo("mold head insert/dup copy [1] 2 3"))
                .isEqualTo("\"[2 2 2 1]\"");
    }

    @Test
    @DisplayName("CHANGE/DUP replaces that many elements rather than one")
    void changeReplacesSeveral() {
        assertThat(answerTo("mold head change/dup copy [1 2 3 4] 9 3"))
                .isEqualTo("\"[9 9 9 4]\"");
    }

    @Test
    @DisplayName("a block being spliced repeats as a whole")
    void aBlockRepeatsWhole() {
        assertThat(answerTo("mold append/dup copy [1] [2 3] 2"))
                .isEqualTo("\"[1 2 3 2 3]\"");
    }

    @Test
    @DisplayName("a string takes the text that many times")
    void aStringRepeats() {
        assertThat(answerTo("append/dup copy \"a\" \"b\" 3")).isEqualTo("\"abbb\"");
    }

    @Test
    @DisplayName("a count of zero adds nothing")
    void zeroAddsNothing() {
        assertThat(answerTo("mold append/dup copy [1] 2 0")).isEqualTo("\"[1]\"");
    }

    @Test
    @DisplayName("a count of one is the same as no refinement at all")
    void oneIsTheOrdinaryCase() {
        assertThat(answerTo("mold append/dup copy [1] 2 1")).isEqualTo("\"[1 2]\"");
        assertThat(answerTo("mold append copy [1] 2")).isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("/part still finds its own argument when /dup is there too")
    void theTwoRefinementsDoNotCollide() {
        assertThat(answerTo("mold append/part copy [1] [2 3 4] 2"))
                .isEqualTo("\"[1 2 3]\"");
        assertThat(answerTo("mold append/dup copy [1] 2 2")).isEqualTo("\"[1 2 2]\"");
    }
}
