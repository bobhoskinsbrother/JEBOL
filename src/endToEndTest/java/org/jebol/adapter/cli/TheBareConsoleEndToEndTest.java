package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheBareConsoleEndToEndTest {

    private record Session(int exitStatus, String printed, String reported) {
    }

    private static Session openASessionIn(Path directory, String typed) {
        return openASessionIn(directory, typed, new String[0]);
    }

    private static Session openASessionIn(Path directory, String typed, String... switches) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runCommandLine(switches,
                new BufferedReader(new StringReader(typed)),
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Session(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("start runs, so the REBOL banner prints and the console's own does not")
    void startRunsAndTheBannerPrints(@TempDir Path directory) {
        Session session = openASessionIn(directory, """
                system/options/boot-level
                quit
                """);

        assertThat(session.printed())
                .as("the banner start builds")
                .contains("╔")
                .contains("Apache 2.0 License, see LICENSE.")
                .as("start ran to its end, which is where boot-level becomes full")
                .contains("== full")
                .as("and the console's own banner is gone")
                .doesNotContain("JEBOL -- REBOL 3 on the JVM");
        assertThat(session.reported()).isEmpty();
        assertThat(session.exitStatus()).isEqualTo(Repl.KEEP_PROCESS);
    }

    @Test
    @DisplayName("the banner names the JEBOL port rather than Rebol's own branch")
    void namesTheJebolPortInTheBanner(@TempDir Path directory) {
        Session session = openASessionIn(directory, """
                quit
                """);

        assertThat(session.printed())
                .contains("REBOL/core 3.22.5 (JEBOL port)")
                .doesNotContain("Oldes branch");
    }

    @Test
    @DisplayName("every service is granted, and a file where it started asks first, which no one can answer, as in r3")
    void grantsEveryServiceButAFileThatAsks(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("data.txt"), "found it");

        Session session = openASessionIn(directory, """
                read/string %data.txt
                not error? try [get-env "HOME"]
                quit
                """);

        assertThat(session.printed())
                .contains("** Access error: security violation: %"
                        + directory.resolve("data.txt").toRealPath())
                .contains("== #(true)");
    }

    @Test
    @DisplayName("with -s the machine is in reach, as it is for a script")
    void reachesTheMachineWithSecurityOff(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("data.txt"), "found it");

        Session session = openASessionIn(directory, """
                read/string %data.txt
                not error? try [get-env "HOME"]
                quit
                """, "-s");

        assertThat(session.printed())
                .contains("== \"found it\"")
                .contains("== #(true)");
    }

    @Test
    @DisplayName("and the session reads, evaluates and reports as any console does")
    void stillReadsEvaluatesAndReports(@TempDir Path directory) {
        Session session = openASessionIn(directory, """
                1 + 2
                divide 1 0
                2 + 2
                quit
                """);

        assertThat(session.printed())
                .contains("== 3")
                .contains("** Math error: attempt to divide by zero")
                .contains("== 4");
    }
}
