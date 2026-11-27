package org.jebol.adapter.cli;

import org.jebol.testing.RunsOn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jebol.testing.TestTarget.DESKTOP_LINUX;
import static org.jebol.testing.TestTarget.DESKTOP_MAC;

@RunsOn(value = {DESKTOP_LINUX, DESKTOP_MAC}, because = "Windows asks its console, not a device file")
class StandardInputThatCanBeATerminalTest {

    private static final Path NOTHING = Path.of("/dev/null");

    @Test
    @DisplayName("input read from a file is never a terminal, so nothing need be started to ask")
    void aFileIsNotATerminal(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("typed.txt"), "print 1");

        assertThat(StandardInput.canBeATerminal(file, NOTHING)).isFalse();
    }

    @Test
    @DisplayName("input from /dev/null is never a terminal, though it is a character device as a terminal is")
    void nothingIsNotATerminal() {
        assertThat(StandardInput.canBeATerminal(NOTHING, NOTHING)).isFalse();
    }

    @Test
    @DisplayName("a pipe is never a terminal")
    void aPipeIsNotATerminal(@TempDir Path directory) throws Exception {
        Path pipe = directory.resolve("pipe");
        assertThat(new ProcessBuilder("mkfifo", pipe.toString()).start().waitFor()).isZero();

        assertThat(StandardInput.canBeATerminal(pipe, NOTHING)).isFalse();
    }

    @Test
    @DisplayName("any other character device may be one, and is worth asking about")
    void anotherCharacterDeviceMayBeATerminal() {
        assertThat(StandardInput.canBeATerminal(Path.of("/dev/zero"), NOTHING)).isTrue();
    }

    @Test
    @DisplayName("input whose kind cannot be read may be a terminal, and is asked about as before")
    void anUnreadableInputMayBeATerminal(@TempDir Path directory) {
        assertThat(StandardInput.canBeATerminal(directory.resolve("not there"), NOTHING)).isTrue();
    }
}
