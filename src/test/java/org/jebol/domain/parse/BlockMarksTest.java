package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A set-word marks where a block parse has reached; a get-word goes back.
 *
 * <p>Specified in {@code spec/parse.allium} and measured against a real R3
 * 3.22.1.
 *
 * <p>The string walker had both and this one had neither, so {@code p:} in
 * a block rule was taken as a value to match and failed against whatever
 * happened to be there. Everything after it in the rule then failed too,
 * which made the mark look like it broke the rule rather than being
 * unimplemented.
 */
class BlockMarksTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a mark consumes nothing and lets the rule carry on")
    void aMarkIsTransparent() {
        assertThat(answerTo("parse [1 2 3] [skip mark: to end]")).isEqualTo("#(true)");
        assertThat(answerTo("parse [1 2 3] [skip to end]"))
                .as("the same rule without the mark")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a mark records where the parse had reached")
    void aMarkHoldsThePosition() {
        assertThat(answerTo("parse [1 2 3] [skip mark: to end] mark = [2 3]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a mark at the head and at the tail are both places")
    void theDegeneratePositions() {
        assertThat(answerTo("parse [1 2] [mark: to end] mark = [1 2]")).isEqualTo("#(true)");
        assertThat(answerTo("parse [1 2] [2 skip mark:] empty? mark")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a get-word goes back to what a mark recorded")
    void aGetWordSeeksBack() {
        assertThat(answerTo("parse [1 2 3] [mark: skip :mark 3 skip]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a get-word naming no place does not match")
    void anUnsetMarkIsNoMatch() {
        // No match rather than a failure, and above all not a position
        // left somewhere the walker cannot read.
        assertThat(answerTo("parse [1 2] [:nowhere 2 skip]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a rule after a mark still changes the series")
    void aChangeAfterAMarkStillWorks() {
        // The case that showed the gap: with the mark in, the whole rule
        // failed and the series came back untouched.
        assertThat(answerTo(
                "parse b: [1 2 3 4 5] [skip mark: change [2 skip] ('x) to end] b = [1 x 4 5]"))
                .isEqualTo("#(true)");
    }
}
