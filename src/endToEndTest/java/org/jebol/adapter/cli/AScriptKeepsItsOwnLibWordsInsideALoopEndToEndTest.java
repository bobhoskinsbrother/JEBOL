package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AScriptKeepsItsOwnLibWordsInsideALoopEndToEndTest {

    private String printedByRunning(Path directory, String source) throws IOException {
        Path script = directory.resolve("own.r3");
        Files.writeString(script, source);
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        Repl.runCommandLine(new String[] {script.toString()},
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        assertThat(reported.toString(StandardCharsets.UTF_8)).isEmpty();
        return printed.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a script that redefines PROBE calls its own inside LOOP, FOR, REPEAT and FOREACH, as R3 does")
    void callsItsOwnDefinitionInEveryLoop(@TempDir Path directory) throws IOException {
        assertThat(printedByRunning(directory, """
                Rebol []
                probe: func [x][print ["mine" x]]
                loop 1 [probe "loop"]
                for i 1 1 1 [probe "for"]
                repeat n 1 [probe "repeat"]
                foreach a [1] [probe "foreach"]
                """)).isEqualTo("mine loop\nmine for\nmine repeat\nmine foreach\n");
    }
}
