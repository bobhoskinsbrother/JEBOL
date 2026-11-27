package org.jebol.adapter.host;

import org.jebol.domain.host.FilePort;
import org.jebol.testing.RunsOn;
import org.jebol.testing.TestTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunsOn(value = TestTarget.DESKTOP_WINDOWS, because = "only Windows refuses to delete a file another handle holds open")
class AFileHeldOpenOnWindowsStaysTest {

    @TempDir
    Path machine;

    @Test
    @DisplayName("on Windows a file the port holds open cannot be deleted, and once let go it can")
    void refusesDeletingAHeldFileUntilLetGo() {
        FileSystemPort files = FileSystemPort.rootFileSystem(machine);
        files.write("/held.txt", "a".getBytes(StandardCharsets.UTF_8));

        FilePort.Held held = files.holdOpen("/held.txt");
        assertThatThrownBy(() -> files.delete("/held.txt")).isInstanceOf(FilePort.Denied.class);
        held.close();

        assertThat(files.delete("/held.txt")).isTrue();
    }

    @Test
    @DisplayName("letting go of every file the port holds lets each be deleted, whatever was never closed")
    void lettingGoOfEveryHeldFileLetsThemGo() {
        FileSystemPort files = FileSystemPort.rootFileSystem(machine);
        files.write("/a.txt", "a".getBytes(StandardCharsets.UTF_8));
        files.write("/b.txt", "b".getBytes(StandardCharsets.UTF_8));
        files.holdOpen("/a.txt");
        files.holdOpen("/b.txt");

        files.letGoOfEveryHeldFile();

        assertThat(files.delete("/a.txt")).isTrue();
        assertThat(files.delete("/b.txt")).isTrue();
    }
}
