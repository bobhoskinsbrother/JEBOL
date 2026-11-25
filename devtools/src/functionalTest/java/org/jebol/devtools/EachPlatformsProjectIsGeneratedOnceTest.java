package org.jebol.devtools;

import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EachPlatformsProjectIsGeneratedOnceTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("generateProjects writes a project for each platform into the app's directory, each with its own files folder")
    void eachPlatformGetsAProject() {
        AnAppBuild build = AnAppBuild.of(ATestApp.smallestIn(directory));

        build.run("generateProjects");

        for (String platform : new String[]{"web", "android", "linux", "macos", "windows"}) {
            assertThat(directory.resolve(platform)).isDirectory();
            assertThat(directory.resolve(platform).resolve("files")).isDirectory();
        }
        assertThat(directory.resolve("web/index.html")).isRegularFile();
        assertThat(directory.resolve("web/manifest.webmanifest")).isRegularFile();
        assertThat(directory.resolve("linux/app.desktop")).isRegularFile();
        assertThat(directory.resolve("macos/Info.plist")).isRegularFile();
        assertThat(directory.resolve("windows/os-condition.wxf")).isRegularFile();
    }

    @Test
    @DisplayName("the Windows installer's Start-menu shortcut carries the app's identity as its AppUserModelID, which jpackage cannot set")
    void theWindowsShortcutCarriesTheIdentity() {
        AnAppBuild build = AnAppBuild.of(ATestApp.smallestIn(directory));

        build.run("generateProjects");

        assertThat(build.read("windows/os-condition.wxf"))
                .contains("""
                        <ShortcutProperty Key="System.AppUserModel.ID" Value="${jebol.identity}"/>""")
                .contains("ProgramMenuFolder")
                .contains("""
                        <ComponentGroup Id="FragmentOsCondition">""");
    }

    @Test
    @DisplayName("a project that is there is the developer's: generating again leaves an edited one exactly as it was")
    void anEditedProjectIsLeftAlone() throws Exception {
        AnAppBuild build = AnAppBuild.of(ATestApp.smallestIn(directory));
        build.run("generateProjects");
        Files.writeString(directory.resolve("web/index.html"), "<p>mine</p>");
        Files.delete(directory.resolve("linux/app.desktop"));

        build.run("generateProjects");

        assertThat(directory.resolve("web/index.html")).hasContent("<p>mine</p>");
        assertThat(directory.resolve("linux/app.desktop")).doesNotExist();
    }

    @Test
    @DisplayName("a generated project holds no shared value: the title, the identity and the version stay placeholders")
    void aProjectHoldsNoSharedValue() {
        AnAppBuild build = AnAppBuild.of(ATestApp.smallestIn(directory));

        build.run("generateProjects");

        assertThat(build.read("web/manifest.webmanifest")).contains("${jebol.title}").doesNotContain("Notes");
        assertThat(build.read("macos/Info.plist")).contains("${jebol.identity}").doesNotContain("com.example.notes");
    }

    @Test
    @DisplayName("a wrong app.reb is reported whole, and nothing is generated")
    void aWrongAppRebGeneratesNothing() {
        AnAppBuild build = AnAppBuild.of(ATestApp.smallestIn(directory).withAppReb("""
                title: "Notes"
                id: notes
                version: 1.2.3
                """));

        BuildResult failed = build.fail("generateProjects");

        assertThat(failed.getOutput())
                .contains("id: expected a word of two or more dotted parts, like com.example.notes")
                .contains("build: expected an integer!")
                .contains("icon: expected a file! naming a square PNG of at least 1024 pixels")
                .contains("contact: expected an email!");
        assertThat(directory.resolve("web")).doesNotExist();
    }
}
