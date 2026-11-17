package org.jebol.android.unit;

import org.jebol.adapter.android.LogcatOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogcatOutputTest {

    private final List<String> logged = new ArrayList<>();
    private final LogcatOutput output = new LogcatOutput(new LogcatOutput.LogLines() {
        @Override
        public void info(String line) {
            logged.add("I " + line);
        }

        @Override
        public void error(String line) {
            logged.add("E " + line);
        }
    });

    @Test
    @DisplayName("a finished line is one log line")
    void logsAFinishedLine() {
        output.writeLine("hello");

        assertThat(logged).containsExactly("I hello");
    }

    @Test
    @DisplayName("a part line is held until it ends, and then is one line with what came before")
    void holdsAPartLine() {
        output.write("ab");
        output.write("c");
        assertThat(logged).isEmpty();

        output.write("d\ne");

        assertThat(logged).containsExactly("I abcd");
    }

    @Test
    @DisplayName("several lines in one write are several log lines, an empty one included")
    void splitsEveryLine() {
        output.write("one\n\nthree\n");

        assertThat(logged).containsExactly("I one", "I ", "I three");
    }

    @Test
    @DisplayName("a carriage return before the newline is not part of the line")
    void dropsACarriageReturn() {
        output.write("dos\r\n");

        assertThat(logged).containsExactly("I dos");
    }

    @Test
    @DisplayName("error output is logged as errors, and its part line is held apart from the other")
    void logsErrorsAsErrors() {
        output.write("out-");
        output.writeError("err\n");
        output.write("put\n");

        assertThat(logged).containsExactly("E err", "I out-put");
    }

    @Test
    @DisplayName("flushing logs a held part line")
    void flushesAHeldLine() {
        output.write("no newline");
        output.flush();

        assertThat(logged).containsExactly("I no newline");
    }
}
