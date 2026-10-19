package org.jebol.adapter.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class WhatAScriptMayTouchEndToEndTest {

    private static final int WHAT_A_QUIT_POLICY_LEAVES_WITH = 101;

    @TempDir
    Path machine;

    private Path app;

    private Path outsideFile;

    private record Ran(int exitStatus, String printed, String reported) {
    }

    @BeforeEach
    void makeAnAppFolderWithAFileBesideIt() throws IOException {
        app = Files.createDirectories(machine.resolve("app"));
        Files.writeString(app.resolve("inside.txt"), "inside");
        outsideFile = machine.resolve("outside.txt");
        Files.writeString(outsideFile, "outside");
    }

    private Ran run(String source, String... options) throws IOException {
        return runNamed(app.resolve("script.r3").toString(), source, options);
    }

    private Ran runNamed(String scriptAsNamed, String source, String... options) throws IOException {
        Files.writeString(app.resolve("script.r3"), "Rebol []\n" + source);
        List<String> arguments = new ArrayList<>(List.of(options));
        arguments.add(scriptAsNamed);
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runCommandLine(arguments.toArray(String[]::new),
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                app.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    private String writeAsRebolSeesIt(Path host) throws IOException {
        return "%" + host.toRealPath();
    }

    private String describeTheViolationNaming(Path host) throws IOException {
        return "** Access error: security violation: " + writeAsRebolSeesIt(host);
    }

    @Nested
    @DisplayName("with no security switch, a script reaches its own folder and nothing beside it")
    class TheDefaultPolicy {

        @Test
        @DisplayName("a file beside the script is read")
        void readsAFileBesideTheScript() throws IOException {
            Ran ran = run("""
                    print read/string %inside.txt""");

            assertThat(ran.reported()).isEmpty();
            assertThat(ran.printed()).isEqualTo("inside\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("a file one folder up stops the script with a violation naming it, and exit status 1")
        void stopsAtAFileOneFolderUp() throws IOException {
            Ran ran = run("""
                    print {start}
                    read %../outside.txt
                    print {after}""");

            assertThat(ran.printed()).isEqualTo("start\n");
            assertThat(ran.reported()).contains(describeTheViolationNaming(outsideFile) + "  (refer to SECURE function)");
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("a write one folder up writes nothing")
        void writesNothingOneFolderUp() throws IOException {
            Ran ran = run("""
                    write %../text.txt {x}""");

            assertThat(ran.reported()).contains("security violation");
            assertThat(machine.resolve("text.txt")).doesNotExist();
        }

        @Test
        @DisplayName("an absolute path elsewhere on the machine is refused")
        void refusesAnAbsolutePathElsewhere() throws IOException {
            Ran ran = run("read %" + outsideFile.toRealPath());

            assertThat(ran.reported()).contains(describeTheViolationNaming(outsideFile));
        }

        @Test
        @DisplayName("a script can catch the violation and read what it names")
        void letsTheScriptCatchTheViolation() throws IOException {
            Ran ran = run("""
                    e: try [read %../outside.txt]
                    print [e/type e/id e/arg1]""");

            assertThat(ran.printed()).isEqualTo("Access security " + outsideFile.toRealPath() + "\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("a link beside the script pointing out of its folder is refused, naming where it leads")
        void refusesALinkPointingOut() throws IOException {
            Files.createSymbolicLink(app.resolve("door.txt"), outsideFile);

            Ran ran = run("read %door.txt");

            assertThat(ran.reported()).contains(describeTheViolationNaming(outsideFile));
        }

        @Test
        @DisplayName("a link beside the script pointing at another file in its folder is read")
        void readsALinkInside() throws IOException {
            Files.createSymbolicLink(app.resolve("again.txt"), Path.of("inside.txt"));

            Ran ran = run("print read/string %again.txt");

            assertThat(ran.printed()).isEqualTo("inside\n");
        }

        @Test
        @DisplayName("deleting a file one folder up is not checked, as in R3")
        void leavesDeletingUpUnchecked() throws IOException {
            Ran ran = run("delete %../outside.txt");

            assertThat(ran.reported()).isEmpty();
            assertThat(outsideFile).doesNotExist();
        }

        @Test
        @DisplayName("a script tightening its own policy is held to it")
        void holdsAScriptToItsOwnTighterPolicy() throws IOException {
            Ran ran = run("""
                    secure [file throw]
                    read %inside.txt""");

            assertThat(ran.reported()).contains(describeTheViolationNaming(app.resolve("inside.txt")));
        }
    }

    @Nested
    @DisplayName("the security switches")
    class TheSwitches {

        @Test
        @DisplayName("-s allows everything, so one folder up is read")
        void secureMin() throws IOException {
            Ran ran = run("print read/string %../outside.txt", "-s");

            assertThat(ran.printed()).isEqualTo("outside\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("--secure allow does the same")
        void secureAllow() throws IOException {
            Ran ran = run("print read/string %../outside.txt", "--secure", "allow");

            assertThat(ran.printed()).isEqualTo("outside\n");
        }

        @Test
        @DisplayName("+s stops start at its own PROTECT, before the script runs, with exit status 1")
        void secureMax() throws IOException {
            Ran ran = run("print {ran}", "+s");

            assertThat(ran.printed()).doesNotContain("ran");
            assertThat(ran.reported()).contains(
                    "** Access error: security violation: system/options/boot-level");
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("--secure throw stops start the same way")
        void secureThrow() throws IOException {
            Ran ran = run("print {ran}", "--secure", "throw");

            assertThat(ran.printed()).doesNotContain("ran");
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("--secure quit leaves with 101 and says nothing")
        void secureQuit() throws IOException {
            Ran ran = run("print {ran}", "--secure", "quit");

            assertThat(ran.printed()).doesNotContain("ran");
            assertThat(ran.reported()).isEmpty();
            assertThat(ran.exitStatus()).isEqualTo(WHAT_A_QUIT_POLICY_LEAVES_WITH);
        }
    }

    @Nested
    @DisplayName("a script confined with --root")
    class Confined {

        @Test
        @DisplayName("a link inside the root leading out of it is refused, even with every policy at allow")
        void refusesALinkLeavingTheRoot() throws IOException {
            Files.createSymbolicLink(app.resolve("door.txt"), outsideFile);

            Ran ran = runNamed("/script.r3", """
                    e: try [read %door.txt]
                    print [e/id e/arg1]""", "-s", "--root", app.toString());

            assertThat(ran.printed()).isEqualTo("security door.txt\n");
            assertThat(ran.exitStatus()).isZero();
        }
    }
}
