package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BinariesAndShufflingTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("INSERT puts a binary into a binary byte by byte")
    void putsABinaryInByteByByte() {
        assertThat(answerTo("b: #{0102} insert b #{03} mold head b"))
                .isEqualTo("\"#{030102}\"");
    }

    @Test
    @DisplayName("INSERT still takes a single byte as a number")
    void stillPutsANumberIn() {
        assertThat(answerTo("b: #{0102} insert b 3 mold head b"))
                .isEqualTo("\"#{030102}\"");
    }

    @Test
    @DisplayName("RANDOM answers a series of the type it was given")
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
    void refusesToShuffleAProtectedSeries() {
        assertThat(readTheErrorIdOf("s: protect \"abc\" random s")).isEqualTo("protected");
        assertThat(readTheErrorIdOf("b: protect #{010203} random b")).isEqualTo("protected");
        assertThat(readTheErrorIdOf("k: protect [1 2 3] random k")).isEqualTo("protected");
    }

    @Test
    @DisplayName("a protected binary refuses an insert")
    void refusesInsertIntoAProtectedBinary() {
        assertThat(readTheErrorIdOf("b: protect #{0102} insert b #{03}")).isEqualTo("protected");
    }

    @Test
    @DisplayName("shuffling an empty series is the degenerate case")
    void shufflesAnEmptySeriesToItself() {
        assertThat(answerTo("s: \"\" random s s")).isEqualTo("\"\"");
        assertThat(answerTo("mold random #{}")).isEqualTo("\"#{}\"");
    }
}
