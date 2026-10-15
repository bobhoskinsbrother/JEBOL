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

    private static Session aSessionIn(Path directory, String typed) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runTheCommandLine(new String[0],
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
        Session session = aSessionIn(directory, """
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
        assertThat(session.exitStatus()).isEqualTo(Repl.KEEP_THE_PROCESS);
    }

    @Test
    @DisplayName("the machine is in reach, as it is for a script")
    void theMachineIsInReach(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("data.txt"), "found it");

        Session session = aSessionIn(directory, """
                read/string %data.txt
                not error? try [get-env "HOME"]
                quit
                """);

        assertThat(session.printed())
                .contains("== \"found it\"")
                .contains("== #(true)");
    }

    @Test
    @DisplayName("and the session reads, evaluates and reports as any console does")
    void theSessionStillReadsEvaluatesAndReports(@TempDir Path directory) {
        Session session = aSessionIn(directory, """
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
