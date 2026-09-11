package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * This had an empty body and asserted nothing, which the runner counts as
     * a pass. The name said what it should check, so it is checked here.
     */
    @Test
    @DisplayName("MAKE-DIR says nothing when the directory is there already")
    void makeDirIsQuietTheSecondTime(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory, HostService.FILES);
        answerTo(interpreter, "make-dir %sub/");
        assertThat(errorIdOf(interpreter, "make-dir %sub/")).isEqualTo("no-error");
        assertThat(answerTo(interpreter, "make-dir %sub/")).isEqualTo("%sub/");
        assertThat(Files.isDirectory(directory.resolve("sub"))).isTrue();
    }

    /**
     * {@code cd ~} goes to where an application keeps its own files, not to
     * the operator's home. {@code ~} is a word, not punctuation the reader
     * knows about, and {@code mezz-tail.reb} binds it with
     * {@code ~: system/options/data}; CD's own word branch then reads the
     * word's value and changes to it.
     *
     * <p>So a caller who moves {@code system/options/data} and expects
     * {@code cd ~} to follow has to move the word too. That is what Rebol's
     * own file test asks for -- {@code cd /} and {@code cd ~} both without a
     * failure -- and what failed here, because the word still named a
     * directory outside the sandbox.
     */
    @Test
    @DisplayName("CD ~ goes where the application keeps its own files")
    void changingToTheDataDirectory(@TempDir Path directory) throws Exception {
        Files.createDirectory(directory.resolve("appdata"));
        Interpreter interpreter = reaching(
                directory, HostService.FILES, HostService.WORKING_DIRECTORY);
        answerTo(interpreter, "system/options/data: %/appdata/ set '~ system/options/data");

        assertThat(answerTo(interpreter, "all [not error? try [cd /] not error? try [cd ~]]"))
                .isEqualTo("#(true)");
        assertThat(answerTo(interpreter, "what-dir")).isEqualTo("%/appdata/");
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
        assertThat(answerTo(interpreter, "change-dir %inner/ read/string %here.txt"))
                .isEqualTo("\"found\"");
    }

    @Test
    @DisplayName("CHANGE-DIR cannot leave the directory the port was given")
    void theRootStillHolds(@TempDir Path directory) {
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
