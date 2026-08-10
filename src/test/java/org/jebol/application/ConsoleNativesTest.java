package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.jebol.domain.eval.ConsolePort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * INPUT and ASK, and the grant a script needs to stop and wait.
 *
 * <p>Specified in {@code spec/embed.allium}.
 *
 * <p>A script that waits for a person is a script the host has to have
 * agreed to. Reading is a separate grant from writing, because a host
 * almost always wants to see what a script printed and almost never wants
 * it to stop.
 */
class ConsoleNativesTest {

    /** A console that answers the lines it was given, in order. */
    private static final class Prepared implements ConsolePort {

        private final Deque<String> lines;

        private Prepared(String... prepared) {
            this.lines = new ArrayDeque<>(List.of(prepared));
        }

        @Override
        public String readLine() {
            return lines.poll();
        }

        @Override
        public String readHiddenLine() {
            return lines.poll();
        }
    }

    private static Interpreter reaching(boolean granted, String... lines) {
        Bounds bounds = granted
                ? Bounds.standard().granting(HostService.CONSOLE)
                : Bounds.standard();
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.useConsole(new Prepared(lines));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(Interpreter interpreter, String source) {
        return answerTo(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("INPUT gives one line")
    void oneLineIsRead() {
        assertThat(answerTo(reaching(true, "hello"), "input")).isEqualTo("\"hello\"");
    }

    @Test
    @DisplayName("each call takes the next line")
    void theLinesComeInOrder() {
        assertThat(answerTo(reaching(true, "one", "two"), "input input"))
                .isEqualTo("\"two\"");
    }

    @Test
    @DisplayName("nothing more to read answers none")
    void theEndIsNone() {
        // None and not an empty string, thus a script can tell the end
        // from a line with nothing on it.
        assertThat(answerTo(reaching(true), "none? input")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty line is an empty string")
    void anEmptyLineIsNotTheEnd() {
        assertThat(answerTo(reaching(true, ""), "input")).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("ASK writes the question and then reads")
    void theQuestionComesFirst() {
        assertThat(answerTo(reaching(true, "yes"), "ask \"go on? \"")).isEqualTo("\"yes\"");
    }

    @Test
    @DisplayName("without the grant both are refused")
    void theGrantIsNeeded() {
        assertThat(errorIdOf(reaching(false, "x"), "input")).isEqualTo("no-service");
        assertThat(errorIdOf(reaching(false, "x"), "ask \"q\"")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("with the grant and no console, reading still fails")
    void aPortIsAlsoNeeded() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.CONSOLE));
        assertThat(errorIdOf(interpreter, "input")).isEqualTo("no-port");
    }
}
