package org.jebol.adapter.host;

import org.jebol.domain.host.FilePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledOnOs(OS.WINDOWS)
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
}
