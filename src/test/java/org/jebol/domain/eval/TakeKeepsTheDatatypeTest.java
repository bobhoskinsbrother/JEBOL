package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAKE answers a series of the kind it took from.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Answering a block for everything loses the datatype silently, and the
 * loss shows up somewhere else entirely -- when the result is molded, or
 * appended to something that minds what kind it is.
 *
 * <p>The counts are the boundaries: zero, negative, exactly the length, and
 * past the end. All of them clamp rather than failing, because taking from
 * a series that has run out is an ordinary thing for a loop to do.
 */
class TakeKeepsTheDatatypeTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("taking from a paren gives a paren")
    void aParenStaysAParen() {
        assertThat(answerTo("mold take/part quote (1 2 3 4) 2")).isEqualTo("\"(1 2)\"");
    }

    @Test
    @DisplayName("taking from a binary gives a binary")
    void aBinaryStaysABinary() {
        assertThat(answerTo("mold take/part #{01020304} 2")).isEqualTo("\"#{0102}\"");
    }

    @Test
    @DisplayName("taking from a string gives a string")
    void aStringStaysAString() {
        assertThat(answerTo("take/part \"abcd\" 2")).isEqualTo("\"ab\"");
    }

    @Test
    @DisplayName("taking from a block gives a block")
    void aBlockStaysABlock() {
        assertThat(answerTo("mold take/part [1 2 3 4] 2")).isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("taking one item from a paren gives the item, not a paren")
    void takingOneGivesTheItemItself() {
        assertThat(answerTo("mold take quote (1 2 3 4)")).isEqualTo("\"1\"");
    }

    @Test
    @DisplayName("TAKE/LAST takes from the tail")
    void takeLastTakesFromTheEnd() {
        assertThat(answerTo("take/last [1 2 3]")).isEqualTo("3");
    }

    @Test
    @DisplayName("TAKE without /last takes from the head")
    void takeTakesFromTheHead() {
        assertThat(answerTo("take [1 2 3]")).isEqualTo("1");
    }

    @Test
    @DisplayName("a count of zero takes nothing")
    void zeroTakesNothing() {
        assertThat(answerTo("mold take/part [1 2 3] 0")).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("a negative count takes nothing at the head")
    void aNegativeCountTakesNothing() {
        assertThat(answerTo("mold take/part [1 2 3] -1")).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("a count past the end takes what there is")
    void anOversizedCountClamps() {
        assertThat(answerTo("mold take/part [1 2] 9")).isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("taking from an empty series gives none")
    void takingFromNothingGivesNone() {
        assertThat(answerTo("mold take []")).isEqualTo("\"_\"");
    }

    @Test
    @DisplayName("the series really loses what was taken")
    void whatIsTakenIsGone() {
        assertThat(answerTo("b: [1 2 3 4] take/part b 2 mold b")).isEqualTo("\"[3 4]\"");
        assertThat(answerTo("b: #{01020304} take/part b 2 mold b")).isEqualTo("\"#{0304}\"");
    }
}
