package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TO-REAL-FILE ends a directory with a slash, whichever way it was asked.
 *
 * <p>{@code OS_Real_Path} stats what it resolved, and the comment beside the
 * line is the whole rule: {@code // Append the trailing slash if it is a
 * directory}. So the answer is about what is there rather than about how the
 * question was spelled -- {@code to-real-file %somewhere} and {@code
 * to-real-file %somewhere/} both end in a slash when SOMEWHERE is a directory,
 * and asking for a file with a slash on the end does not put one back.
 *
 * <p>Which matters wherever the answer is joined to something. Rebol's own
 * cache module opens with {@code join to-real-file any [get-env "TEMP"
 * so/data] %thru-cache/}, and a missing slash there put the whole cache in a
 * directory named by running two names together -- {@code %/datathru-cache/}
 * where {@code %/data/thru-cache/} was meant.
 *
 * <p>Every expectation was run against a real 3.22.5 first.
 */
class ToRealFileEndsADirectoryFromTheSourceTest {

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String A_DIRECTORY_AND_A_FILE = """
            make-dir %a-dir/
            write %a-file.txt "x"
            """;

    @Test
    @DisplayName("a directory comes back with a slash, asked for with one or without")
    void aDirectoryComesBackWithASlash(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                reduce [to-real-file %a-dir/  to-real-file %a-dir]"""))
                .isEqualTo("[%/a-dir/ %/a-dir/]");
    }

    @Test
    @DisplayName("and a file does not, asked for with a slash or without")
    void aFileDoesNot(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                reduce [to-real-file %a-file.txt  to-real-file %a-file.txt/]"""))
                .isEqualTo("[%/a-file.txt %/a-file.txt]");
    }

    /**
     * The dots are resolved and the answer still ends where what it names
     * says, so a path that goes up and comes back down is the directory it
     * started from.
     */
    @Test
    @DisplayName("the dots resolve and the slash still follows what is there")
    void theDotsResolveAndTheSlashStillFollows(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                reduce [to-real-file %a-dir/../a-dir/  to-real-file %.  to-real-file %./]"""))
                .isEqualTo("[%/a-dir/ %/ %/]");
    }

    @Test
    @DisplayName("a string is read as a path the same way")
    void aStringIsReadAsAPathTheSameWay(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                to-real-file "a-dir\"""")).isEqualTo("%/a-dir/");
    }

    /**
     * Nothing there is none, which the C's own summary states: "resolves
     * symbolic links and returns NONE if file does not exists!". An empty path
     * names nothing either, and must not resolve to wherever the interpreter
     * happens to be standing.
     */
    @Test
    @DisplayName("nothing there is none, and so is an empty path")
    void nothingThereIsNoneAndSoIsAnEmptyPath(@TempDir Path root) {
        assertThat(answerTo(root, "to-real-file %not-there.txt")).isEqualTo("_");
        assertThat(answerTo(root, "to-real-file %\"\"")).isEqualTo("_");
    }

    /**
     * The line the cache module opens with, written out. Joining onto the
     * answer is what the trailing slash is for.
     */
    @Test
    @DisplayName("so joining a name onto a directory reaches inside it")
    void soJoiningANameOntoADirectoryReachesInsideIt(@TempDir Path root) {
        assertThat(answerTo(root, A_DIRECTORY_AND_A_FILE + """
                join to-real-file %a-dir %inside/""")).isEqualTo("%/a-dir/inside/");
    }
}
