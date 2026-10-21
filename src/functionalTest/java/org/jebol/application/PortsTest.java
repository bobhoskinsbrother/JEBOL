package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PortsTest {

    private static Interpreter grantedFiles() {
        return AnInterpreter.withBounds(
                Bounds.standard().grant(org.jebol.domain.host.HostService.FILES));
    }

    private static Interpreter over(Path root) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(root)),
                OutputPort.discard(),
                Bounds.standard().grant(org.jebol.domain.host.HostService.FILES));
    }

    @Nested
    @DisplayName("without a port, a script reaches nothing")
    class DeniedByDefault {

        @Test
        @DisplayName("reading a file is refused when no port was supplied")
        void refusesReading() {
            ScriptOutcome outcome = grantedFiles().run("read %somewhere.txt");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("no-port");
        }

        @Test
        @DisplayName("writing is refused too")
        void refusesWriting() {
            ScriptOutcome outcome =
                    grantedFiles().run("write %somewhere.txt \"contents\"");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("no-port");
        }

        @Test
        @DisplayName("and the refusal is a catchable error, not a crash")
        void raisesAnOrdinaryError() {
            Interpreter interpreter = grantedFiles();

            assertThat(interpreter.run("error? try [read %somewhere.txt]").display())
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("with a port, a script reaches what the port allows")
    class ThroughAPort {

        @Test
        void readsAFile(@TempDir Path directory) throws IOException {
            Path file = directory.resolve("greeting.txt");
            Files.writeString(file, "hello from disk", StandardCharsets.UTF_8);

            Interpreter interpreter = over(directory);

            assertThat(interpreter.run("read/string %greeting.txt").display())
                    .isEqualTo("\"hello from disk\"");
        }

        @Test
        void writesAFile(@TempDir Path directory) throws IOException {
            Interpreter interpreter = over(directory);

            interpreter.run("write %written.txt \"from the script\"");

            assertThat(Files.readString(directory.resolve("written.txt")))
                    .isEqualTo("from the script");
        }

        @Test
        @DisplayName("reading something that is not there is an error, not empty text")
        void raisesForReadingSomethingAbsent(@TempDir Path directory) {
            Interpreter interpreter = over(directory);

            ScriptOutcome outcome = interpreter.run("read %missing.txt");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("cannot-open");
        }

        @Test
        @DisplayName("a script can tell whether a file is there")
        void asksWhetherAFileExists(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve("here.txt"), "yes");
            Interpreter interpreter = over(directory);

            assertThat(interpreter.run("exists? %here.txt").display()).isEqualTo("file");
            assertThat(interpreter.run("exists? %not-here.txt").display()).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("the port is a boundary, not a suggestion")
    class TheBoundaryHolds {

        @Test
        @DisplayName("a script climbing out reaches inside the root instead")
        void refusesEscapingTheRoot(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve("inside.txt"), "fine");
            Interpreter interpreter = over(directory);

            ScriptOutcome outcome = interpreter.run("read %../../../etc/passwd");

            assertThat(outcome.conclusion())
                    .as("a relative path must not reach outside the root")
                    .isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("cannot-open");
        }

        @Test
        @DisplayName("and a climbing write lands under the root, not above it")
        void landsAClimbingWriteUnderTheRoot(@TempDir Path directory) {
            Interpreter interpreter = over(directory);

            interpreter.run("write %../../../climbed.txt \"inside\"");

            assertThat(Files.exists(directory.resolve("climbed.txt")))
                    .as("it landed under the root")
                    .isTrue();
            assertThat(Files.exists(directory.getParent().resolve("climbed.txt")))
                    .as("and nothing was written above it")
                    .isFalse();
        }

        @Test
        @DisplayName("an absolute path names the port's own root, not the machine's")
        void countsAbsolutePathsFromTheGivenRoot(@TempDir Path directory)
                throws IOException {

            Files.writeString(directory.resolve("inside.txt"), "fine");
            Interpreter interpreter = over(directory);

            assertThat(interpreter.display(interpreter.run("""
                    to string! read %/inside.txt""")))
                    .as("the slash counts from the root this port was given")
                    .isEqualTo("\"fine\"");
        }

        @Test
        @DisplayName("so a machine path reaches nothing rather than reaching out")
        void reachesNothingByAMachinePath(@TempDir Path directory) {
            Interpreter interpreter = over(directory);

            ScriptOutcome outcome = interpreter.run("read %/etc/passwd");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId())
                    .as("there is no etc/passwd under the root, and none is reached")
                    .contains("cannot-open");
        }

        @Test
        @DisplayName("and dots in an absolute path clamp at the root the same way")
        void keepsDotsInAnAbsolutePathFromClimbingOut(@TempDir Path directory) {
            Interpreter interpreter = over(directory);

            ScriptOutcome outcome = interpreter.run("read %/../../../etc/passwd");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("cannot-open");
        }

        @Test
        @DisplayName("a read-only port refuses writes")
        void refusesWritesToAReadOnlyPort(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve("readable.txt"), "contents");
            Interpreter interpreter = readOnlyOver(directory);

            assertThat(interpreter.run("read %readable.txt").succeeded()).isTrue();

            ScriptOutcome outcome = interpreter.run("write %readable.txt \"changed\"");
            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("read-only");
        }

        @Test
        @DisplayName("and the file is untouched after a refused write")
        void changesNothingForARefusedWrite(@TempDir Path directory) throws IOException {
            Path file = directory.resolve("readable.txt");
            Files.writeString(file, "original");
            Interpreter interpreter = readOnlyOver(directory);

            interpreter.run("write %readable.txt \"changed\"");

            assertThat(Files.readString(file)).isEqualTo("original");
        }

        @Test
        @DisplayName("a read-only port refuses deleting a file with read-only, and the file stays")
        void refusesDeletingOnReadOnlyPort(@TempDir Path directory) throws IOException {
            Path file = directory.resolve("kept.txt");
            Files.writeString(file, "original");

            ScriptOutcome outcome = readOnlyOver(directory).run("""
                    delete %kept.txt""");

            assertThat(outcome.errorId()).contains("read-only");
            assertThat(Files.readString(file)).isEqualTo("original");
        }

        @Test
        @DisplayName("a read-only port refuses deleting a directory with read-only, and the directory stays")
        void refusesDeletingDirectoryOnReadOnlyPort(@TempDir Path directory) throws IOException {
            Files.createDirectory(directory.resolve("kept"));

            ScriptOutcome outcome = readOnlyOver(directory).run("""
                    delete %kept/""");

            assertThat(outcome.errorId()).contains("read-only");
            assertThat(directory.resolve("kept")).isDirectory();
        }

        @Test
        @DisplayName("a read-only refusal names no path, so the script learns nothing about the root")
        void namesNoPathWhenRefusingOnReadOnlyPort(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve("kept.txt"), "original");

            ScriptOutcome outcome = readOnlyOver(directory).run("""
                    refusal: try [delete %kept.txt]
                    reduce [refusal/id refusal/arg1]""");

            assertThat(outcome.display()).isEqualTo("[read-only _]");
        }

        @Test
        @DisplayName("a read-only port refuses deleting what is not there with read-only, not false")
        void refusesDeletingMissingFileOnReadOnlyPort(@TempDir Path directory) {
            ScriptOutcome outcome = readOnlyOver(directory).run("""
                    delete %missing.txt""");

            assertThat(outcome.errorId()).contains("read-only");
        }

        @Test
        @DisplayName("a read-only port refuses renaming with read-only, and both names stay as they were")
        void refusesRenamingOnReadOnlyPort(@TempDir Path directory) throws IOException {
            Path file = directory.resolve("before.txt");
            Files.writeString(file, "original");

            ScriptOutcome outcome = readOnlyOver(directory).run("""
                    rename %before.txt %after.txt""");

            assertThat(outcome.errorId()).contains("read-only");
            assertThat(file).hasContent("original");
            assertThat(directory.resolve("after.txt")).doesNotExist();
        }

        @Test
        @DisplayName("a read-only port refuses making a directory with read-only, and none appears")
        void refusesMakingDirectoryOnReadOnlyPort(@TempDir Path directory) {
            ScriptOutcome outcome = readOnlyOver(directory).run("""
                    make-dir %fresh/""");

            assertThat(outcome.errorId()).contains("read-only");
            assertThat(directory.resolve("fresh")).doesNotExist();
        }

        @Test
        @DisplayName("a read-only port fails make-dir/deep as Rebol's own make-dir reports it, and no parent appears")
        void failsMakingDirectoryDeepOnReadOnlyPort(@TempDir Path directory) {
            ScriptOutcome outcome = readOnlyOver(directory).run("""
                    refusal: try [make-dir/deep %outer/inner/]
                    reduce [refusal/id refusal/arg1]""");

            assertThat(outcome.display()).isEqualTo("[cannot-open %outer/]");
            assertThat(directory.resolve("outer")).doesNotExist();
        }

        @Test
        @DisplayName("a read-only port lets make-dir answer a directory already there, since nothing changes")
        void answersMakingExistingDirectoryOnReadOnlyPort(@TempDir Path directory) throws IOException {
            Files.createDirectory(directory.resolve("already"));

            ScriptOutcome outcome = readOnlyOver(directory).run("""
                    make-dir %already/""");

            assertThat(outcome.succeeded()).isTrue();
        }

        private Interpreter readOnlyOver(Path directory) {
            return Interpreter.createOn(
                    AnInterpreter.standardPorts().withFiles(
                            FileSystemPort.rootFileSystem(directory).readOnlyFileSystem()),
                    OutputPort.discard(),
                    Bounds.standard().grant(org.jebol.domain.host.HostService.FILES));
        }
    }
}
