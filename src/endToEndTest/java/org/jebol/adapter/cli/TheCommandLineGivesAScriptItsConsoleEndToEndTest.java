package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheCommandLineGivesAScriptItsConsoleEndToEndTest {

    @TempDir
    Path directory;

    private record Ran(int exitStatus, String printed, String reported) {
    }

    private Ran runScript(String script, String typed) throws IOException {
        Path written = directory.resolve("script.r3");
        Files.writeString(written, script);
        return run(typed, "-qs", written.toString());
    }

    private Ran run(String typed, String... arguments) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runCommandLine(arguments,
                new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8), reported.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a script's ASK reads the line typed, and INPUT at the end of input is an empty string")
    void readsWhatIsTyped() throws IOException {
        Ran ran = runScript("""
                Rebol []
                probe ask {Q? }
                probe input
                """, "hello\n");

        assertThat(ran.printed()).isEqualTo("Q? \"hello\"\n\"\"\n");
        assertThat(ran.reported()).isEmpty();
    }

    @Test
    @DisplayName("ASK/HIDE reads to a CR and prints a line feed after")
    void readsAPassword() throws IOException {
        Ran ran = runScript("""
                Rebol []
                probe ask/hide {P? }
                """, "s3cret\r");

        assertThat(ran.printed()).isEqualTo("P? \n\"s3cret\"\n");
    }

    @Test
    @DisplayName("through a pipe the console is not a terminal")
    void isNotATerminal() throws IOException {
        assertThat(runScript("""
                Rebol []
                print tty?
                """, "").printed()).isEqualTo("false\n");
    }

    @Test
    @DisplayName("echo writes its escapes, and error sends what is printed to the error stream")
    void honoursEchoAndError() throws IOException {
        Ran ran = runScript("""
                Rebol []
                modify system/ports/output 'echo false
                print {hidden}
                modify system/ports/output 'echo true
                modify system/ports/output 'error true
                print {complained}
                modify system/ports/output 'error false
                print {back}
                """, "");

        assertThat(ran.printed()).isEqualTo("\u001b[8mhidden\n\u001b[28mback\n");
        assertThat(ran.reported()).isEqualTo("complained\n");
    }

    @Test
    @DisplayName("ASK at the prompt reads the next line typed, and the prompt carries on after it")
    void asksAtThePrompt() {
        Ran ran = run("""
                x: ask {Q? }
                yes
                print x
                """, "-q");

        assertThat(ran.printed()).contains("Q? ", "yes\n");
        assertThat(ran.printed()).doesNotContain("yes has no value");
    }
}
