package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxPathsFromTheSourceTest {

    private static Interpreter grantedFilesUnder(Path root) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a slash counts from the root the port was given")
    class TheLeadingSlash {

        @Test
        @DisplayName("a file at the root is reached by naming it with a slash")
        void aFileAtTheRoot(@TempDir Path root) throws IOException {
            Files.writeString(root.resolve("inside.txt"), "fine");

            assertThat(answerTo(grantedFilesUnder(root), """
                    to string! read %/inside.txt""")).isEqualTo("\"fine\"");
        }

        @Test
        @DisplayName("and one in a directory beneath it the same way")
        void oneInADirectory(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("units"));
            Files.writeString(root.resolve("units").resolve("deeper.txt"), "here");

            assertThat(answerTo(grantedFilesUnder(root), """
                    to string! read %/units/deeper.txt""")).isEqualTo("\"here\"");
        }

        @Test
        @DisplayName("changing directory to the slash is the top of what can be seen")
        void changingToTheSlash(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("units"));
            Files.writeString(root.resolve("at-the-top.txt"), "yes");

            assertThat(answerTo(grantedFilesUnder(root), """
                    cd %units/
                    cd %/
                    to string! read %at-the-top.txt""")).isEqualTo("\"yes\"");
        }
    }

    @Nested
    @DisplayName("what the script is told about where it is")
    class WhereItThinksItIs {

        @Test
        @DisplayName("WHAT-DIR is the slash, not wherever the root happens to sit")
        void whatDirIsTheSlash(@TempDir Path root) {
            assertThat(answerTo(grantedFilesUnder(root), "what-dir"))
                    .isEqualTo("%/");
        }

        @Test
        @DisplayName("and it follows the script down a directory")
        void itFollowsTheScriptDown(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("units").resolve("files"));

            assertThat(answerTo(grantedFilesUnder(root), """
                    cd %units/files/
                    what-dir""")).isEqualTo("%/units/files/");
        }

        @Test
        @DisplayName("TO-REAL-FILE answers a path READ can be given straight back")
        void toRealFileRoundTrips(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("units"));
            Files.writeString(root.resolve("units").resolve("there.txt"), "round");

            assertThat(answerTo(grantedFilesUnder(root), """
                    real: to-real-file %units/there.txt
                    reduce [real to string! read real]"""))
                    .isEqualTo("[%/units/there.txt \"round\"]");
        }

        @Test
        @DisplayName("and none for a path it cannot resolve, whether absent or out of bounds")
        void noneForWhatItCannotResolve(@TempDir Path root) {
            assertThat(answerTo(grantedFilesUnder(root), """
                    reduce [
                        none? to-real-file %nosuchfile.txt
                        none? to-real-file %/../../../etc/passwd
                    ]""")).isEqualTo("[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("the boundary, which none of this moves")
    class TheBoundary {

        @Test
        @DisplayName("dots after a slash clamp at the root and reach nothing")
        void dotsAfterASlash(@TempDir Path root) {
            assertThat(grantedFilesUnder(root)
                    .run("read %/../../../etc/passwd").errorId())
                    .contains("cannot-open");
        }

        @Test
        @DisplayName("nor do dots without one climb out")
        void dotsWithoutASlash(@TempDir Path root) {
            assertThat(grantedFilesUnder(root)
                    .run("read %../../../etc/passwd").errorId())
                    .contains("cannot-open");
        }

        @Test
        @DisplayName("and a climbing path writes inside the root, not above it")
        void aClimbingPathWritesInsideTheRoot(@TempDir Path root) throws Exception {
            assertThat(answerTo(grantedFilesUnder(root), """
                    write %../../../climbed.txt "inside"
                    to string! read %/climbed.txt""")).isEqualTo("\"inside\"");
            assertThat(java.nio.file.Files.exists(root.resolve("climbed.txt")))
                    .as("it landed under the root")
                    .isTrue();
            assertThat(java.nio.file.Files.exists(
                    root.getParent().resolve("climbed.txt")))
                    .as("and nothing was written above it")
                    .isFalse();
        }

        @Test
        @DisplayName("and a machine path reaches nothing rather than reaching out")
        void aMachinePathReachesNothing(@TempDir Path root) {
            assertThat(grantedFilesUnder(root).run("read %/etc/passwd").errorId())
                    .contains("cannot-open");
        }

        @Test
        @DisplayName("SECURE names a path outside the root without being stopped by it")
        void secureNamesAPathOutsideTheRoot(@TempDir Path root) {
            assertThat(grantedFilesUnder(root)
                    .run("secure [%/ allow] 'went-through").errorId())
                    .as("resolving a path exception must not raise")
                    .isEmpty();
        }
    }
}
