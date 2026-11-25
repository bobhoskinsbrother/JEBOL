package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AStreamWritesLinesAsRebolPrintsThemTest {

    private final ByteArrayOutputStream ordinary = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    private final StreamOutput output = new StreamOutput(
            new PrintStream(ordinary, true, StandardCharsets.UTF_8),
            new PrintStream(errors, true, StandardCharsets.UTF_8));

    private String written(ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a printed line ends with a line feed alone on every system, as R3's Print_Line writes it")
    void endsEachLineWithALineFeed() {
        output.writeLine("a\nb");
        assertThat(written(ordinary)).isEqualTo("a\nb\n");
    }

    @Test
    @DisplayName("a carriage return already in the text leaves as it was, with nothing added")
    void keepsACarriageReturnAsWritten() {
        output.write("x\r\ny");
        assertThat(written(ordinary)).isEqualTo("x\r\ny");
    }

    @Test
    @DisplayName("the error stream ends its lines the same way")
    void endsErrorLinesTheSameWay() {
        output.writeError("** Script Error\n");
        assertThat(written(errors)).isEqualTo("** Script Error\n");
    }
}
