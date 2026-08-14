package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading and writing through ports.
 *
 * <p>Written before they exist. In a web production environment this is the
 * most dangerous thing a script can do, so the shape matters more than the
 * feature: a script reaches the filesystem through a port the host supplied,
 * and a host that supplied none has a script that cannot read anything.
 *
 * <p>Deny by default, in other words, for the same reason {@code HostAccess}
 * defaults to nothing. A host that has not thought about what a script may
 * read has not decided that it may read everything.
 */
class PortsTest {

    /**
     * An interpreter that may ask for files but has been given no port.
     *
     * <p>The grant and the port are two separate things. This file is
     * about the port, thus the grant is given everywhere and the tests
     * below say what happens with and without somewhere to read.
     */
    private static Interpreter grantedFiles() {
        return Interpreter.withBounds(
                Bounds.standard().granting(org.jebol.domain.host.HostService.FILES));
    }

    @Nested
    @DisplayName("without a port, a script reaches nothing")
    class DeniedByDefault {

        @Test
        @DisplayName("reading a file is refused when no port was supplied")
        void readingIsRefusedByDefault() {
            ScriptOutcome outcome = grantedFiles().run("read %somewhere.txt");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("no-port");
        }

        @Test
        @DisplayName("writing is refused too")
        void writingIsRefusedByDefault() {
            ScriptOutcome outcome =
                    grantedFiles().run("write %somewhere.txt \"contents\"");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("no-port");
        }

        @Test
        @DisplayName("and the refusal is a catchable error, not a crash")
        void refusalIsAnOrdinaryError() {
            Interpreter interpreter = grantedFiles();

            assertThat(interpreter.run("error? try [read %somewhere.txt]").display())
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("with a port, a script reaches what the port allows")
    class ThroughAPort {

        @Test
        void aScriptCanReadAFile(@TempDir Path directory) throws IOException {
            Path file = directory.resolve("greeting.txt");
            Files.writeString(file, "hello from disk", StandardCharsets.UTF_8);

            Interpreter interpreter = grantedFiles();
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));

            assertThat(interpreter.run("read/string %greeting.txt").display())
                    .isEqualTo("\"hello from disk\"");
        }

        @Test
        void aScriptCanWriteAFile(@TempDir Path directory) throws IOException {
            Interpreter interpreter = grantedFiles();
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));

            interpreter.run("write %written.txt \"from the script\"");

            assertThat(Files.readString(directory.resolve("written.txt")))
                    .isEqualTo("from the script");
        }

        @Test
        @DisplayName("reading something that is not there is an error, not empty text")
        void readingSomethingAbsentIsAnError(@TempDir Path directory) {
            Interpreter interpreter = grantedFiles();
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));

            ScriptOutcome outcome = interpreter.run("read %missing.txt");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("cannot-open");
        }

        @Test
        @DisplayName("a script can tell whether a file is there")
        void aScriptCanAskWhetherAFileExists(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve("here.txt"), "yes");
            Interpreter interpreter = grantedFiles();
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));

            assertThat(interpreter.run("exists? %here.txt").display()).isEqualTo("file");
            assertThat(interpreter.run("exists? %not-here.txt").display()).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("the port is a boundary, not a suggestion")
    class TheBoundaryHolds {

        @Test
        @DisplayName("a script cannot climb out of the directory it was given")
        void escapingTheRootIsRefused(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve("inside.txt"), "fine");
            Interpreter interpreter = grantedFiles();
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));

            ScriptOutcome outcome = interpreter.run("read %../../../etc/passwd");

            assertThat(outcome.conclusion())
                    .as("a relative path must not reach outside the root")
                    .isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("outside-root");
        }

        @Test
        @DisplayName("nor reach an absolute path")
        void absolutePathsAreRefused(@TempDir Path directory) {
            Interpreter interpreter = grantedFiles();
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));

            ScriptOutcome outcome = interpreter.run("read %/etc/passwd");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("outside-root");
        }

        @Test
        @DisplayName("a read-only port refuses writes")
        void aReadOnlyPortRefusesWrites(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve("readable.txt"), "contents");
            Interpreter interpreter = grantedFiles();
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory).readOnly());

            assertThat(interpreter.run("read %readable.txt").succeeded()).isTrue();

            ScriptOutcome outcome = interpreter.run("write %readable.txt \"changed\"");
            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("read-only");
        }

        @Test
        @DisplayName("and the file is untouched after a refused write")
        void aRefusedWriteChangesNothing(@TempDir Path directory) throws IOException {
            Path file = directory.resolve("readable.txt");
            Files.writeString(file, "original");
            Interpreter interpreter = grantedFiles();
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory).readOnly());

            interpreter.run("write %readable.txt \"changed\"");

            assertThat(Files.readString(file)).isEqualTo("original");
        }
    }
}
