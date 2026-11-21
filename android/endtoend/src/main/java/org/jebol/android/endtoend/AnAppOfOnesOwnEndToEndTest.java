package org.jebol.android.endtoend;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AnAppOfOnesOwnEndToEndTest {

    private static final String IDENTITY = "org.jebol.example.notes";
    private static final Duration PATIENCE = Duration.ofSeconds(60);

    private TheApp app;

    @AfterEach
    void endTheApp() {
        if (app != null) {
            app.endProcess();
        }
    }

    @Test
    @DisplayName("the example app jebol-devtools packaged is installed under its own identity, version and label")
    void itIsInstalledUnderItsOwnIdentity() throws PackageManager.NameNotFoundException {
        PackageManager packages = InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();

        PackageInfo installed = packages.getPackageInfo(IDENTITY, 0);

        assertThat(installed.versionName).isEqualTo("1.0.0");
        assertThat(installed.getLongVersionCode()).isEqualTo(1L);
        assertThat(packages.getApplicationLabel(installed.applicationInfo).toString()).isEqualTo("Notes");
    }

    @Test
    @DisplayName("the example app opens from its launcher, runs its own script, and hears a tap on its button")
    void itRunsItsOwnScript() {
        app = TheApp.launchedAfreshFromPackage(IDENTITY);
        app.waitForLineStarting("open", PATIENCE);
        String line = app.waitForLineStarting("button-at ", PATIENCE);
        int[] button = app.readPairAfter(line, "button-at ");

        app.tapAt(button);

        assertThat(app.waitForLineStarting("notes 1", PATIENCE)).isEqualTo("notes 1");
    }
}
