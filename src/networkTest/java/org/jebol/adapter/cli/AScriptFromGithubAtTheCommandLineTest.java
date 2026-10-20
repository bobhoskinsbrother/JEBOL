package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class AScriptFromGithubAtTheCommandLineTest {

    @Test
    @DisplayName("a script given by github url runs with its arguments")
    void runsAScriptFromGithub(@TempDir Path directory) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();

        int status = Repl.runCommandLine(new String[] {
                Repl.DATA_SWITCH, directory.toString(),
                "https://raw.githubusercontent.com/Oldes/Rebol3/master/src/tests/units/files/print-args.r3",
                "a", "b"},
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());

        assertThat(printed.toString(StandardCharsets.UTF_8)).isEqualTo("""
                ["a" "b"]
                ["a" "b"]
                """);
        assertThat(reported.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(status).isZero();
    }
}
