package org.jebol.adapter.host;

import org.jebol.domain.host.FilePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ANameNoFileCanHaveIsAbsentTest {

    private static final String NAME_NO_SYSTEM_HOLDS = "a\0b.txt";

    @Test
    @DisplayName("asked whether it exists, a name the system cannot hold is not there, as R3's query finds nothing")
    void isNotThere(@TempDir Path root) {
        assertThat(FileSystemPort.rootFileSystem(root).exists(NAME_NO_SYSTEM_HOLDS)).isFalse();
    }

    @Test
    @DisplayName("asked to describe it, a name the system cannot hold has nothing to describe")
    void describesNothing(@TempDir Path root) {
        assertThat(FileSystemPort.rootFileSystem(root).describe(NAME_NO_SYSTEM_HOLDS)).isEmpty();
    }

    @Test
    @DisplayName("asked to read it, a name the system cannot hold is still refused")
    void refusesReading(@TempDir Path root) {
        assertThatThrownBy(() -> FileSystemPort.rootFileSystem(root).readBytes(NAME_NO_SYSTEM_HOLDS))
                .isInstanceOf(FilePort.Denied.class);
    }
}
