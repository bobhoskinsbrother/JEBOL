package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheCommandLineAsRebolsHostReadsItEndToEndTest {

    @TempDir
    Path directory;

    private record Ran(int exitStatus, String printed, String reported) {
    }

    private Ran typing(String typed, String... arguments) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runTheCommandLine(arguments,
                new BufferedReader(new StringReader(typed)),
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    private Ran running(String... arguments) {
        return typing("", arguments);
    }

    private String aScript(String named, String source) throws IOException {
        Files.writeString(directory.resolve(named), source);
        return directory.resolve(named).toString();
    }

    @Nested
    @DisplayName("a script sees the system object as r3's start leaves it")
    class WhatAScriptSees {

        @Test
        @DisplayName("flags, boot level, quiet, its arguments, its title, and the boot finished")
        void theSystemAfterStart() throws IOException {
            String script = aScript("s.r3", """
                    Rebol [Title: "Sweep"]
                    probe system/options/flags
                    probe system/options/boot-level
                    probe system/options/quiet
                    probe system/options/args
                    probe system/options/do-arg
                    probe system/script/title
                    probe system/script/args
                    probe equal? ~ system/options/data
                    probe error? try [system/product: 1]
                    probe error? try [system/build/os: 1]
                    probe error? try [system/options/boot-level: 1]
                    probe sys/boot-mezz
                    probe sys/start
                    probe equal? what-dir first split-path system/options/script
                    """);

            Ran ran = running(script, "a", "b c");

            assertThat(ran.printed()).isEqualTo("""
                    [#(true)]
                    full
                    #(true)
                    ["a" "b c"]
                    _
                    "Sweep"
                    ["a" "b c"]
                    #(true)
                    #(true)
                    #(true)
                    #(true)
                    done
                    done
                    #(true)
                    """);
            assertThat(ran.reported()).isEmpty();
            assertThat(ran.exitStatus()).isZero();
        }
    }

    @Nested
    @DisplayName("--do and a script")
    class DoAndAScript {

        @Test
        @DisplayName("a word after --do's code is the script, so a missing one is reported after the code ran")
        void theWordAfterTheCodeIsTheScript() {
            Ran ran = running("--do",
                    "probe system/options/flags probe system/options/quiet "
                            + "probe system/options/script probe system/options/args",
                    "x");

            assertThat(ran.printed()).isEqualTo("""
                    [do #(true)]
                    #(true)
                    %x
                    []
                    """);
            assertThat(ran.reported()).isEqualTo("""

                    ** access error: script not found: %x

                    """);
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("-- ends the options, and what follows is only arguments")
        void aDoubleDashLeavesOnlyArguments() {
            Ran ran = running("--do", "probe system/options/args", "--", "x", "y");

            assertThat(ran.printed()).isEqualTo("""
                    ["x" "y"]
                    """);
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("the code runs first and then the script")
        void theCodeRunsBeforeTheScript() throws IOException {
            String script = aScript("n.r3", """
                    Rebol [Title: "T"]
                    print system/script/title
                    """);

            Ran ran = running("--do", "print 1", script);

            assertThat(ran.printed()).isEqualTo("1\nT\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("flags come in boot-flags order and --args comes first among the arguments")
        void flagsInBootFlagsOrder() {
            Ran ran = running("-s", "--args", "a b", "--do",
                    "probe system/options/flags probe system/options/args", "--", "c");

            assertThat(ran.printed()).isEqualTo("""
                    [args do secure-min #(true)]
                    ["a b" "c"]
                    """);
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("an empty script name is reported molded, with its quotes")
        void anEmptyScriptName() {
            Ran ran = running("");

            assertThat(ran.reported()).isEqualTo("""

                    ** access error: script not found: %""

                    """);
            assertThat(ran.exitStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("what the host does after start")
    class AfterStart {

        @Test
        @DisplayName("--halt opens the console after the script, in the context the script set its words in")
        void halting() throws IOException {
            String script = aScript("xs.r3", """
                    Rebol []
                    x: 5 f: does [x]
                    """);

            Ran ran = typing("""
                    x
                    x: 6
                    f
                    foo: 3 foo
                    """, "-h", script);

            assertThat(ran.printed()).isEqualTo(
                    ">> == 5\n\n>> == 6\n\n>> == 6\n\n>> == 3\n\n>> ");
            assertThat(ran.exitStatus()).isEqualTo(Repl.KEEP_THE_PROCESS);
        }

        @Test
        @DisplayName("a boot level below mods never reaches start, so --do does not run and the console opens")
        void aBootLevelBelowModsSkipsStart() {
            Ran ran = running("--boot", "base", "--do", "print 1");

            assertThat(ran.printed()).isEqualTo(">> ");
            assertThat(ran.exitStatus()).isEqualTo(Repl.KEEP_THE_PROCESS);
        }

        @Test
        @DisplayName("--cgi reports an error and still leaves with nought")
        void cgiLeavesWithNought() {
            Ran ran = running("--cgi", "--do", "1 / 0");

            assertThat(ran.reported()).isEqualTo("""

                    ** Math error: attempt to divide by zero
                    ** Where: / do if -apply-
                    ** Near: / 0

                    """);
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("--halt after an error opens the console rather than leaving")
        void anErrorWithHaltOpensTheConsole() throws IOException {
            String script = aScript("n.r3", """
                    Rebol []
                    """);

            Ran ran = typing("print 2\n", "-h", "--do", "1 / 0", script);

            assertThat(ran.reported()).isEqualTo("""

                    ** Math error: attempt to divide by zero
                    ** Where: / do if -apply-
                    ** Near: / 0

                    """);
            assertThat(ran.printed()).isEqualTo(">> 2\n>> ");
            assertThat(ran.exitStatus()).isEqualTo(Repl.KEEP_THE_PROCESS);
        }

        @Test
        @DisplayName("SECURE's bulk form works, its policy words being bound to their object")
        void secureAllowWorks() {
            Ran ran = running("--do", "secure allow probe 1");

            assertThat(ran.printed()).isEqualTo("1\n");
            assertThat(ran.exitStatus()).isZero();
        }
    }

    @Nested
    @DisplayName("JEBOL's own switches")
    class JebolsOwnSwitches {

        @Test
        @DisplayName("a relative script under --root is found, wherever the directory's real path lies")
        void aRelativeScriptUnderARootIsFound() throws IOException {
            aScript("r.r3", """
                    Rebol []
                    print {found}
                    """);

            Ran ran = running("--root", directory.toString(), "r.r3");

            assertThat(ran.printed()).isEqualTo("found\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("the launcher is visible and readable inside a root, so start resolves it and says nothing")
        void theLauncherIsReadableInsideARoot() {
            Ran ran = running("--root", directory.toString(), "--do", """
                    b: system/options/boot
                    probe exists? b
                    probe (file-checksum b 'md5) = checksum read b 'md5
                    probe 0 < length? read b""");

            assertThat(ran.printed()).isEqualTo("file\n#(true)\n#(true)\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("--data reaches start as REBOL_HOME, so the data folder is the one named")
        void theDataSwitchIsRebolHome() throws IOException {
            Files.createDirectories(directory.resolve("kept"));

            Ran ran = running("--root", directory.toString(), "--data", "/kept/", "--do", """
                    probe system/options/data probe system/options/modules""");

            assertThat(ran.printed()).isEqualTo("%/kept/\n%/kept/modules/\n");
        }
    }
}
