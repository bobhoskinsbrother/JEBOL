package org.jebol.adapter.cli;

import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.application.TheShell;
import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.testing.TestTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jebol.testing.OnEachTarget.elsewhere;
import static org.jebol.testing.OnEachTarget.expected;
import static org.jebol.testing.OnEachTarget.on;

class AConfinedCommandLineTellsStartItsHomeTest {

    private static final String[] NAMES_START_READS = {"HOME", "USERPROFILE", "APPDATA", "REBOL_HOME"};

    @TempDir
    Path machineFolder;

    private Path root;

    private Path outside;

    private ProcessEnvironment environment;

    @BeforeEach
    void makeRootAndHomeBesideIt() throws IOException {
        root = Files.createDirectories(machineFolder.resolve("root"));
        outside = Files.createDirectories(machineFolder.resolve("home-outside"));
        environment = new ProcessEnvironment();
        for (String name : NAMES_START_READS) {
            environment.assign(name, null);
        }
    }

    private static Bounds grantEverything() {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.grant(service);
        }
        return bounds;
    }

    private Interpreter startConfined() {
        Bounds bounds = grantEverything();
        Interpreter interpreter = Interpreter.createOn(
                Repl.portsFor(bounds, new String[]{Repl.ROOT_SWITCH, root.toString()},
                        new PrintStream(OutputStream.nullOutputStream()), false, root, environment,
                        ConsolePort.none()),
                OutputPort.discard(), bounds);
        ScriptOutcome started = interpreter.start();
        assertThat(started.succeeded()).as(started.display()).isTrue();
        return interpreter;
    }

    private String answer(Interpreter interpreter, String source) {
        return interpreter.run(source).display();
    }

    @Test
    @DisplayName("a home reached through a link inside the root that lands outside it is the root")
    void placesHomeThroughLinkLeavingRootAtRoot() throws IOException {
        Path door = Files.createSymbolicLink(root.resolve("door"), outside);
        environment.assign("HOME", door.toString());

        assertThat(answer(startConfined(), """
                system/options/home""")).isEqualTo("%/");
    }

    @Test
    @DisplayName("start makes the data directory inside the root, and nothing at the machine's home path")
    void makesDataInsideRootAndNoCopyOfMachinesHome() throws IOException {
        environment.assign("HOME", outside.toString());

        startConfined();

        try (Stream<Path> made = Files.list(root)) {
            assertThat(made.map(path -> path.getFileName().toString())).containsExactly(".rebol");
        }
        assertThat(root.resolve(".rebol/modules")).isDirectory();
    }

    @Test
    @DisplayName("a name the machine does not set stays unset")
    void leavesUnsetNamesUnset() {
        environment.assign("HOME", outside.toString());

        assertThat(answer(startConfined(), """
                reduce [get-env {USERPROFILE}  get-env {APPDATA}]""")).isEqualTo("[_ _]");
    }

    @Test
    @DisplayName("APPDATA outside the root is not there at all")
    void hidesAppDataOutsideRoot() {
        environment.assign("HOME", outside.toString());
        environment.assign("APPDATA", outside.toString());

        assertThat(answer(startConfined(), """
                get-env {APPDATA}""")).isEqualTo("_");
    }

    @Test
    @DisplayName("a program the script starts inherits the machine's own HOME, which means something outside the root")
    void letsProgramsInheritMachinesHome() {
        environment.assign("HOME", outside.toString());

        assertThat(answer(startConfined(), """
                printed: copy {}
                call/shell/wait/output {%s} printed
                (trim/lines printed) == {%s}""".formatted(TheShell.echo("HOME"), outside))).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a HOME the script sets itself is what it reads back and what a program it starts inherits")
    void letsScriptsOwnHomeReachBoth() {
        environment.assign("HOME", outside.toString());

        assertThat(answer(startConfined(), """
                set-env {HOME} {/elsewhere}
                printed: copy {}
                call/shell/wait/output {%s} printed
                reduce [get-env {HOME}  trim/lines printed]""".formatted(TheShell.echo("HOME")))).isEqualTo("""
                ["/elsewhere" "/elsewhere"]""");
    }

    private void placeHomeOutsideRoot() {
        environment.assign("HOME", outside.toString());
    }

    private void placeHomeInsideRootAt(String beneath) throws IOException {
        Files.createDirectories(root.resolve(beneath));
        environment.assign("HOME", root.resolve(beneath).toString());
    }

    private void placeAppDataInsideRootAt(String beneath) throws IOException {
        Files.createDirectories(root.resolve(beneath));
        environment.assign("HOME", outside.toString());
        environment.assign("APPDATA", root.resolve(beneath).toString());
    }

    private static String rootsLocalName() {
        return expected(on(TestTarget.DESKTOP_WINDOWS, ""), elsewhere("/"));
    }

    @Test
    @DisplayName("a machine home outside the root is the root, data is /.rebol/ beside it, and HOME says so")
    void placesHomeOutsideRootAtRoot() {
        placeHomeOutsideRoot();

        assertThat(answer(startConfined(), """
                reduce [system/options/home  system/options/data  get-env {HOME}]""")).isEqualTo("""
                [%%/ %%/.rebol/ "%s"]""".formatted(rootsLocalName()));
    }

    @Test
    @DisplayName("a machine home inside the root is written beneath it, as a drive on Windows, and data follows")
    void translatesHomeInsideRoot() throws IOException {
        placeHomeInsideRootAt("u/me");
        String homesLocalName = expected(on(TestTarget.DESKTOP_WINDOWS, "u:\\me"), elsewhere("/u/me"));

        assertThat(answer(startConfined(), """
                reduce [system/options/home  system/options/data  get-env {HOME}]""")).isEqualTo("""
                [%%/u/me/ %%/u/me/.rebol/ "%s"]""".formatted(homesLocalName));
    }

    @Test
    @DisplayName("a home exactly at the root is the root")
    void placesHomeAtRootItself() {
        environment.assign("HOME", root.toString());

        assertThat(answer(startConfined(), """
                reduce [system/options/home  get-env {HOME}]""")).isEqualTo("""
                [%%/ "%s"]""".formatted(rootsLocalName()));
    }

    @Test
    @DisplayName("USERPROFILE is answered with the same home, where the machine sets it")
    void answersUserProfileWithConfinedHome() {
        placeHomeOutsideRoot();
        environment.assign("USERPROFILE", outside.toString());

        assertThat(answer(startConfined(), """
                reduce [get-env {USERPROFILE}]""")).isEqualTo("""
                ["%s"]""".formatted(rootsLocalName()));
    }

    @Test
    @DisplayName("APPDATA inside the root is written beneath it, with backslashes on Windows")
    void translatesAppDataInsideRoot() throws IOException {
        placeAppDataInsideRootAt("roaming");
        String appDatasLocalName = expected(on(TestTarget.DESKTOP_WINDOWS, "\\\\roaming"), elsewhere("/roaming"));

        assertThat(answer(startConfined(), """
                reduce [get-env {APPDATA}]""")).isEqualTo("""
                ["%s"]""".formatted(appDatasLocalName));
    }

    @Test
    @DisplayName("LIST-ENV agrees with GET-ENV about HOME")
    void listsConfinedHome() {
        placeHomeOutsideRoot();

        assertThat(answer(startConfined(), """
                reduce [select list-env {HOME}]""")).isEqualTo("""
                ["%s"]""".formatted(rootsLocalName()));
    }
}
