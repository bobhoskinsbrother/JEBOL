package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;


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


    @Test
    @DisplayName("deleting what is not there answers false, without failing")
    void deletingWhatIsNotThereAnswersFalse(@TempDir Path root) {
        assertThat(answerTo(root, "delete %never-existed.txt")).isEqualTo("#(false)");
        assertThat(answerTo(root, "delete %never-existed/")).isEqualTo("#(false)");
    }

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


    @Test
    @DisplayName("so a delete can be chained, which is what the answer is for")
    void soADeleteCanBeChained(@TempDir Path root) {
        assertThat(answerTo(root, """
                write %chained.txt "x"
                port? delete %chained.txt""")).isEqualTo("#(true)");
    }
}
