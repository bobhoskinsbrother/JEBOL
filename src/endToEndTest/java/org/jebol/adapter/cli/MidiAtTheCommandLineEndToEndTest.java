package org.jebol.adapter.cli;

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

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class MidiAtTheCommandLineEndToEndTest {

    @TempDir
    Path app;

    private record Ran(int exitStatus, String printed, String reported) {
    }

    private Ran run(String source) throws IOException {
        Path script = app.resolve("script.r3");
        Files.writeString(script, "Rebol []\n" + source);
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runCommandLine(new String[] {script.toString()},
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                app.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a script finds the midi scheme registered after serial")
    void offersTheScheme() throws IOException {
        Ran ran = run("""
                print second find words-of system/schemes 'serial""");

        assertThat(ran.reported()).isEmpty();
        assertThat(ran.printed()).isEqualTo("midi\n");
        assertThat(ran.exitStatus()).isZero();
    }

    @Test
    @DisplayName("a script lists the instruments, and with none attached both lists are empty")
    void listsTheInstruments() throws IOException {
        Ran ran = run("""
                print mold query midi:// [devices-in devices-out]""");

        assertThat(ran.reported()).isEmpty();
        assertThat(ran.printed()).isEqualTo("[\n    devices-in: []\n    devices-out: []\n]\n");
        assertThat(ran.exitStatus()).isZero();
    }

    @Test
    @DisplayName("a script that tries an instrument and catches the refusal carries on")
    void triesAnInstrument() throws IOException {
        Ran ran = run("""
                failure: try [open midi://1]
                print [failure/id failure/arg1 failure/arg2]
                print {carried on}""");

        assertThat(ran.reported()).isEmpty();
        assertThat(ran.printed()).isEqualTo("cannot-open midi://1 3\ncarried on\n");
        assertThat(ran.exitStatus()).isZero();
    }

    @Test
    @DisplayName("a script that plays a note without opening a port stops with the refusal, and exit status 1")
    void playsWithoutOpening() throws IOException {
        Ran ran = run("""
                print {start}
                write midi://0/1 #{903C4000}
                print {never}""");

        assertThat(ran.printed()).isEqualTo("start\n");
        assertThat(ran.reported()).contains("** Access error: port is not open: midi://0/1");
        assertThat(ran.exitStatus()).isEqualTo(1);
    }
}
