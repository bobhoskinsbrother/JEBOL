package org.jebol.domain.eval;

import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DIR?, which Rebol writes in C and JEBOL had written in REBOL.
 *
 * <p>The layer was the smaller half of it. The prelude version answered on a
 * trailing forward slash alone, so it had neither the backslash the C also
 * accepts nor {@code /check}, which is the refinement that makes the question
 * worth asking: without it DIR? reads a name, and with it DIR? reads the disk.
 * A directory whose name does not end in a slash is the ordinary case, and
 * every one of them came back false.
 *
 * <p>{@code n-io.c} consults the disk only for a {@code file!}, and only ever
 * to say true: a check that finds nothing falls through to the slash test
 * rather than answering false, which is why {@code dir?/check http://a/} is
 * still true.
 */
class DirectoryQuestionFromTheSourceTest {

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    private static String answerTo(String source) {
        return answerFrom(Interpreter.create(), source);
    }

    private static String answerFrom(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static Interpreter reaching(Path directory, HostService... granted) {
        Bounds bounds = Bounds.standard();
        for (HostService service : granted) {
            bounds = bounds.granting(service);
        }
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static Interpreter holdingADirectoryAndAFile(Path root) throws Exception {
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("plain.txt"), "x");
        return reaching(root, HostService.FILES);
    }

    @Nested
    @DisplayName("without /check it reads the name and nothing else")
    class TheNameAlone {

        @Test
        @DisplayName("a trailing forward slash means a directory")
        void aforwardSlashSaysYes() {
            assertThat(answerTo("dir? %a/")).isEqualTo(TRUE);
            assertThat(answerTo("dir? http://a/")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and so does a trailing backslash, which the prelude never knew")
        void abackslashSaysYesToo() {
            assertThat(answerTo("dir? to file! join {a} to char! 92"))
                    .as("n-io.c tests both characters, and a Windows path uses the "
                            + "one the prelude left out")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a name ending in anything else does not")
        void everythingElseSaysNo() {
            assertThat(answerTo("dir? %a")).isEqualTo(FALSE);
            assertThat(answerTo("dir? http://a")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("an empty name and no name at all are both false")
        void thedegenerateCasesAreFalse() {
            assertThat(answerTo("dir? to file! {}"))
                    .as("the C answers before it reaches for the last character, "
                            + "which there is not one of")
                    .isEqualTo(FALSE);
            assertThat(answerTo("dir? none")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and anything outside file, url and none is turned away")
        void theotherDatatypesAreRefused() {
            assertThat(answerTo(
                    "either error? e: try [dir? {a/}] [e/id] ['accepted]"))
                    .isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("with /check it reads the disk as well")
    class TheDiskToo {

        @Test
        @DisplayName("a real directory is one, whether or not its name says so")
        void arealDirectoryIsOne(@TempDir Path root) throws Exception {
            assertThat(answerFrom(holdingADirectoryAndAFile(root), "dir?/check %sub"))
                    .as("this is the case the prelude version could never answer")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a real file is not, however it is named")
        void arealFileIsNot(@TempDir Path root) throws Exception {
            assertThat(answerFrom(holdingADirectoryAndAFile(root), "dir?/check %plain.txt"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a name that is on no disk falls back to the slash test")
        void anabsentNameFallsBack(@TempDir Path root) throws Exception {
            assertThat(answerFrom(holdingADirectoryAndAFile(root),
                    "dir?/check %no-such-thing-here")).isEqualTo(FALSE);
            assertThat(answerFrom(holdingADirectoryAndAFile(root),
                    "dir?/check %no-such-thing-here/"))
                    .as("the check can only say yes; saying no is left to the name")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a url is never looked for on a disk")
        void aurlIsNotLookedFor(@TempDir Path root) throws Exception {
            assertThat(answerFrom(holdingADirectoryAndAFile(root), "dir?/check http://a/"))
                    .isEqualTo(TRUE);
            assertThat(answerFrom(holdingADirectoryAndAFile(root), "dir?/check http://a"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and none is still none")
        void noneIsStillFalse(@TempDir Path root) throws Exception {
            assertThat(answerFrom(holdingADirectoryAndAFile(root), "dir?/check none"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a script not granted the file system is refused rather than told no")
        void withoutTheGrantItRefuses(@TempDir Path root) throws Exception {
            Files.createDirectory(root.resolve("sub"));

            assertThat(answerFrom(reaching(root),
                    "either error? e: try [dir?/check %sub] [e/id] ['answered]"))
                    .as("reading the disk is reading the disk, and a false here "
                            + "would be a lie about a directory that is there")
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("but the plain question needs no grant, because it reads no disk")
        void theplainQuestionNeedsNoGrant() {
            assertThat(answerTo("dir? %a/")).isEqualTo(TRUE);
        }
    }
}
