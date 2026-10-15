package org.jebol.adapter.cli;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.value.ErrorWording;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AScriptOnTheCommandLineRunsAsLongAsItNeedsEndToEndTest {

    private record Ran(int exitStatus, String printed, String reported) {
    }

    private Ran runningTheScript(Path directory, String source) throws IOException {
        Path script = directory.resolve("long.r3");
        Files.writeString(script, source);
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runTheCommandLine(new String[] {script.toString()},
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    @Test
    @Timeout(30)
    @DisplayName("a script that waits past five seconds and then does something still finishes, as a window waiting for its operator does")
    void aScriptThatWaitsAndThenWorksFinishes(@TempDir Path directory) throws IOException {
        Ran ran = runningTheScript(directory, """
                wait 6
                answer: 2 + 2
                print [{still running} answer]
                """);

        assertThat(ran.reported()).isEmpty();
        assertThat(ran.printed()).isEqualTo("still running 4\n");
        assertThat(ran.exitStatus()).isZero();
    }

    @Test
    @Timeout(30)
    @DisplayName("a script that works for more than five seconds finishes")
    void aScriptThatWorksForLongFinishes(@TempDir Path directory) throws IOException {
        Ran ran = runningTheScript(directory, """
                total: 0
                repeat round 70 [
                    wait 0.1
                    loop 1000 [total: total + round]
                ]
                print [{done} total]
                """);

        assertThat(ran.reported()).isEmpty();
        assertThat(ran.printed()).isEqualTo("done 2485000\n");
        assertThat(ran.exitStatus()).isZero();
    }

    @Test
    @Timeout(30)
    @DisplayName("a run its bounds stop is reported in the catalogue's words, not as an improperly formatted error")
    void aStoppedRunIsReportedInTheCataloguesWords() {
        Interpreter embedded = Interpreter.withBounds(
                Bounds.standard().withWallClockLimit(Duration.ofMillis(200)));

        ScriptOutcome outcome = embedded.run("forever []");

        assertThat(embedded.whatTheConsolePrints(outcome))
                .contains("** Access error: port action timed out")
                .doesNotContain(ErrorWording.NOTHING_IN_THE_CATALOGUE);
    }

    @Test
    @Timeout(30)
    @DisplayName("an embedding host's own runs keep their time limit")
    void anEmbeddedRunKeepsItsLimit() {
        Interpreter embedded = Interpreter.withBounds(
                Bounds.standard().withWallClockLimit(Duration.ofMillis(200)));

        ScriptOutcome outcome = embedded.run("forever []");

        assertThat(outcome.succeeded()).isFalse();
    }
}
