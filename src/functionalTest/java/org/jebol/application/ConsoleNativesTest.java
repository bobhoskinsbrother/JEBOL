package org.jebol.application;

import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleNativesTest {

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

    private static Interpreter makeAnInterpreterReaching(boolean granted, String... lines) {
        Bounds bounds = granted
                ? Bounds.standard().grant(HostService.CONSOLE)
                : Bounds.standard();
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withConsole(new Prepared(lines)),
                OutputPort.discard(),
                bounds);
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(Interpreter interpreter, String source) {
        return answerTo(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("INPUT gives one line")
    void readsOneLine() {
        assertThat(answerTo(makeAnInterpreterReaching(true, "hello"), "input")).isEqualTo("\"hello\"");
    }

    @Test
    @DisplayName("each call takes the next line")
    void readsTheLinesInOrder() {
        assertThat(answerTo(makeAnInterpreterReaching(true, "one", "two"), "input input"))
                .isEqualTo("\"two\"");
    }

    @Test
    @DisplayName("nothing more to read answers none")
    void answersNoneAtTheEnd() {
        assertThat(answerTo(makeAnInterpreterReaching(true), "none? input")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty line is an empty string")
    void readsPastAnEmptyLine() {
        assertThat(answerTo(makeAnInterpreterReaching(true, ""), "input")).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("ASK writes the question and then reads")
    void asksTheQuestionFirst() {
        assertThat(answerTo(makeAnInterpreterReaching(true, "yes"), "ask \"go on? \"")).isEqualTo("\"yes\"");
    }

    @Test
    @DisplayName("without the grant both are refused")
    void needsTheGrant() {
        assertThat(readTheErrorIdOf(makeAnInterpreterReaching(false, "x"), "input")).isEqualTo("no-service");
        assertThat(readTheErrorIdOf(makeAnInterpreterReaching(false, "x"), "ask \"q\"")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("with the grant and no console, reading still fails")
    void needsAPortToo() {
        Interpreter interpreter = AnInterpreter.withBounds(
                Bounds.standard().grant(HostService.CONSOLE));
        assertThat(readTheErrorIdOf(interpreter, "input")).isEqualTo("no-port");
    }
}
