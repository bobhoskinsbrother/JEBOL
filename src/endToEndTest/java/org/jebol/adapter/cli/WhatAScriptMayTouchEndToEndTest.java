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
    void anAppFolderWithAFileBesideIt() throws IOException {
        app = Files.createDirectories(machine.resolve("app"));
        Files.writeString(app.resolve("inside.txt"), "inside");
        outsideFile = machine.resolve("outside.txt");
        Files.writeString(outsideFile, "outside");
    }

    private Ran running(String source, String... options) throws IOException {
        return runningNamed(app.resolve("script.r3").toString(), source, options);
    }

    private Ran runningNamed(String scriptAsNamed, String source, String... options) throws IOException {
        Files.writeString(app.resolve("script.r3"), "Rebol []\n" + source);
        List<String> arguments = new ArrayList<>(List.of(options));
        arguments.add(scriptAsNamed);
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runTheCommandLine(arguments.toArray(String[]::new),
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                app.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    private String asRebolSeesIt(Path host) throws IOException {
        return "%" + host.toRealPath();
    }

    private String theViolationNaming(Path host) throws IOException {
        return "** Access error: security violation: " + asRebolSeesIt(host);
    }

    @Nested
    @DisplayName("with no security switch, a script reaches its own folder and nothing beside it")
    class TheDefaultPolicy {

        @Test
        @DisplayName("a file beside the script is read")
        void itsOwnFolder() throws IOException {
            Ran ran = running("""
                    print read/string %inside.txt""");

            assertThat(ran.reported()).isEmpty();
            assertThat(ran.printed()).isEqualTo("inside\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("a file one folder up stops the script with a violation naming it, and exit status 1")
        void oneFolderUp() throws IOException {
            Ran ran = running("""
                    print {start}
                    read %../outside.txt
                    print {after}""");

            assertThat(ran.printed()).isEqualTo("start\n");
            assertThat(ran.reported()).contains(theViolationNaming(outsideFile) + "  (refer to SECURE function)");
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("a write one folder up writes nothing")
        void aWriteUp() throws IOException {
            Ran ran = running("""
                    write %../written.txt {x}""");

            assertThat(ran.reported()).contains("security violation");
            assertThat(machine.resolve("written.txt")).doesNotExist();
        }

        @Test
        @DisplayName("an absolute path elsewhere on the machine is refused")
        void anAbsolutePath() throws IOException {
            Ran ran = running("read %" + outsideFile.toRealPath());

            assertThat(ran.reported()).contains(theViolationNaming(outsideFile));
        }

        @Test
        @DisplayName("a script can catch the violation and read what it names")
        void caughtAndRead() throws IOException {
            Ran ran = running("""
                    e: try [read %../outside.txt]
                    print [e/type e/id e/arg1]""");

            assertThat(ran.printed()).isEqualTo("Access security " + outsideFile.toRealPath() + "\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("a link beside the script pointing out of its folder is refused, naming where it leads")
        void aLinkPointingOut() throws IOException {
            Files.createSymbolicLink(app.resolve("door.txt"), outsideFile);

            Ran ran = running("read %door.txt");

            assertThat(ran.reported()).contains(theViolationNaming(outsideFile));
        }

        @Test
        @DisplayName("a link beside the script pointing at another file in its folder is read")
        void aLinkInside() throws IOException {
            Files.createSymbolicLink(app.resolve("again.txt"), Path.of("inside.txt"));

            Ran ran = running("print read/string %again.txt");

            assertThat(ran.printed()).isEqualTo("inside\n");
        }

        @Test
        @DisplayName("deleting a file one folder up is not checked, as in R3")
        void deletingUp() throws IOException {
            Ran ran = running("delete %../outside.txt");

            assertThat(ran.reported()).isEmpty();
            assertThat(outsideFile).doesNotExist();
        }

        @Test
        @DisplayName("a script tightening its own policy is held to it")
        void tighteningItsOwn() throws IOException {
            Ran ran = running("""
                    secure [file throw]
                    read %inside.txt""");

            assertThat(ran.reported()).contains(theViolationNaming(app.resolve("inside.txt")));
        }
    }

    @Nested
    @DisplayName("the security switches")
    class TheSwitches {

        @Test
        @DisplayName("-s allows everything, so one folder up is read")
        void secureMin() throws IOException {
            Ran ran = running("print read/string %../outside.txt", "-s");

            assertThat(ran.printed()).isEqualTo("outside\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("--secure allow does the same")
        void secureAllow() throws IOException {
            Ran ran = running("print read/string %../outside.txt", "--secure", "allow");

            assertThat(ran.printed()).isEqualTo("outside\n");
        }

        @Test
        @DisplayName("+s stops start at its own PROTECT, before the script runs, with exit status 1")
        void secureMax() throws IOException {
            Ran ran = running("print {ran}", "+s");

            assertThat(ran.printed()).doesNotContain("ran");
            assertThat(ran.reported()).contains(
                    "** Access error: security violation: system/options/boot-level");
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("--secure throw stops start the same way")
        void secureThrow() throws IOException {
            Ran ran = running("print {ran}", "--secure", "throw");

            assertThat(ran.printed()).doesNotContain("ran");
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("--secure quit leaves with 101 and says nothing")
        void secureQuit() throws IOException {
            Ran ran = running("print {ran}", "--secure", "quit");

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
        void aLinkLeavingTheRoot() throws IOException {
            Files.createSymbolicLink(app.resolve("door.txt"), outsideFile);

            Ran ran = runningNamed("/script.r3", """
                    e: try [read %door.txt]
                    print [e/id e/arg1]""", "-s", "--root", app.toString());

            assertThat(ran.printed()).isEqualTo("security door.txt\n");
            assertThat(ran.exitStatus()).isZero();
        }
    }
}
