package org.jebol.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheWebPackageTest {

    private static final String SITE = "build/jebol/web/site/";

    @TempDir
    Path directory;

    private static String quoted(String text) {
        return '"' + text + '"';
    }

    @Test
    @DisplayName("packageWeb makes a site of the app, the runtime, the page, the manifest and its icons")
    void packageWebMakesASite() throws Exception {
        AnAppBuild build = AnAppBuild.of(ATestApp.completeIn(directory).withFile("notes/list.reb", "REBOL []"));

        build.run("packageWeb");

        Path site = directory.resolve(SITE);
        assertThat(site.resolve("main.reb")).isRegularFile();
        assertThat(site.resolve("notes/list.reb")).isRegularFile();
        assertThat(site.resolve("jebol-web.wasm")).isRegularFile();
        assertThat(site.resolve("jebol-runner.js")).isRegularFile();
        assertThat(site.resolve("NotoSans-Regular.ttf")).isRegularFile();
        assertThat(site.resolve("app.reb")).doesNotExist();
        assertThat(site.resolve("build.gradle.kts")).doesNotExist();
        assertThat(build.read(SITE + "index.html")).contains("<title>Notes</title>")
                .contains("""
                        <link rel="manifest" href="manifest.webmanifest">""");
        assertThat(build.read(SITE + "manifest.webmanifest"))
                .contains("""
                        "name": "Notes",""")
                .contains("""
                        "id": "com.example.notes",""")
                .contains("""
                        "theme_color": "#0078d7",""");
        BufferedImage large = ImageIO.read(site.resolve("icons/icon-512.png").toFile());
        assertThat(large.getWidth()).isEqualTo(512);
        assertThat(ImageIO.read(site.resolve("icons/icon-192.png").toFile()).getWidth()).isEqualTo(192);
    }

    @Test
    @DisplayName("app.json names the app's files and the entry app.reb declares")
    void appJsonNamesTheEntry() {
        AnAppBuild build = AnAppBuild.of(ATestApp.smallestIn(directory)
                .withAppReb(ATestApp.SMALLEST_APP_REB + "entry: %start/notes.reb\n")
                .withFile("start/notes.reb", ATestApp.MAIN_SCRIPT));

        build.run("packageWeb");

        assertThat(build.read(SITE + "app.json"))
                .contains(quoted("start/notes.reb"))
                .contains(quoted("main.reb"))
                .endsWith("""
                        "entry":"start/notes.reb"}""")
                .doesNotContain(quoted("app.reb"));
    }

    @Test
    @DisplayName("a file in web/files wins over the shared file of the same name")
    void aPlatformsFileWins() throws Exception {
        AnAppBuild build = AnAppBuild.of(ATestApp.smallestIn(directory));
        build.run("generateProjects");
        Files.writeString(directory.resolve("web/files/main.reb"), "REBOL [] print {the page's own}");

        build.run("packageWeb");

        assertThat(build.read(SITE + "main.reb")).contains("the page's own");
    }

    @Test
    @DisplayName("an edit to app.reb reaches the next package with nothing regenerated")
    void anEditToAppRebReachesThePackage() throws Exception {
        AnAppBuild build = AnAppBuild.of(ATestApp.smallestIn(directory));
        build.run("packageWeb");
        Files.writeString(directory.resolve("app.reb"),
                ATestApp.SMALLEST_APP_REB.replace("Notes", "Jottings"));

        build.run("packageWeb");

        assertThat(build.read(SITE + "index.html")).contains("<title>Jottings</title>");
        assertThat(build.read(SITE + "manifest.webmanifest")).contains("""
                "name": "Jottings",""");
        assertThat(build.read("web/manifest.webmanifest")).contains("${jebol.title}");
    }

    @Test
    @DisplayName("SQLite's page files are in the site only when app.reb requires SQLite")
    void sqliteIsCarriedOnlyWhenRequired() {
        AnAppBuild plain = AnAppBuild.of(ATestApp.smallestIn(directory.resolve("plain")));
        plain.run("packageWeb");
        AnAppBuild requiring = AnAppBuild.of(ATestApp.completeIn(directory.resolve("requiring")));
        requiring.run("packageWeb");

        assertThat(directory.resolve("plain/" + SITE + "sqlite3.wasm")).doesNotExist();
        assertThat(directory.resolve("requiring/" + SITE + "sqlite3.wasm")).isRegularFile();
        assertThat(directory.resolve("requiring/" + SITE + "jebol-sqlite.js")).isRegularFile();
    }
}
