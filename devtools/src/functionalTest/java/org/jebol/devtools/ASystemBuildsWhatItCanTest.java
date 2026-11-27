package org.jebol.devtools;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jebol.testing.RunsOn;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jebol.testing.TestTarget.DESKTOP_LINUX;
import static org.jebol.testing.TestTarget.DESKTOP_MAC;

class ASystemBuildsWhatItCanTest {

    @TempDir
    Path directory;

    @Test
    @RunsOn(value = DESKTOP_LINUX, because = "jpackage builds for the system it runs on, so this is what Linux builds and refuses")
    @DisplayName("on Linux, packageMac refuses and names the system that builds it")
    void packageMacRefusesOnLinux() {
        BuildResult failed = AnAppBuild.of(ATestApp.smallestIn(directory)).fail("packageMac");

        assertThat(failed.getOutput()).contains("packageMac builds only on macOS, and this is Linux");
    }

    @Test
    @RunsOn(value = DESKTOP_LINUX, because = "jpackage builds for the system it runs on, so this is what Linux builds and refuses")
    @DisplayName("on Linux, packageWindows refuses and names the system that builds it")
    void packageWindowsRefusesOnLinux() {
        BuildResult failed = AnAppBuild.of(ATestApp.smallestIn(directory)).fail("packageWindows");

        assertThat(failed.getOutput()).contains("packageWindows builds only on Windows, and this is Linux");
    }

    @Test
    @RunsOn(value = DESKTOP_LINUX, because = "jpackage builds for the system it runs on, so this is what Linux builds and refuses")
    @DisplayName("on Linux, packageAll builds the web and the .deb, and neither macOS's nor Windows' package; jebol.skip leaves Android out")
    void packageAllBuildsWhatThisMachineCan() {
        BuildResult made = AnAppBuild.of(ATestApp.smallestIn(directory)).run("packageAll", "-Pjebol.skip=android");

        assertThat(made.task(":packageWeb").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(made.task(":packageDeb").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(made.task(":packageMac")).isNull();
        assertThat(made.task(":packageWindows")).isNull();
        assertThat(made.task(":packageAndroid")).isNull();
        assertThat(directory.resolve("build/jebol/web/site/index.html")).isRegularFile();
    }

    @Test
    @RunsOn(value = DESKTOP_MAC, because = "jpackage builds for the system it runs on, so this is what macOS builds and refuses")
    @DisplayName("on macOS, packageDeb refuses and names the system that builds it")
    void packageDebRefusesOnAMac() {
        BuildResult failed = AnAppBuild.of(ATestApp.smallestIn(directory)).fail("packageDeb");

        assertThat(failed.getOutput()).contains("packageDeb builds only on Linux, and this is macOS");
    }

    @Test
    @RunsOn(value = DESKTOP_MAC, because = "jpackage builds for the system it runs on, so this is what macOS builds and refuses")
    @DisplayName("on macOS, packageWindows refuses and names the system that builds it")
    void packageWindowsRefusesOnAMac() {
        BuildResult failed = AnAppBuild.of(ATestApp.smallestIn(directory)).fail("packageWindows");

        assertThat(failed.getOutput()).contains("packageWindows builds only on Windows, and this is macOS");
    }

    @Test
    @RunsOn(value = DESKTOP_MAC, because = "jpackage builds for the system it runs on, so this is what macOS builds and refuses")
    @DisplayName("on macOS, packageAll builds the web and the .app, and neither Linux's nor Windows' package; jebol.skip leaves Android out")
    void packageAllBuildsWhatAMacCan() {
        BuildResult made = AnAppBuild.of(ATestApp.smallestIn(directory)).run("packageAll", "-Pjebol.skip=android");

        assertThat(made.task(":packageWeb").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(made.task(":packageMac").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(made.task(":packageDeb")).isNull();
        assertThat(made.task(":packageWindows")).isNull();
        assertThat(made.task(":packageAndroid")).isNull();
        assertThat(directory.resolve("build/jebol/web/site/index.html")).isRegularFile();
    }
}
