package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CHANGE and REPLACE on strings, from {@code series-test.r3}. CHANGE returns the
 * position just past what it wrote, grows the string when the replacement runs
 * past the tail, and REPLACE copies a replacement that aliases the target before
 * the destructive write.
 */
class ChangeAndReplaceFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("CHANGE returns the position just past what it wrote")
    void changeReturnsThePositionAfter() {
        assertThat(answerTo("""
                s: copy "12345" p: change at s 1 skip s 1
                all [s = "23455" p = "5"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CHANGE with a shorter replacement overwrites only that many")
    void changeOverwritesTheReplacementLength() {
        assertThat(answerTo("""
                c: copy "hello" change c "AB" c = "ABllo\"""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CHANGE/dup into an empty string grows it")
    void changeGrowsAnEmptyString() {
        assertThat(answerTo("""
                mem: make string! 5 change/dup mem "x" 5 mem = "xxxxx\"""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REPLACE copies a replacement that aliases the target")
    void replaceCopiesAnAliasedReplacement() {
        assertThat(answerTo("""
                r: copy "abcde" replace r "abcd" skip r 1 r = "bcdee\"""")).isEqualTo("#(true)");
    }
}
