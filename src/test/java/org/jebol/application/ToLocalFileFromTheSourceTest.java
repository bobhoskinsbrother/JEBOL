package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TO-LOCAL-FILE, from {@code REBNATIVE(to_local_file)} and
 * {@code To_Local_Path}.
 *
 * <p>Two things happen whatever is asked for. The separator becomes the one
 * this system writes, and a run of slashes becomes a single one.
 *
 * <p>/FULL does two more, and the second is the surprise: it puts the current
 * directory in front of a relative path, and it is also what turns the dots on.
 * The loop that reads {@code .} and {@code ..} sits inside {@code if (full)},
 * so without the refinement a dot is an ordinary character in a name. That is
 * why {@code to-local-file %a/../b} keeps the dots and
 * {@code to-local-file/full %/a/../b} does not.
 *
 * <p>The answer is always a string, never a file: {@code Set_Series(REB_STRING,
 * D_RET, ser)}. A local path is not a REBOL path, and molding one as a file
 * would put a percent sign in front of it.
 */
class ToLocalFileFromTheSourceTest {

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

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("without /FULL")
    class ThePlainConversion {

        @Test
        @DisplayName("a relative path is left where it is")
        void aRelativePathStands() {
            assertThat(answerTo("to-local-file %a/b")).isEqualTo("\"a/b\"");
        }

        @Test
        @DisplayName("and a run of slashes becomes one")
        void slashesCollapse() {
            // `if (n == 0 || out[n-1] != OS_DIR_SEP) out[n++] = OS_DIR_SEP;`
            assertThat(answerTo("to-local-file %/a//b")).isEqualTo("\"/a/b\"");
            assertThat(answerTo("to-local-file %///a")).isEqualTo("\"/a\"");
        }

        @Test
        @DisplayName("the dots are ordinary characters, because the dot loop is inside /FULL")
        void theDotsAreNotRead() {
            assertThat(answerTo("to-local-file %./a")).isEqualTo("\"./a\"");
            assertThat(answerTo("to-local-file %a/../b")).isEqualTo("\"a/../b\"");
        }

        @Test
        @DisplayName("nothing converts to nothing")
        void theEmptyPath() {
            assertThat(answerTo("to-local-file %\"\"")).isEqualTo("\"\"");
        }

        @Test
        @DisplayName("a string is accepted as readily as a file, and both answer a string")
        void bothKindsOfPath() {
            assertThat(answerTo("to-local-file \"a/b\"")).isEqualTo("\"a/b\"");
            assertThat(answerTo("type? to-local-file %a")).isEqualTo("#(string!)");
        }

        @Test
        @DisplayName("and nothing else is a path at all")
        void anythingElseIsRefused() {
            assertThat(errorIdOf(Interpreter.create(), "to-local-file 5"))
                    .isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("with /FULL")
    class TheFullConversion {

        @Test
        @DisplayName("a relative path gets the current directory in front of it")
        void theCurrentDirectoryGoesInFront(@TempDir Path directory) {
            // `if (full) l = OS_Get_Current_Dir(&lpath);` and then the path is
            // appended to it, with a separator put in between if the directory
            // did not end with one.
            Interpreter interpreter = reaching(directory, HostService.WORKING_DIRECTORY);
            assertThat(answerTo(interpreter, "(to-local-file/full %a) = "
                    + "rejoin [to-local-file what-dir \"a\"]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an absolute path keeps its own root")
        void anAbsolutePathIsLeftAlone(@TempDir Path directory) {
            // The prepending is in the `else` branch of the leading-slash
            // prescan, so a path that starts at the root never reaches it.
            assertThat(answerTo("to-local-file/full %/a/b")).isEqualTo("\"/a/b\"");
        }

        @Test
        @DisplayName("a single dot names the directory it is in, and is dropped")
        void aSingleDotGoes() {
            assertThat(answerTo("to-local-file/full %/a/./b")).isEqualTo("\"/a/b\"");
            assertThat(answerTo("to-local-file/full %/a/.")).isEqualTo("\"/a/\"");
        }

        @Test
        @DisplayName("a double dot backs out of a directory and leaves a separator behind")
        void aDoubleDotBacksOut() {
            // `n -= (n > 2) ? 2 : n;` and then a walk back to the separator
            // before that, and `c = c ? 0 : OS_DIR_SEP` puts one back. So the
            // answer to a path ending in .. ends with a separator.
            assertThat(answerTo("to-local-file/full %/a/../b")).isEqualTo("\"/b\"");
            assertThat(answerTo("to-local-file/full %/a/b/..")).isEqualTo("\"/a/\"");
            assertThat(answerTo("to-local-file/full %/a/b/../../c")).isEqualTo("\"/c\"");
        }

        @Test
        @DisplayName("and backing out past the root stops at it")
        void backingOutTooFar() {
            assertThat(answerTo("to-local-file/full %/..")).isEqualTo("\"/\"");
        }

        @Test
        @DisplayName("a name that begins with a dot is a name")
        void aDotIsOnlyASegmentWhenItIsTheWholeSegment() {
            // The dot branch only fires when the segment is `.` or `..`
            // exactly: what follows has to be a slash or the end of the path.
            assertThat(answerTo("to-local-file/full %/a/.hidden")).isEqualTo("\"/a/.hidden\"");
            assertThat(answerTo("to-local-file/full %/a/..x")).isEqualTo("\"/a/..x\"");
        }

        @Test
        @DisplayName("and asking where the process is needs the grant that answers it")
        void theGrantIsNeeded(@TempDir Path directory) {
            // Only for a relative path: an absolute one never asks.
            Interpreter refused = reaching(directory);
            assertThat(errorIdOf(refused, "to-local-file/full %a")).isEqualTo("no-service");
            assertThat(answerTo(refused, "to-local-file/full %/a")).isEqualTo("\"/a\"");
        }
    }
}
