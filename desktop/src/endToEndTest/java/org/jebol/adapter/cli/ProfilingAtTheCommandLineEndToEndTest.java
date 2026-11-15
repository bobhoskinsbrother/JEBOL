package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class ProfilingAtTheCommandLineEndToEndTest {

    private record Ran(int exitStatus, String printed, String reported) {
    }

    private Ran run(Path directory, String script) throws Exception {
        Path written = directory.resolve("profiling.reb");
        Files.writeString(written, script);
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runCommandLine(new String[] {written.toString()},
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8), reported.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a script sees the template as none, profiles it into numbers, and measures a block with delta-profile")
    void profilesAWorkload(@TempDir Path directory) throws Exception {
        Ran ran = run(directory, """
                Rebol []
                print ["template" mold system/standard/stats/evals]
                print ["profiled" type? stats/profile/evals]
                measured: delta-profile [loop 50 [1 + 1]]
                print ["measured" measured/evals > 0 measured/recycles]
                """);

        assertThat(ran.printed()).isEqualTo("""
                template _
                profiled integer!
                measured true 0
                """);
        assertThat(ran.reported()).isEmpty();
        assertThat(ran.exitStatus()).isZero();
    }

    @Test
    @DisplayName("a script that subtracts from the untouched template is refused, as a real 3.22.5 refuses it")
    void subtractingFromTheUntouchedTemplateIsRefused(@TempDir Path directory) throws Exception {
        Ran ran = run(directory, """
                Rebol []
                print system/standard/stats/evals - 1
                print "not reached"
                """);

        assertThat(ran.exitStatus()).isEqualTo(1);
        assertThat(ran.printed() + ran.reported()).contains("cannot use subtract");
        assertThat((ran.printed() + ran.reported()).lines()).doesNotContain("not reached");
    }
}
