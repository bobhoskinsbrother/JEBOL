package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AStreamEndsLinesAsItsSystemDoesTest {

    private final ByteArrayOutputStream ordinary = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    private StreamOutput endingLinesWith(String lineEnding) {
        return new StreamOutput(new PrintStream(ordinary, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8), lineEnding);
    }

    private String written(ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("on Windows every line feed a script prints leaves as CR LF, the ones inside the text as well as print's own, as R3 encodes with OS_CRLF")
    void windowsEndsEveryLineWithCarriageReturnAndLineFeed() {
        StreamOutput output = endingLinesWith("\r\n");
        output.writeLine("a\nb");
        assertThat(written(ordinary)).isEqualTo("a\r\nb\r\n");
    }

    @Test
    @DisplayName("on Windows the error stream ends its lines the same way")
    void windowsEndsErrorLinesTheSameWay() {
        endingLinesWith("\r\n").writeError("** Script Error\n");
        assertThat(written(errors)).isEqualTo("** Script Error\r\n");
    }

    @Test
    @DisplayName("on Windows a carriage return already in the text is kept and a line feed still gains one, as R3 does")
    void windowsAddsACarriageReturnEvenAfterOne() {
        endingLinesWith("\r\n").write("x\r\ny");
        assertThat(written(ordinary)).isEqualTo("x\r\r\ny");
    }

    @Test
    @DisplayName("elsewhere the text leaves exactly as it was written")
    void elsewhereNothingChanges() {
        StreamOutput output = endingLinesWith("\n");
        output.writeLine("a\nb");
        output.writeError("c\n");
        assertThat(written(ordinary)).isEqualTo("a\nb\n");
        assertThat(written(errors)).isEqualTo("c\n");
    }

    @Test
    @DisplayName("a stream made without a system in mind ends lines with a line feed alone")
    void aPlainStreamEndsLinesWithALineFeed() {
        StreamOutput output = new StreamOutput(new PrintStream(ordinary, true, StandardCharsets.UTF_8));
        output.writeLine("a");
        assertThat(written(ordinary)).isEqualTo("a\n");
    }
}
