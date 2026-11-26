package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class APackagedAppIsToldItsIconTest {

    @Test
    @DisplayName("a packaged app started with --icon knows where its own icon is")
    void readsItsIcon() {
        PackagedApp app = PackagedApp.readFrom(new String[]{
                "--packaged", "com.example.notes", "--carried", "/opt/notes/jebol-app",
                "--icon", "/opt/notes/jebol-app-icon.png", "main.reb"}).orElseThrow();

        assertThat(app.icon()).contains(Path.of("/opt/notes/jebol-app-icon.png"));
    }

    @Test
    @DisplayName("one packaged before --icon existed starts as it did, with no icon of its own")
    void startsWithoutOne() {
        PackagedApp app = PackagedApp.readFrom(new String[]{
                "--packaged", "com.example.notes", "--carried", "/opt/notes/jebol-app", "main.reb"}).orElseThrow();

        assertThat(app.icon()).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("--icon and the file after it are the packager's, so the script never sees them")
    void keepsTheSwitchFromTheScript() {
        assertThat(PackagedApp.isOwnSwitch("--icon")).isTrue();
    }
}
