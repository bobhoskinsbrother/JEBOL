package org.jebol.domain.eval;

import org.jebol.application.Conclusion;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BinarySearchBoundsTest {

    private static String readTheErrorIdOf(String source) {
        String probe = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(probe);
        ScriptOutcome outcome = interpreter.run(probe);
        assertThat(outcome.conclusion())
                .as("%s must not escape as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_VALUE);
        return interpreter.display(outcome);
    }

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("one below the smallest byte raises")
    void raisesForMinusOne() {
        assertThat(readTheErrorIdOf("find #{0063} -1")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("one above the largest byte raises")
    void raisesForTwoFiftySix() {
        assertThat(readTheErrorIdOf("find #{0063} 256")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("well past the largest byte raises")
    void raisesForSevenHundred() {
        assertThat(readTheErrorIdOf("find/tail #{0063} 700")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("SELECT raises on the same input")
    void selectRaisesToo() {
        assertThat(readTheErrorIdOf("select #{0063} 700")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("the smallest byte is an ordinary search")
    void zeroIsOrdinary() {
        assertThat(answerTo("mold find #{0063} 0")).isEqualTo("\"#{0063}\"");
    }

    @Test
    @DisplayName("the largest byte is inside the range and is not refused")
    void acceptsTwoFiftyFive() {
        assertThat(readTheErrorIdOf("find #{00FF} 255")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("a byte in the middle is found where it sits")
    void findsAnOrdinaryByte() {
        assertThat(answerTo("mold find #{0063} 99")).isEqualTo("\"#{63}\"");
    }

    @Test
    @DisplayName("a byte that is not there answers none")
    void answersNoneForAMiss() {
        assertThat(answerTo("mold find #{0063} 7")).isEqualTo("\"_\"");
    }

    @ParameterizedTest(name = "find {0} answers {1}")
    @CsvSource({
            "#{0080} 128, #{80}",
            "#{00C8} 200, #{C8}",
            "#{00FE} 254, #{FE}",
            "#{00FF} 255, #{FF}",
            "#{007F} 127, #{7F}",
            "#{0001} 1, #{01}",
            "#{00FF} 0, #{00FF}",
            "#{0001} 255, _"})
    @DisplayName("every byte from nought to 255 is found where it sits")
    void findsEveryByte(String search, String found) {
        assertThat(answerTo("find " + search)).isEqualTo(found);
    }

    @ParameterizedTest(name = "{0} answers {1}")
    @CsvSource(delimiter = '|', value = {
            "find/case #{00FF} 255 | #{FF}",
            "find/tail #{00FF01} 255 | #{01}",
            "find/match #{FF00} 255 | #{FF00}",
            "find/match #{00FF} 255 | _",
            "find/last #{FF00FF00} 255 | #{FF00}",
            "find/reverse tail #{FF00FF00} 255 | #{FF00}",
            "find/skip #{00FFFF00} 255 2 | #{FF00}",
            "find/skip #{FF00FF00} 200 2 | _",
            "find/part #{00FF} 255 2 | #{FF}",
            "find/part #{00FF} 255 1 | _"})
    @DisplayName("a byte above 127 is found under every refinement")
    void findsAHighByteUnderEveryRefinement(String search, String found) {
        assertThat(answerTo(search)).isEqualTo(found);
    }

    @ParameterizedTest(name = "{0} answers {1}")
    @CsvSource(delimiter = '|', value = {
            "select #{FE01} 254 | 1",
            "select #{FF01} 255 | 1",
            "select #{00FF} 0 | 255",
            "select #{01FF} 255 | _"})
    @DisplayName("SELECT answers the byte after a high byte")
    void selectsAfterAHighByte(String search, String found) {
        assertThat(answerTo(search)).isEqualTo(found);
    }

    @ParameterizedTest(name = "{0} answers {1}")
    @CsvSource(delimiter = '|', value = {
            "find #{00FF} to char! 255 | #{FF}",
            "find #{0080} to char! 128 | #{80}",
            "find #{00FF} #{FF} | #{FF}"})
    @DisplayName("a char or a binary needle finds a high byte as a number does")
    void findsAHighByteByCharOrBinary(String search, String found) {
        assertThat(answerTo(search)).isEqualTo(found);
    }

    @ParameterizedTest(name = "{0} answers none")
    @ValueSource(strings = {"find {ab} 98", "find {a^(FF)} 255", "find {a^(E9)} 233"})
    @DisplayName("a whole number in a string is searched for as text, so it finds no character")
    void findsNoCharacterInAStringByNumber(String search) {
        assertThat(answerTo(search)).isEqualTo("_");
    }
}
