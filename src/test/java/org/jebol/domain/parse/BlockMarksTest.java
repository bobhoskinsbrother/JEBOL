package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("a get-word naming no place is refused rather than passed over")
    void anUnsetMarkIsRefused() {
        assertThat(answerTo("""
                e: try [parse [1 2] [:nowhere 2 skip]]
                reduce [e/id e/arg1]""")).isEqualTo("[parse-series :nowhere]");
    }

    @Test
    @DisplayName("a rule after a mark still changes the series")
    void aChangeAfterAMarkStillWorks() {
        assertThat(answerTo(
                "parse b: [1 2 3 4 5] [skip mark: change [2 skip] ('x) to end] b = [1 x 4 5]"))
                .isEqualTo("#(true)");
    }
}
