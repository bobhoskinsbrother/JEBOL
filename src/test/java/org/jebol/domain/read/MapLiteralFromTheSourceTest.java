package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A map literal {@code #[...]} whose keys and values do not pair up is
 * refused with {@code invalid-arg}, read from {@code lexer-test.r3}
 * "Invalid MAP" -- not the construction failure a malformed datatype gives.
 */
class MapLiteralFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a single-item map literal is invalid-arg")
    void aSingleItemMapIsInvalidArg() {
        assertThat(errorIdOf("load {#[x]}")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("an odd-length map literal is invalid-arg")
    void anOddLengthMapIsInvalidArg() {
        assertThat(errorIdOf("load {#[a 1 b]}")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("a paired map literal loads")
    void aPairedMapLoads() {
        assertThat(answerTo("""
                m: load {#[a 1 b 2]} reduce [m/a m/b]""")).isEqualTo("[1 2]");
    }

    @Test
    @DisplayName("an empty map literal loads")
    void anEmptyMapLoads() {
        assertThat(answerTo("empty? load {#[]}")).isEqualTo("#(true)");
    }
}
