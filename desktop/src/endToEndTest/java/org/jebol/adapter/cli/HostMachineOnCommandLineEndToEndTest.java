package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class HostMachineOnCommandLineEndToEndTest {

    private static final int PROCESS_TIMEOUT_SECONDS = 60;

    private record Ran(long processNumber, int exitStatus, List<String> lines) {
    }

    private Ran runScript(Path directory, String script) throws IOException, InterruptedException {
        Path file = directory.resolve("script.r3");
        Files.writeString(file, script);
        Path output = directory.resolve("output.txt");
        Process process = new ProcessBuilder(List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                "-cp", System.getProperty("java.class.path"),
                Repl.class.getName(), file.toString()))
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("the script was still running after "
                    + PROCESS_TIMEOUT_SECONDS + " seconds");
        }
        return new Ran(process.pid(), process.exitValue(),
                Files.readAllLines(output, StandardCharsets.UTF_8));
    }

    private Process startSleeper() throws IOException {
        return new ProcessBuilder("sleep", "60").start();
    }

    @Test
    @DisplayName("a script at the command line sees the machine it runs on: its own process, home, start directory and platform")
    void seesItsMachine(@TempDir Path directory) throws Exception {
        Ran ran = runScript(directory, """
                Rebol []
                print access-os 'pid
                print system/options/home
                print system/options/path
                print not none? find [Windows macOS Linux] system/platform
                print exists? system/options/boot
                """);

        assertThat(ran.exitStatus()).isZero();
        assertThat(ran.lines()).containsExactly(
                Long.toString(ran.processNumber()),
                System.getProperty("user.home") + "/",
                directory.toRealPath() + "/",
                "true",
                "file");
    }

    @Test
    @DisplayName("a script at the command line ends another process by its number, and names a number nothing runs under")
    void endsAnotherProcess(@TempDir Path directory) throws Exception {
        Process sleeper = startSleeper();

        Ran ran = runScript(directory, """
                Rebol []
                print access-os/set 'pid %d
                e: try [access-os/set 'pid 2147483646]
                print e/id
                """.formatted(sleeper.pid()));

        assertThat(ran.lines()).containsExactly("true", "process-not-found");
        assertThat(sleeper.waitFor(10, TimeUnit.SECONDS)).isTrue();
    }
}
