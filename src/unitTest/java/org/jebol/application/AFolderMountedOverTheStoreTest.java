package org.jebol.application;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.FileValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AFolderMountedOverTheStoreTest {

    private final MemoryFiles store = new MemoryFiles();
    private final MemoryFiles picked = new MemoryFiles();
    private final MountedFiles files = new MountedFiles(store, "/picked/", picked);

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(FilePort port, String path) {
        return new String(port.readBytes(path), StandardCharsets.UTF_8);
    }

    private void pickFile(String path, String contents) {
        picked.makeDirectory(ScriptPaths.parent(path), true);
        picked.write(path, bytes(contents));
    }

    @Test
    @DisplayName("a path under the mount reaches the mounted port and nothing lands in the store")
    void aPathUnderTheMountReachesTheMount() {
        pickFile("/picked/1/notes.txt", "the user's");

        files.write("/picked/1/notes.txt", bytes("edited"));

        assertThat(text(picked, "/picked/1/notes.txt")).isEqualTo("edited");
        assertThat(store.exists("/picked/")).isFalse();
        assertThat(text(files, "/picked/1/notes.txt")).isEqualTo("edited");
    }

    @Test
    @DisplayName("a path outside the mount reaches the store, and the mount sees none of it")
    void aPathOutsideReachesTheStore() {
        files.write("notes.txt", bytes("mine"));

        assertThat(text(store, "/notes.txt")).isEqualTo("mine");
        assertThat(picked.exists("/notes.txt")).isFalse();
    }

    @Test
    @DisplayName("a relative path counts from the working directory before it is routed")
    void aRelativePathIsRoutedFromTheWorkingDirectory() {
        pickFile("/picked/2/a.txt", "in the mount");
        store.makeDirectory("/inner/", false);
        store.changeDirectory("/inner/");

        assertThat(text(files, "../picked/2/a.txt")).isEqualTo("in the mount");
    }

    @Test
    @DisplayName("change-dir into the mount makes relative paths count from there, and what-dir says so")
    void changeDirectoryIntoTheMount() {
        pickFile("/picked/3/Docs/a.txt", "a");

        files.changeDirectory("/picked/3/Docs/");

        assertThat(files.workingDirectory()).isEqualTo("/picked/3/Docs/");
        assertThat(text(files, "a.txt")).isEqualTo("a");
        assertThat(files.listFileNames("")).containsExactly("a.txt");
        files.write("b.txt", bytes("b"));
        assertThat(text(picked, "/picked/3/Docs/b.txt")).isEqualTo("b");
    }

    @Test
    @DisplayName("change-dir back out of the mount returns relative paths to the store")
    void changeDirectoryBackOut() {
        pickFile("/picked/3/Docs/a.txt", "a");
        store.makeDirectory("/home/", false);
        files.changeDirectory("/picked/3/Docs/");

        files.changeDirectory("../../../home/");
        files.write("c.txt", bytes("c"));

        assertThat(files.workingDirectory()).isEqualTo("/home/");
        assertThat(text(store, "/home/c.txt")).isEqualTo("c");
    }

    @Test
    @DisplayName("a copy over another store keeps a working directory inside the mount, as a task starts where its parent was")
    void aCopyKeepsTheWorkingDirectoryInTheMount() {
        pickFile("/picked/3/Docs/a.txt", "a");
        files.changeDirectory("/picked/3/Docs/");

        MountedFiles copy = files.copyOver(new MemoryFiles());

        assertThat(copy.workingDirectory()).isEqualTo("/picked/3/Docs/");
        assertThat(text(copy, "a.txt")).isEqualTo("a");
    }

    @Test
    @DisplayName("change-dir to a folder the mount has not got is refused as a desktop refuses it")
    void changeDirectoryToNothingIsRefused() {
        assertThatThrownBy(() -> files.changeDirectory("/picked/9/"))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        denied -> assertThat(denied.errorId()).isEqualTo("cannot-open"));
        assertThat(files.workingDirectory()).isEqualTo("/");
    }

    @Test
    @DisplayName("the root lists picked/ beside the store's names once the mount holds anything")
    void theRootListsTheMount() {
        files.write("/notes.txt", bytes("mine"));
        assertThat(files.listFileNames("/")).containsExactly("notes.txt");

        pickFile("/picked/1/a.txt", "a");

        assertThat(files.listFileNames("/")).containsExactly("notes.txt", "picked/");
        assertThat(files.isDirectory("/picked/")).isTrue();
        assertThat(files.exists("/picked/")).isTrue();
    }

    @Test
    @DisplayName("a folder the store holds under the mount's name is hidden by the mount")
    void theMountHidesTheStoresFolder() {
        store.makeDirectory("/picked/old/", true);
        pickFile("/picked/1/a.txt", "a");

        assertThat(files.listFileNames("/picked/")).containsExactly("1/");
        assertThat(files.listFileNames("/")).containsExactly("picked/");
    }

    @Test
    @DisplayName("QUERY of a picked file names it from the working directory, as the store's do")
    void describeNamesFromTheWorkingDirectory() {
        pickFile("/picked/1/a.txt", "four");

        FileInformation described = files.describe("picked/1/a.txt").orElseThrow();

        assertThat(described.name()).isEqualTo("picked/1/a.txt");
        assertThat(described.size()).contains(4L);
    }

    @Test
    @DisplayName("renaming across the mount's edge is refused, since no provider can move a file there")
    void renameAcrossTheEdgeIsRefused() {
        pickFile("/picked/1/a.txt", "a");

        assertThatThrownBy(() -> files.rename("/picked/1/a.txt", "/a.txt"))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        denied -> assertThat(denied.errorId()).isEqualTo("cannot-open"));
        assertThat(text(picked, "/picked/1/a.txt")).isEqualTo("a");
    }

    @Test
    @DisplayName("renaming inside the mount reaches the mount")
    void renameInsideTheMount() {
        pickFile("/picked/1/Docs/a.txt", "a");

        files.rename("/picked/1/Docs/a.txt", "/picked/1/Docs/b.txt");

        assertThat(text(picked, "/picked/1/Docs/b.txt")).isEqualTo("a");
    }

    @Test
    @DisplayName("TO-REAL-FILE and the location of a picked file are the mount's own answers")
    void resolvedAndLocatedByTheMount() {
        pickFile("/picked/1/a.txt", "a");

        assertThat(files.resolvedFile("picked/1/a.txt")).isEqualTo(FileValue.of("/picked/1/a.txt"));
        assertThat(files.locationFor("picked/1/")).isEqualTo("/picked/1/");
    }
}
