package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapLiteralFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a single-item map literal is invalid-arg")
    void refusesASingleItemMap() {
        assertThat(readTheErrorIdOf("load {#[x]}")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("an odd-length map literal is invalid-arg")
    void refusesAnOddLengthMap() {
        assertThat(readTheErrorIdOf("load {#[a 1 b]}")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("a paired map literal loads")
    void loadsAPairedMap() {
        assertThat(answerTo("""
                m: load {#[a 1 b 2]} reduce [m/a m/b]""")).isEqualTo("[1 2]");
    }

    @Test
    @DisplayName("an empty map literal loads")
    void loadsAnEmptyMap() {
        assertThat(answerTo("empty? load {#[]}")).isEqualTo("#(true)");
    }
}
