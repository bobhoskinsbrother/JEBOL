package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AFileThatCannotBeMadeOrMovedFromTheSourceTest {

    private static Interpreter reaching(Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(Interpreter interpreter, String source) {
        return answerTo(interpreter, "e: try [" + source + """
                ]
                either error? e [reduce [e/type e/id e/arg1]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("OPEN/NEW/READ is a file to be made that nobody may write to")
    void openingANewFileNobodyMayWriteToIsRefused(@TempDir Path directory) {
        assertThat(failureOf(reaching(directory), "open/new/read %fresh.txt"))
                .isEqualTo("[Access bad-file-mode %fresh.txt]");
        assertThat(Files.exists(directory.resolve("fresh.txt"))).isFalse();
    }

    @Test
    @DisplayName("and it is refused for a file that is already there, before the path is looked at")
    void theRefusalDoesNotDependOnTheFileBeingThere(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("already.txt"), "kept");
        Interpreter interpreter = reaching(directory);

        assertThat(failureOf(interpreter, "open/new/read %already.txt"))
                .isEqualTo("[Access bad-file-mode %already.txt]");
        assertThat(Files.readString(directory.resolve("already.txt"))).isEqualTo("kept");
    }

    @Test
    @DisplayName("OPEN/NEW alone names neither way round, so writing is the default and it works")
    void openingANewFileWithoutNamingAWayRoundWorks(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory);

        assertThat(failureOf(interpreter, "close open/new %fresh.txt")).isEqualTo("[ok]");
        assertThat(failureOf(interpreter, "close open/new/write %second.txt")).isEqualTo("[ok]");
        assertThat(Files.exists(directory.resolve("fresh.txt"))).isTrue();
        assertThat(Files.exists(directory.resolve("second.txt"))).isTrue();
    }

    @Test
    @DisplayName("a read that does not ask for a new file is the missing-file failure instead")
    void readingAFileThatIsNotThereIsStillCannotOpen(@TempDir Path directory) {
        assertThat(failureOf(reaching(directory), "open/read %missing.txt"))
                .isEqualTo("[Access cannot-open %missing.txt]");
    }

    @Test
    @DisplayName("a rename that could not be done names what it was to move")
    void aRenameThatCouldNotBeDoneNamesWhatItWasToMove(@TempDir Path directory) {
        assertThat(failureOf(reaching(directory), "rename %missing.txt %other.txt"))
                .isEqualTo("[Access no-rename %missing.txt]");
    }

    @Test
    @DisplayName("and a rename that could be done moves the file and answers the new name")
    void aRenameThatCouldBeDoneMovesTheFile(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("before.txt"), "carried");
        Interpreter interpreter = reaching(directory);

        assertThat(answerTo(interpreter, "rename %before.txt %after.txt"))
                .isEqualTo("%after.txt");
        assertThat(Files.exists(directory.resolve("before.txt"))).isFalse();
        assertThat(Files.readString(directory.resolve("after.txt"))).isEqualTo("carried");
    }

    @Test
    @DisplayName("a rename into a directory that is not there is the same failure")
    void aRenameIntoADirectoryThatIsNotThereIsTheSameFailure(@TempDir Path directory)
            throws IOException {
        Files.writeString(directory.resolve("before.txt"), "carried");

        assertThat(failureOf(reaching(directory), "rename %before.txt %nowhere/after.txt"))
                .isEqualTo("[Access no-rename %before.txt]");
        assertThat(Files.exists(directory.resolve("before.txt"))).isTrue();
    }
}
