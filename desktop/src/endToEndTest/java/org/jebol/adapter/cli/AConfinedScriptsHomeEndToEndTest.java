package org.jebol.adapter.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(180)
class AConfinedScriptsHomeEndToEndTest {

    private static final int PROCESS_TIMEOUT_SECONDS = 90;

    private static final String SCRIPT_AS_THE_ROOT_NAMES_IT = "/app/script.r3";

    @TempDir
    Path machineFolder;

    private Path root;

    private Path machineHome;

    private record Ran(int exitStatus, List<String> lines) {
    }

    @BeforeEach
    void makeRootAndHomeBesideIt() throws IOException {
        root = Files.createDirectories(machineFolder.resolve("root"));
        machineHome = Files.createDirectories(machineFolder.resolve("home"));
    }

    private Ran runConfined(Consumer<Map<String, String>> settingHome, String source, String... switches)
            throws IOException, InterruptedException {

        Path app = Files.createDirectories(root.resolve("app"));
        Files.writeString(app.resolve("script.r3"), "Rebol []\n" + source);
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                "-cp", System.getProperty("java.class.path"),
                Repl.class.getName()));
        command.addAll(List.of(switches));
        command.addAll(List.of(Repl.ROOT_SWITCH, root.toString(), SCRIPT_AS_THE_ROOT_NAMES_IT));
        Path output = machineFolder.resolve("output.txt");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(app.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile());
        Map<String, String> environment = builder.environment();
        environment.remove("REBOL_HOME");
        environment.remove("USERPROFILE");
        environment.remove("APPDATA");
        settingHome.accept(environment);
        Process process = builder.start();
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("the script was still running after "
                    + PROCESS_TIMEOUT_SECONDS + " seconds");
        }
        return new Ran(process.exitValue(), Files.readAllLines(output, StandardCharsets.UTF_8));
    }

    private Ran runConfined(String source, String... switches) throws IOException, InterruptedException {
        return runConfined(environment -> environment.put("HOME", machineHome.toString()), source, switches);
    }

    @Test
    @DisplayName("under --root a script's home is the root, its data is beside it, and HOME agrees")
    void reportsHomeInsideRoot() throws Exception {
        Ran ran = runConfined("""
                print mold reduce [system/options/home  system/options/data  get-env {HOME}]""");

        assertThat(ran.lines()).containsExactly("""
                [%/ %/.rebol/ "/"]""");
        assertThat(ran.exitStatus()).isZero();
    }

    @Test
    @DisplayName("nothing is made inside the root at the machine's home path")
    void makesNoCopyOfMachinesHome() throws Exception {
        runConfined("""
                print {ran}""");

        try (Stream<Path> made = Files.list(root)) {
            assertThat(made.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("app", ".rebol");
        }
    }

    @Test
    @DisplayName("with -s, set-user/n makes REBOL's user file at home inside the root, and set-user reads it back")
    void makesUserFileAtHomeInsideRoot() throws Exception {
        Ran made = runConfined("""
                set-env {REBOL_SAFE_PASS} {a-pass}
                set-user/n/p me {a-pass}
                print port? system/user/data""", "-s");
        Ran readBack = runConfined("""
                set-env {REBOL_SAFE_PASS} {a-pass}
                set-user/p me {a-pass}
                print port? system/user/data""", "-s");

        assertThat(made.lines()).containsExactly("true");
        assertThat(root.resolve(".me.safe")).isRegularFile();
        assertThat(readBack.lines()).containsExactly("true");
        assertThat(machineHome.resolve(".me.safe")).doesNotExist();
    }

    @Test
    @DisplayName("without -s, set-user/n is refused at home, as R3's default policy refuses it")
    void refusesUserFileUnderDefaultPolicy() throws Exception {
        Ran ran = runConfined("""
                set-env {REBOL_SAFE_PASS} {a-pass}
                e: try [set-user/n/p me {a-pass}]
                print mold reduce [e/id e/arg1]""");

        assertThat(ran.lines()).containsExactly("[security %/.me.safe]");
        assertThat(root.resolve(".me.safe")).doesNotExist();
    }

    @Test
    @DisplayName("a program the script starts sees the machine's own HOME")
    void letsProgramSeeMachinesHome() throws Exception {
        Ran ran = runConfined("""
                printed: copy {}
                call/shell/wait/output {echo $HOME} printed
                print trim/lines printed""", "-s");

        assertThat(ran.lines()).containsExactly(machineHome.toString());
    }

    @Test
    @DisplayName("with HOME unset, boot stops at the security policy, as R3's does")
    void stopsAtBootWithoutHome() throws Exception {
        Ran ran = runConfined(environment -> environment.remove("HOME"), """
                print {ran}""");

        assertThat(ran.lines()).doesNotContain("ran");
        assertThat(String.join("\n", ran.lines())).contains("invalid _ security policy: allow");
    }

    @Test
    @DisplayName("a child started through system/options/boot agrees with its parent on home and data")
    void childAgreesOnHomeAndData() throws Exception {
        Files.writeString(root.resolve("child.r3"), """
                Rebol []
                print mold reduce [system/options/home  system/options/data]
                """);

        Ran ran = runConfined("""
                print mold reduce [system/options/home  system/options/data]
                printed: copy {}
                call/wait/output reduce [to-local-file system/options/boot  {/child.r3}] printed
                print trim/lines printed""", "-s");

        assertThat(ran.lines()).hasSize(2);
        assertThat(ran.lines().get(1)).isEqualTo(ran.lines().getFirst());
        assertThat(ran.lines().getFirst()).isEqualTo("[%/ %/.rebol/]");
    }
}
