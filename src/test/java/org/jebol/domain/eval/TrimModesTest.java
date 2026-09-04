package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRIM's five modes, which are not variations on each other.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Four of them were declared and never read, so each silently did what
 * the plain form does -- which is right often enough on a string with
 * nothing but leading and trailing spaces to look like it works.
 */
class TrimModesTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the plain form takes whitespace off both ends")
    void plainTrimsBothEnds() {
        assertThat(answerTo("trim copy \"  a b  \"")).isEqualTo("\"a b\"");
    }

    @Test
    @DisplayName("/all takes every space out, wherever it sits")
    void allTakesEverySpace() {
        assertThat(answerTo("trim/all copy \"  a b  \"")).isEqualTo("\"ab\"");
    }

    @Test
    @DisplayName("/lines folds the whole thing onto one line")
    void linesFoldsOntoOne() {
        assertThat(answerTo("trim/lines copy \"a^/b^/^/c\"")).isEqualTo("\"a b c\"");
    }

    @Test
    @DisplayName("/with takes out the characters it is given")
    void withTakesWhatItIsGiven() {
        assertThat(answerTo("trim/with copy \"aXbXc\" \"X\"")).isEqualTo("\"abc\"");
    }

    @Test
    @DisplayName("/auto keeps the shape of what is indented deeper")
    void autoKeepsRelativeIndentation() {
        assertThat(answerTo(
                "(trim/auto copy \"    a^/      b^/    c\") = \"a^/  b^/c\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/head and /tail each bound the plain form to one end")
    void headAndTailTakeOneEnd() {
        assertThat(answerTo("trim/head copy \" a \"")).isEqualTo("\"a \"");
        assertThat(answerTo("trim/tail copy \" a \"")).isEqualTo("\" a\"");
    }

    @Test
    @DisplayName("a string with nothing to trim is unchanged by every mode")
    void nothingToTrimIsTheDegenerateCase() {
        assertThat(answerTo("trim copy \"ab\"")).isEqualTo("\"ab\"");
        assertThat(answerTo("trim/all copy \"ab\"")).isEqualTo("\"ab\"");
        assertThat(answerTo("trim/lines copy \"ab\"")).isEqualTo("\"ab\"");
    }
}
