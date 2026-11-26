package org.jebol.devtools;

import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(600)
@EnabledOnOs(value = OS.LINUX, disabledReason = "jpackage builds a .deb only on Linux")
class TheLinuxPackageTest {

    @TempDir
    static Path directory;

    private static Path deb;
    private static BuildResult made;

    @BeforeAll
    static void packageTheApp() throws IOException {
        AnAppBuild build = AnAppBuild.of(ATestApp.completeIn(directory));
        made = build.run("packageDeb");
        try (Stream<Path> found = Files.list(directory.resolve("build/jebol/linux/package"))) {
            deb = found.filter(path -> path.toString().endsWith(".deb")).findFirst().orElseThrow();
        }
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process running = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(running.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(running.waitFor()).as(String.join(" ", command) + "\n" + output).isZero();
        return output;
    }

    @Test
    @DisplayName("the .deb is named for the identity and the version, and the build says where it is")
    void theDebIsNamedForTheIdentity() {
        assertThat(deb.getFileName().toString()).isEqualTo("com.example.notes_1.2.3_amd64.deb");
        assertThat(made.getOutput()).contains(deb.getFileName().toString());
    }

    @Test
    @DisplayName("its control file carries the identity, the version, the summary, and a maintainer made of publisher and contact")
    void theControlFileCarriesTheDeclaration() throws Exception {
        String control = run("dpkg-deb", "-I", deb.toString());

        assertThat(control)
                .contains("Package: com.example.notes")
                .contains("Version: 1.2.3")
                .contains("Maintainer: Example Ltd <dev@example.com>")
                .contains("Description: Quick notes that sync nowhere");
    }

    @Test
    @DisplayName("it carries the app's scripts, JEBOL, SQLite as the app requires, a Java runtime and a desktop entry named for the identity")
    void itCarriesTheApp() throws Exception {
        List<String> contents = run("dpkg-deb", "-c", deb.toString()).lines()
                .map(line -> line.substring(line.indexOf("./"))).toList();

        assertThat(contents).contains(
                "./opt/com.example.notes/bin/notes",
                "./opt/com.example.notes/lib/app/jebol.jar",
                "./opt/com.example.notes/lib/app/sqlite4j-3.54.1.0.jar",
                "./opt/com.example.notes/lib/app/jebol-sqlite.jar",
                "./opt/com.example.notes/lib/app/jebol-app/main.reb",
                "./opt/com.example.notes/lib/app/jebol-app-icon.png",
                "./opt/com.example.notes/lib/app/com.example.notes.desktop",
                "./opt/com.example.notes/lib/notes.png",
                "./opt/com.example.notes/lib/runtime/bin/java");
        assertThat(contents).noneMatch(path -> path.endsWith("/app.reb"));
    }

    @Test
    @DisplayName("its desktop entry is filled from app.reb, and installing puts it in the menu under the identity")
    void itsDesktopEntryIsTheIdentity() throws Exception {
        Path unpacked = directory.resolve("unpacked");
        run("dpkg-deb", "-x", deb.toString(), unpacked.toString());
        Path control = directory.resolve("control");
        run("dpkg-deb", "-e", deb.toString(), control.toString());

        assertThat(unpacked.resolve("opt/com.example.notes/lib/app/com.example.notes.desktop")).content()
                .contains("Name=Notes")
                .contains("Exec=/opt/com.example.notes/bin/notes")
                .contains("Categories=Office;");
        assertThat(control.resolve("postinst")).content()
                .contains("xdg-desktop-menu install --novendor /opt/com.example.notes/lib/app/com.example.notes.desktop");
        assertThat(control.resolve("prerm")).content()
                .contains("xdg-desktop-menu uninstall --novendor");
    }

    @Test
    @DisplayName("its launcher starts JEBOL as the packaged app, from the declared entry, with a window")
    void itsLauncherStartsThePackagedApp() throws Exception {
        Path unpacked = directory.resolve("launcher");
        run("dpkg-deb", "-x", deb.toString(), unpacked.toString());

        assertThat(unpacked.resolve("opt/com.example.notes/lib/app/notes.cfg")).content()
                .contains("arguments=--packaged")
                .contains("arguments=com.example.notes")
                .contains("arguments=--carried")
                .contains("arguments=$APPDIR/jebol-app")
                .contains("arguments=--screen=window")
                .contains("arguments=main.reb");
    }

    @Test
    @DisplayName("and tells the app where its own icon is, so its windows wear the app's icon rather than JEBOL's")
    void itsLauncherNamesTheAppsIcon() throws Exception {
        Path unpacked = directory.resolve("icon");
        run("dpkg-deb", "-x", deb.toString(), unpacked.toString());

        assertThat(unpacked.resolve("opt/com.example.notes/lib/app/notes.cfg")).content()
                .contains("arguments=--icon")
                .contains("arguments=$APPDIR/jebol-app-icon.png");
        assertThat(unpacked.resolve("opt/com.example.notes/lib/app/jebol-app-icon.png")).isRegularFile();
    }
}
