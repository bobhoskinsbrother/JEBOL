package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What DELETE answers, which is a port and not the file it was given.
 *
 * <p>{@code A_DELETE} in {@code p-file.c} says why beside the line:
 * {@code return R_RET; // returns port so it can be used in chained
 * evaluation}. Rebol's own cache module relies on it -- DELETE-THRU answers
 * whatever DELETE answered, and the test for it asks {@code port? delete-thru
 * url}.
 *
 * <p>Two answers below it in the same switch, and JEBOL had neither. Nothing
 * there to delete is FALSE: the device answers -2 for that one case and
 * {@code if (result == -2) return R_FALSE;} turns it straight into false, so a
 * script clearing up after itself writes {@code delete %maybe} and reads the
 * answer without wrapping it. A delete that was refused is different -- a
 * directory with something in it is {@code no-delete} with the path, because
 * the caller asked for something that could have worked.
 *
 * <p>Every expectation was run against a real 3.22.5 first.
 */
class DeleteAnswersThePortFromTheSourceTest {

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(Path root, String source) {
        return answerTo(root, "failure: try [" + source + "] failure/id");
    }

    @Test
    @DisplayName("deleting a file answers the port it opened, referring to the file")
    void deletingAFileAnswersThePortItOpened(@TempDir Path root) {
        assertThat(answerTo(root, """
                write %gone.txt "x"
                d: delete %gone.txt
                reduce [port? d  d/spec/scheme  d/spec/ref  exists? %gone.txt]"""))
                .isEqualTo("[#(true) file %gone.txt _]");
    }

    @Test
    @DisplayName("and deleting a directory does the same")
    void deletingADirectoryDoesTheSame(@TempDir Path root) {
        assertThat(answerTo(root, """
                make-dir %empty/
                reduce [port? delete %empty/  exists? %empty/]"""))
                .isEqualTo("[#(true) _]");
    }

    /**
     * The one case that is false rather than a failure. A script clearing up
     * after itself does not have to know whether the file was ever made.
     */
    @Test
    @DisplayName("deleting what is not there answers false, without failing")
    void deletingWhatIsNotThereAnswersFalse(@TempDir Path root) {
        assertThat(answerTo(root, "delete %never-existed.txt")).isEqualTo("#(false)");
        assertThat(answerTo(root, "delete %never-existed/")).isEqualTo("#(false)");
    }

    /**
     * And a delete that could have worked and did not is refused by name, with
     * the path, so the caller learns which file the operating system would not
     * give up.
     */
    @Test
    @DisplayName("a directory with something in it is refused, with the path")
    void aDirectoryWithSomethingInItIsRefused(@TempDir Path root) {
        assertThat(errorIdFrom(root, """
                make-dir %full/
                write %full/x.txt "y"
                delete %full/""")).isEqualTo("no-delete");
        assertThat(answerTo(root, """
                make-dir %full/
                write %full/x.txt "y"
                failure: try [delete %full/] failure/arg1""")).isEqualTo("%full/");
    }

    /**
     * The line Rebol's cache module ends on, which is what found this. Its
     * DELETE-THRU answers whatever DELETE answered and the assertion asks
     * whether that is a port.
     */
    @Test
    @DisplayName("so a delete can be chained, which is what the answer is for")
    void soADeleteCanBeChained(@TempDir Path root) {
        assertThat(answerTo(root, """
                write %chained.txt "x"
                port? delete %chained.txt""")).isEqualTo("#(true)");
    }
}
