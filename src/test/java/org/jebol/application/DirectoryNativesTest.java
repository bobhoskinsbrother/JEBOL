package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The directory natives, and the grants each one needs.
 *
 * <p>Specified in {@code spec/embed.allium}.
 *
 * <p>WHAT-DIR and CHANGE-DIR need the working directory grant. The rest
 * need the files grant. They are separate kinds because a host can want a
 * script to read files and not want it to wander.
 */
class DirectoryNativesTest {

    private static Interpreter reaching(Path directory, HostService... granted) {
        Bounds bounds = Bounds.standard();
        for (HostService service : granted) {
            bounds = bounds.granting(service);
        }
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(Interpreter interpreter, String source) {
        return answerTo(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("MAKE-DIR makes a directory")
    void makeDirMakesOne(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory, HostService.FILES);
        answerTo(interpreter, "make-dir %sub/");
        assertThat(Files.isDirectory(directory.resolve("sub"))).isTrue();
    }

    @Test
    @DisplayName("MAKE-DIR says nothing when the directory is there already")
    void makeDirIsQuietTheSecondTime() {
        // R3 says so in its own help text, thus a script can call it
        // without asking first.
    }

    @Test
    @DisplayName("/DEEP makes the directories above it too")
    void deepMakesTheParents(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory, HostService.FILES);
        answerTo(interpreter, "make-dir/deep %a/b/c/");
        assertThat(Files.isDirectory(directory.resolve("a/b/c"))).isTrue();
    }

    @Test
    @DisplayName("without /DEEP a missing parent is a failure")
    void aMissingParentFails(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory, HostService.FILES);
        assertThat(errorIdOf(interpreter, "make-dir %x/y/")).isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("DELETE removes a file")
    void deleteRemoves(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("gone.txt"), "x");
        Interpreter interpreter = reaching(directory, HostService.FILES);
        answerTo(interpreter, "delete %gone.txt");
        assertThat(Files.exists(directory.resolve("gone.txt"))).isFalse();
    }

    @Test
    @DisplayName("RENAME gives a file another name")
    void renameMoves(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("old.txt"), "x");
        Interpreter interpreter = reaching(directory, HostService.FILES);
        answerTo(interpreter, "rename %old.txt %new.txt");
        assertThat(Files.exists(directory.resolve("new.txt"))).isTrue();
        assertThat(Files.exists(directory.resolve("old.txt"))).isFalse();
    }

    @Test
    @DisplayName("READ-DIR gives the names, with a slash on each directory")
    void readDirNamesEverything(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("one.txt"), "x");
        Files.createDirectory(directory.resolve("two"));
        Interpreter interpreter = reaching(directory, HostService.FILES);
        assertThat(answerTo(interpreter, "(read-dir %.) = [%one.txt %two/]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("READ-DIR of an empty directory gives no names")
    void theDegenerateDirectory(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory, HostService.FILES);
        assertThat(answerTo(interpreter, "empty? read-dir %.")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("WHAT-DIR ends with a slash")
    void theDirectoryNamesItselfAsADirectory(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory, HostService.WORKING_DIRECTORY);
        assertThat(answerTo(interpreter, "#\"/\" = last what-dir")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CHANGE-DIR moves where a relative path counts from")
    void changeDirMoves(@TempDir Path directory) throws Exception {
        Files.createDirectory(directory.resolve("inner"));
        Files.writeString(directory.resolve("inner/here.txt"), "found");
        Interpreter interpreter = reaching(
                directory, HostService.WORKING_DIRECTORY, HostService.FILES);
        assertThat(answerTo(interpreter, "change-dir %inner/ read %here.txt"))
                .isEqualTo("\"found\"");
    }

    @Test
    @DisplayName("CHANGE-DIR cannot leave the directory the port was given")
    void theRootStillHolds(@TempDir Path directory) {
        // Moving must not widen what a script can reach. The test is
        // against the root and not against where the script is now.
        Interpreter interpreter = reaching(
                directory, HostService.WORKING_DIRECTORY, HostService.FILES);
        assertThat(errorIdOf(interpreter, "change-dir %../")).isEqualTo("outside-root");
    }

    @Test
    @DisplayName("CHANGE-DIR to something that is not a directory fails")
    void aFileIsNotADirectory(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("flat.txt"), "x");
        Interpreter interpreter = reaching(
                directory, HostService.WORKING_DIRECTORY, HostService.FILES);
        assertThat(errorIdOf(interpreter, "change-dir %flat.txt")).isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("the two grants are separate")
    void readingDoesNotLetAScriptWander(@TempDir Path directory) {
        // A host can want a script to read files and not want it to move.
        Interpreter interpreter = reaching(directory, HostService.FILES);
        assertThat(errorIdOf(interpreter, "what-dir")).isEqualTo("no-service");
        assertThat(errorIdOf(interpreter, "change-dir %.")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("and moving does not let a script read")
    void wanderingDoesNotLetAScriptRead(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory, HostService.WORKING_DIRECTORY);
        assertThat(errorIdOf(interpreter, "read %a.txt")).isEqualTo("no-service");
    }
}
