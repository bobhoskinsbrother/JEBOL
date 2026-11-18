package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.FileValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AStoreOverCarriedFilesTest {

    private static final Map<String, String> CARRIED = Map.of(
            "main.reb", "carried main",
            "lib/util.reb", "carried util",
            "lib/deeper/more.reb", "carried more");

    @TempDir
    Path storeFolder;

    private LayeredFiles files() {
        return new LayeredFiles(FileSystemPort.rootFileSystem(storeFolder),
                ListedFiles.of(List.copyOf(CARRIED.keySet()),
                        name -> CARRIED.get(name).getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(FilePort files, String path) {
        return new String(files.readBytes(path), StandardCharsets.UTF_8);
    }

    private List<Path> storeHolds() throws IOException {
        try (Stream<Path> walked = Files.walk(storeFolder)) {
            return walked.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    @DisplayName("a carried file reads where the store has none, and reading copies nothing into the store")
    void readsTheCarriedFile() throws IOException {
        LayeredFiles files = files();

        assertThat(text(files, "/main.reb")).isEqualTo("carried main");
        assertThat(text(files, "lib/util.reb")).isEqualTo("carried util");
        assertThat(storeHolds()).isEmpty();
    }

    @Test
    @DisplayName("a file in the store shadows the carried one of the same name")
    void theStoreShadowsTheCarriedFile() {
        LayeredFiles files = files();
        files.write("/main.reb", bytes("my own main"));

        assertThat(text(files, "/main.reb")).isEqualTo("my own main");
    }

    @Test
    @DisplayName("a write lands in the store and the carried file is left as it was")
    void aWriteLandsInTheStore() throws IOException {
        LayeredFiles files = files();
        files.write("/notes.txt", bytes("kept"));

        assertThat(Files.readString(storeFolder.resolve("notes.txt"))).isEqualTo("kept");
        assertThat(text(files, "/notes.txt")).isEqualTo("kept");
    }

    @Test
    @DisplayName("a write into a folder only the APK carries makes that folder in the store")
    void aWriteIntoACarriedFolderMakesItInTheStore() throws IOException {
        LayeredFiles files = files();
        files.write("/lib/deeper/mine.txt", bytes("mine"));

        assertThat(Files.readString(storeFolder.resolve("lib/deeper/mine.txt"))).isEqualTo("mine");
        assertThat(text(files, "/lib/deeper/more.reb")).isEqualTo("carried more");
    }

    @Test
    @DisplayName("a write into a folder neither has is refused as the store refuses it")
    void aWriteIntoNoFolderIsRefused() {
        LayeredFiles files = files();

        assertThatThrownBy(() -> files.write("/nowhere/at-all.txt", bytes("x")))
                .isInstanceOf(FilePort.Denied.class);
    }

    @Test
    @DisplayName("a folder lists the store's names and the carried names, each once and in order")
    void aFolderListsBoth() {
        LayeredFiles files = files();
        files.write("/main.reb", bytes("my own main"));
        files.write("/notes.txt", bytes("kept"));
        files.write("/lib/extra.reb", bytes("extra"));

        assertThat(files.listFileNames("/")).containsExactly("lib/", "main.reb", "notes.txt");
        assertThat(files.listFileNames("/lib/")).containsExactly("deeper/", "extra.reb", "util.reb");
    }

    @Test
    @DisplayName("a carried file and a carried folder exist, and only the folder is a folder")
    void carriedThingsExist() {
        LayeredFiles files = files();

        assertThat(files.exists("/lib/util.reb")).isTrue();
        assertThat(files.exists("/lib/")).isTrue();
        assertThat(files.isDirectory("/lib/")).isTrue();
        assertThat(files.isDirectory("/lib/util.reb")).isFalse();
        assertThat(files.exists("/missing.reb")).isFalse();
    }

    @Test
    @DisplayName("deleting a file only the APK carries is refused as read-only")
    void deletingACarriedFileIsRefused() {
        LayeredFiles files = files();

        assertThatThrownBy(() -> files.delete("/main.reb"))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        denied -> assertThat(denied.errorId()).isEqualTo(FilePort.Denied.READ_ONLY));
        assertThat(text(files, "/main.reb")).isEqualTo("carried main");
    }

    @Test
    @DisplayName("deleting the store's own file of a carried name shows the carried one again")
    void deletingAShadowShowsTheCarriedFile() {
        LayeredFiles files = files();
        files.write("/main.reb", bytes("my own main"));

        assertThat(files.delete("/main.reb")).isTrue();
        assertThat(text(files, "/main.reb")).isEqualTo("carried main");
    }

    @Test
    @DisplayName("deleting what neither has answers false")
    void deletingNothingAnswersFalse() {
        assertThat(files().delete("/missing.reb")).isFalse();
    }

    @Test
    @DisplayName("renaming a file only the APK carries is refused as read-only")
    void renamingACarriedFileIsRefused() {
        LayeredFiles files = files();

        assertThatThrownBy(() -> files.rename("/main.reb", "/other.reb"))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        denied -> assertThat(denied.errorId()).isEqualTo(FilePort.Denied.READ_ONLY));
    }

    @Test
    @DisplayName("changing into a carried folder lets a relative path reach both layers from there")
    void changingIntoACarriedFolder() {
        LayeredFiles files = files();
        files.changeDirectory("/lib/");
        files.write("mine.txt", bytes("mine"));

        assertThat(files.workingDirectory()).isEqualTo("/lib/");
        assertThat(text(files, "util.reb")).isEqualTo("carried util");
        assertThat(text(files, "mine.txt")).isEqualTo("mine");
        assertThat(text(files, "../main.reb")).isEqualTo("carried main");
    }

    @Test
    @DisplayName("changing into a folder neither has is refused")
    void changingIntoNoFolderIsRefused() {
        LayeredFiles files = files();

        assertThatThrownBy(() -> files.changeDirectory("/nowhere/")).isInstanceOf(FilePort.Denied.class);
        assertThat(files.workingDirectory()).isEqualTo("/");
    }

    @Test
    @DisplayName("appending to a carried file writes the carried text and the addition into the store")
    void appendingToACarriedFile() throws IOException {
        LayeredFiles files = files();
        files.appendTo("/main.reb", bytes(" and more"));

        assertThat(Files.readString(storeFolder.resolve("main.reb"))).isEqualTo("carried main and more");
    }

    @Test
    @DisplayName("writing part of a carried file keeps the rest of the carried text")
    void writingPartOfACarriedFile() {
        LayeredFiles files = files();
        files.writeAt("/main.reb", 0, bytes("CARRIED"));

        assertThat(text(files, "/main.reb")).isEqualTo("CARRIED main");
    }

    @Test
    @DisplayName("QUERY of a carried file gives its size and its name as seen from where the script stands")
    void describingACarriedFile() {
        LayeredFiles files = files();
        files.changeDirectory("/lib/");

        Optional<FileInformation> described = files.describe("util.reb");

        assertThat(described).hasValueSatisfying(information -> {
            assertThat(information.name()).isEqualTo("util.reb");
            assertThat(information.size()).contains((long) "carried util".length());
            assertThat(information.isDirectory()).isFalse();
        });
        assertThat(files.describe("/missing.reb")).isEmpty();
    }

    @Test
    @DisplayName("QUERY of a carried folder says it is a folder")
    void describingACarriedFolder() {
        assertThat(files().describe("/lib/deeper/"))
                .hasValueSatisfying(information -> assertThat(information.isDirectory()).isTrue());
    }

    @Test
    @DisplayName("TO-REAL-FILE of a carried file is its path from the root")
    void resolvingACarriedFile() {
        assertThat(files().resolvedFile("lib/util.reb")).isEqualTo(FileValue.of("/lib/util.reb"));
    }

    @Test
    @DisplayName("making a folder inside a carried folder makes the carried folder in the store too")
    void makingAFolderInsideACarriedOne() {
        LayeredFiles files = files();
        files.makeDirectory("/lib/deeper/mine/", false);

        assertThat(Files.isDirectory(storeFolder.resolve("lib/deeper/mine"))).isTrue();
    }

    @Test
    @DisplayName("the listed files alone are read-only and answer only what is listed")
    void theListedFilesAreReadOnly() {
        FilePort listed = ListedFiles.of(List.of("a/b.txt"), name -> bytes("b"));

        assertThat(listed.isDirectory("/a/")).isTrue();
        assertThat(listed.listFileNames("/")).containsExactly("a/");
        assertThat(listed.readBytes("/a/b.txt")).isEqualTo(bytes("b"));
        assertThatThrownBy(() -> listed.write("/a/c.txt", bytes("c")))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        denied -> assertThat(denied.errorId()).isEqualTo(FilePort.Denied.READ_ONLY));
        assertThatThrownBy(() -> listed.readBytes("/a/missing.txt")).isInstanceOf(FilePort.Denied.class);
    }
}
