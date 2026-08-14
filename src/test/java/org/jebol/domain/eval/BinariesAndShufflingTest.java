package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RANDOM shuffling in place, and INSERT taking a binary.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Shuffling a copy agrees on the datatype and disagrees on both things
 * that matter: the caller's own series is left untouched, and a protected
 * one is shuffled rather than refusing.
 */
class BinariesAndShufflingTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("INSERT puts a binary into a binary byte by byte")
    void aBinaryGoesInByteByByte() {
        assertThat(answerTo("b: #{0102} insert b #{03} mold head b"))
                .isEqualTo("\"#{030102}\"");
    }

    @Test
    @DisplayName("INSERT still takes a single byte as a number")
    void aNumberStillGoesIn() {
        assertThat(answerTo("b: #{0102} insert b 3 mold head b"))
                .isEqualTo("\"#{030102}\"");
    }

    @Test
    @DisplayName("RANDOM answers a series of the kind it was given")
    void randomKeepsTheDatatype() {
        assertThat(answerTo("mold reduce [type? random \"abc\" type? random #{010203} "
                + "type? random [1 2 3]]"))
                .isEqualTo("\"[#(string!) #(binary!) #(block!)]\"");
    }

    @Test
    @DisplayName("RANDOM shuffles the series the caller holds")
    void randomShufflesInPlace() {
        assertThat(answerTo("s: \"abc\" t: s random s same? s t")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("RANDOM leaves the same characters behind")
    void randomKeepsTheContents() {
        assertThat(answerTo("s: \"abc\" random s (length? s) = 3")).isEqualTo("#(true)");
        assertThat(answerTo("s: \"aaa\" random s s")).isEqualTo("\"aaa\"");
    }

    @Test
    @DisplayName("a protected series refuses to be shuffled")
    void aProtectedSeriesRefusesToShuffle() {
        assertThat(errorIdOf("s: protect \"abc\" random s")).isEqualTo("protected");
        assertThat(errorIdOf("b: protect #{010203} random b")).isEqualTo("protected");
        assertThat(errorIdOf("k: protect [1 2 3] random k")).isEqualTo("protected");
    }

    @Test
    @DisplayName("a protected binary refuses an insert")
    void aProtectedBinaryRefusesInsert() {
        assertThat(errorIdOf("b: protect #{0102} insert b #{03}")).isEqualTo("protected");
    }

    @Test
    @DisplayName("shuffling an empty series is the degenerate case")
    void anEmptySeriesShufflesToItself() {
        assertThat(answerTo("s: \"\" random s s")).isEqualTo("\"\"");
        assertThat(answerTo("mold random #{}")).isEqualTo("\"#{}\"");
    }
}
