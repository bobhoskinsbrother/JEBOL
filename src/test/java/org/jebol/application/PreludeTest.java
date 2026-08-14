package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The half of the standard library that is written in REBOL.
 *
 * <p>Natives are written in Java because they reach something the language
 * cannot: machine arithmetic, the reader, the evaluator. Everything that
 * can be said in REBOL is said in REBOL, in {@code prelude.reb}, evaluated
 * once when an interpreter is built.
 *
 * <p>This is not an optimisation. REBOL is extended in REBOL, and an
 * implementation that can only be extended in its host language is a
 * program that parses REBOL rather than a REBOL.
 */
class PreludeTest {

    @Test
    @DisplayName("a function the prelude defines is callable")
    void aPreludeFunctionIsCallable() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("max 3 7"))).isEqualTo("7");
    }

    @Test
    @DisplayName("a prelude function is an ordinary function value")
    void aPreludeFunctionIsAValue() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("type? :rejoin")))
                .isEqualTo("#(function!)");
    }

    @Test
    @DisplayName("a caller cannot tell it from a native")
    void aPreludeFunctionCanBeRenamedAndCalled() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("larger: :max  larger 2 9")))
                .isEqualTo("9");
    }

    @Test
    @DisplayName("the prelude can use the natives")
    void thePreludeSeesTheNatives() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("min 4 1"))).isEqualTo("1");
    }

    @Test
    @DisplayName("a script's own words do not disturb it")
    void aScriptCannotBreakThePreludeForTheNextInterpreter() {
        Interpreter first = Interpreter.create();
        first.run("max: 3");

        assertThat(Interpreter.create().display(Interpreter.create().run("max 3 7")))
                .isEqualTo("7");
    }
}
