package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class APackagedAppsStoreIsWhereItsSystemKeepsAppDataTest {

    private static final String IDENTITY = "com.example.notes";

    @Test
    @DisplayName("on Linux the store is under XDG_DATA_HOME, named by the identity")
    void linuxUsesXdgDataHome() {
        assertThat(PackagedApp.storeFolder(IDENTITY, "Linux", Map.of("XDG_DATA_HOME", "/data", "HOME", "/home/u"), "/x"))
                .isEqualTo(Path.of("/data", IDENTITY));
    }

    @Test
    @DisplayName("on Linux with no XDG_DATA_HOME the store is under ~/.local/share, HOME first")
    void linuxFallsBackToLocalShare() {
        assertThat(PackagedApp.storeFolder(IDENTITY, "Linux", Map.of("HOME", "/home/u"), "/x"))
                .isEqualTo(Path.of("/home/u/.local/share", IDENTITY));
        assertThat(PackagedApp.storeFolder(IDENTITY, "Linux", Map.of(), "/home/v"))
                .isEqualTo(Path.of("/home/v/.local/share", IDENTITY));
    }

    @Test
    @DisplayName("an XDG_DATA_HOME that is not absolute is ignored, as the XDG specification says")
    void aRelativeXdgDataHomeIsIgnored() {
        assertThat(PackagedApp.storeFolder(IDENTITY, "Linux", Map.of("XDG_DATA_HOME", "data", "HOME", "/home/u"), "/x"))
                .isEqualTo(Path.of("/home/u/.local/share", IDENTITY));
    }

    @Test
    @DisplayName("an XDG_DATA_HOME is absolute by the XDG specification's rule, whatever system asks, so a drive path is ignored")
    void anXdgDataHomeIsAbsoluteByItsOwnRule() {
        assertThat(PackagedApp.storeFolder(IDENTITY, "Linux", Map.of("XDG_DATA_HOME", "C:\\data", "HOME", "/home/u"), "/x"))
                .isEqualTo(Path.of("/home/u/.local/share", IDENTITY));
    }

    @Test
    @DisplayName("on macOS the store is in ~/Library/Application Support")
    void macUsesApplicationSupport() {
        assertThat(PackagedApp.storeFolder(IDENTITY, "Mac OS X", Map.of("HOME", "/Users/u"), "/x"))
                .isEqualTo(Path.of("/Users/u/Library/Application Support", IDENTITY));
    }

    @Test
    @DisplayName("on Windows the store is in APPDATA")
    void windowsUsesAppData() {
        assertThat(PackagedApp.storeFolder(IDENTITY, "Windows 11", Map.of("APPDATA", "/C/Users/u/AppData/Roaming"), "/x"))
                .isEqualTo(Path.of("/C/Users/u/AppData/Roaming", IDENTITY));
    }
}
