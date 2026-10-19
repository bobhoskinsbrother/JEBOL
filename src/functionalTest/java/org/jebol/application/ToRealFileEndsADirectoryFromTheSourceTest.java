package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ToRealFileEndsADirectoryFromTheSourceTest {

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootFileSystem(root));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String A_DIRECTORY_AND_A_FILE = """
            make-dir %a-dir/
            write %a-file.txt "x"
            """;

    @Test
    @DisplayName("a directory comes back with a slash, asked for with one or without")
    void endsADirectoryWithASlash(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                reduce [to-real-file %a-dir/  to-real-file %a-dir]"""))
                .isEqualTo("[%/a-dir/ %/a-dir/]");
    }

    @Test
    @DisplayName("and a file does not, asked for with a slash or without")
    void leavesAFileWithoutOne(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                reduce [to-real-file %a-file.txt  to-real-file %a-file.txt/]"""))
                .isEqualTo("[%/a-file.txt %/a-file.txt]");
    }

    @Test
    @DisplayName("the dots resolve and the slash still follows what is there")
    void resolvesTheDotsAndStillEndsWithASlash(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                reduce [to-real-file %a-dir/../a-dir/  to-real-file %.  to-real-file %./]"""))
                .isEqualTo("[%/a-dir/ %/ %/]");
    }

    @Test
    @DisplayName("a string is read as a path the same way")
    void readsAStringAsAPathTheSameWay(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                to-real-file "a-dir\"""")).isEqualTo("%/a-dir/");
    }

    @Test
    @DisplayName("nothing there is none, and so is an empty path")
    void answersNoneForNothingThereAndForAnEmptyPath(@TempDir Path root) {
        assertThat(answerTo(root, "to-real-file %not-there.txt")).isEqualTo("_");
        assertThat(answerTo(root, "to-real-file %\"\"")).isEqualTo("_");
    }

    @Test
    @DisplayName("so joining a name onto a directory reaches inside it")
    void soJoiningANameOntoADirectoryReachesInsideIt(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                join to-real-file %a-dir %inside/""")).isEqualTo("%/a-dir/inside/");
    }
}
