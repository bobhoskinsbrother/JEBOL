package org.jebol.devtools;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ASystemBuildsWhatItCanTest {

    @TempDir
    Path directory;

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("on Linux, packageMac refuses and names the system that builds it")
    void packageMacRefusesOnLinux() {
        BuildResult failed = AnAppBuild.of(ATestApp.smallestIn(directory)).fail("packageMac");

        assertThat(failed.getOutput()).contains("packageMac builds only on macOS, and this is Linux");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("on Linux, packageWindows refuses and names the system that builds it")
    void packageWindowsRefusesOnLinux() {
        BuildResult failed = AnAppBuild.of(ATestApp.smallestIn(directory)).fail("packageWindows");

        assertThat(failed.getOutput()).contains("packageWindows builds only on Windows, and this is Linux");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
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
    @EnabledOnOs(OS.MAC)
    @DisplayName("on macOS, packageDeb refuses and names the system that builds it")
    void packageDebRefusesOnAMac() {
        BuildResult failed = AnAppBuild.of(ATestApp.smallestIn(directory)).fail("packageDeb");

        assertThat(failed.getOutput()).contains("packageDeb builds only on Linux, and this is macOS");
    }

    @Test
    @EnabledOnOs(OS.MAC)
    @DisplayName("on macOS, packageWindows refuses and names the system that builds it")
    void packageWindowsRefusesOnAMac() {
        BuildResult failed = AnAppBuild.of(ATestApp.smallestIn(directory)).fail("packageWindows");

        assertThat(failed.getOutput()).contains("packageWindows builds only on Windows, and this is macOS");
    }

    @Test
    @EnabledOnOs(OS.MAC)
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
