package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REDUCE/INTO, stepping a character, and rounding to nothing.
 *
 * <p>Each specified in {@code spec/natives.allium} and confirmed against a
 * real R3.
 *
 * <p>Zero is the interesting boundary for ROUND/TO: it is the one scale
 * that cannot mean "a multiple of this", and it means the nearest whole
 * number rather than a division by zero.
 */
class ReduceIntoAndSteppingTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("REDUCE/INTO fills a block the caller already has")
    void reduceIntoFillsAnExistingBlock() {
        assertThat(answerTo("b: [] reduce/into [1 + 1 2 + 2] b mold head b"))
                .isEqualTo("\"[2 4]\"");
    }

    @Test
    @DisplayName("REDUCE/INTO answers the position after what it put there")
    void reduceIntoAnswersThePositionAfter() {
        assertThat(answerTo("b: [] mold reduce/into [1 + 1] b")).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("REDUCE/INTO of nothing leaves the block alone")
    void reduceIntoNothingIsTheDegenerateCase() {
        assertThat(answerTo("b: [9] reduce/into [] b mold head b")).isEqualTo("\"[9]\"");
    }

    @Test
    @DisplayName("plain REDUCE still builds a new block")
    void plainReduceIsUnaffected() {
        assertThat(answerTo("mold reduce [1 + 1 2 + 2]")).isEqualTo("\"[2 4]\"");
    }

    @Test
    @DisplayName("++ steps a character to the next one")
    void incrementStepsACharacter() {
        assertThat(answerTo("a: #\"a\" reduce [++ a a]"))
                .isEqualTo("[#\"a\" #\"b\"]");
    }

    @Test
    @DisplayName("-- steps a character back")
    void decrementStepsACharacterBack() {
        assertThat(answerTo("a: #\"b\" reduce [-- a a]"))
                .isEqualTo("[#\"b\" #\"a\"]");
    }

    @Test
    @DisplayName("ROUND/TO with a scale of zero throws the fraction away")
    void roundToZeroGivesAWholeNumber() {
        assertThat(answerTo("mold reduce [round/to 11.65 0 round/to 11.4 0 "
                + "round/to -11.6 0]"))
                .isEqualTo("\"[11 11 -11]\"");
    }

    @Test
    @DisplayName("that whole number is an integer, not a decimal")
    void roundToZeroGivesAnInteger() {
        assertThat(answerTo("mold type? round/to 11.65 0")).isEqualTo("\"#(integer!)\"");
    }

    @Test
    @DisplayName("but a DECIMAL zero rounds nothing and keeps the fraction")
    void aDecimalZeroScaleRoundsNothing() {
        assertThat(answerTo("mold round/to 11.65 0.0")).isEqualTo("\"11.65\"");
        assertThat(answerTo("mold type? round/to 11.65 0.0"))
                .isEqualTo("\"#(decimal!)\"");
        assertThat(answerTo("mold round/to 11.65 1e-400")).isEqualTo("\"11.65\"");
    }

    @Test
    @DisplayName("an ordinary scale still means a multiple of that")
    void anOrdinaryScaleIsUnaffected() {
        assertThat(answerTo("mold reduce [round/to 1.234 0.01 round/to 17 5]"))
                .isEqualTo("\"[1.23 15]\"");
    }
}
