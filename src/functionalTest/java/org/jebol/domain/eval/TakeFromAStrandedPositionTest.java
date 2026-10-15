package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TakeFromAStrandedPositionTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("TAKE/ALL from a stranded block answers an empty block")
    void aStrandedBlockTakesNothing() {
        assertThat(answerTo("s: next [1 2] clear head s (take/all s) = []"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TAKE/ALL from a stranded string answers an empty string")
    void aStrandedStringTakesNothing() {
        assertThat(answerTo("s: next \"12\" clear head s (take/all s) = \"\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TAKE/ALL from a stranded binary answers an empty binary")
    void aStrandedBinaryTakesNothing() {
        assertThat(answerTo("s: next #{0102} clear head s empty? take/all s"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the series is left empty and at its head")
    void theSeriesItselfIsUnharmed() {
        assertThat(answerTo("s: next [1 2] clear head s take/all s (head s) = []"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TAKE/ALL from a series that still has items is unaffected")
    void theOrdinaryCaseStillWorks() {
        assertThat(answerTo("s: copy [1 2] (take/all s) = [1 2]")).isEqualTo("#(true)");
        assertThat(answerTo("s: skip copy [1 2 3] 1 (take/all s) = [2 3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TAKE/ALL from an empty series answers an empty one")
    void theDegenerateSeriesIsEmpty() {
        assertThat(answerTo("s: copy [] (take/all s) = []")).isEqualTo("#(true)");
        assertThat(answerTo("s: tail copy [1 2] (take/all s) = []")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a plain TAKE from a stranded position answers none")
    void takingOneFromNowhereIsNone() {
        assertThat(answerTo("s: next [1 2] clear head s none? take s")).isEqualTo("#(true)");
    }
}
