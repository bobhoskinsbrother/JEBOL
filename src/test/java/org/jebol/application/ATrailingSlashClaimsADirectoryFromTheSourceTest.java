package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ATrailingSlashClaimsADirectoryFromTheSourceTest {

    private static Path root;

    @BeforeEach
    void freshRoot(@TempDir Path made) {
        root = made;
    }

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorFrom(String source) {
        return answerTo("e: try [" + source
                + "] either error? e [reduce [e/id e/arg1]] ['no-error]");
    }

    private static final String A_FILE_AND_A_DIRECTORY = """
            write %f.txt "test"
            make-dir %d/
            """;

    @Nested
    @DisplayName("asking what is there")
    class AskingWhatIsThere {

        @Test
        @DisplayName("a file answers to its own name and not to one with a slash")
        void aFileAnswersToItsOwnNameOnly() {
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "exists? %f.txt"))
                    .isEqualTo("file");
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "exists? %f.txt/"))
                    .isEqualTo("_");
        }

        @Test
        @DisplayName("but a directory answers to both, because the slash is not needed")
        void aDirectoryAnswersToBoth() {
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "exists? %d/")).isEqualTo("dir");
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "exists? %d")).isEqualTo("dir");
        }

        @Test
        @DisplayName("and QUERY answers none for every field, since EXISTS? is QUERY")
        void queryAnswersNoneForEveryField() {
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "none? query %f.txt/ 'type"))
                    .isEqualTo("#(true)");
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "none? query %f.txt/ 'size"))
                    .isEqualTo("#(true)");
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "none? size? %f.txt/"))
                    .isEqualTo("#(true)");
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "none? query %f.txt/ object!"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and none rather than a raise")
        void noneRatherThanARaise() {
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "query %f.txt/ 'type"))
                    .isEqualTo("no-error");
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "exists? %f.txt/"))
                    .isEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("acting on it")
    class ActingOnIt {

        @Test
        @DisplayName("reading a file through a slash is refused")
        void readingAFileThroughASlashIsRefused() {
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "read %f.txt/"))
                    .isEqualTo("[cannot-open %f.txt/]");
        }

        @Test
        @DisplayName("where reading a directory without one still lists it")
        void readingADirectoryWithoutOneStillListsIt() {
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "mold read %d")).isEqualTo("\"[]\"");
        }

        @Test
        @DisplayName("deleting a file through a slash is no-delete")
        void deletingAFileThroughASlashIsNoDelete() {
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "delete %f.txt/"))
                    .isEqualTo("[no-delete %f.txt/]");
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + """
                    try [delete %f.txt/] exists? %f.txt""")).isEqualTo("file");
        }
    }

    @Nested
    @DisplayName("making a directory where a file already is")
    class MakingADirectoryWhereAFileAlreadyIs {

        @Test
        @DisplayName("is no-create, not cannot-open")
        void isNoCreateNotCannotOpen() {
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "make-dir %f.txt/"))
                    .isEqualTo("[no-create %f.txt/]");
        }

        @Test
        @DisplayName("whether or not the caller wrote the slash")
        void whetherOrNotTheCallerWroteTheSlash() {
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "make-dir %f.txt"))
                    .isEqualTo("[no-create %f.txt/]");
        }

        @Test
        @DisplayName("and CREATE says the same on its own")
        void createSaysTheSameOnItsOwn() {
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "create %f.txt/"))
                    .isEqualTo("[no-create %f.txt/]");
        }

        @Test
        @DisplayName("and the file is left alone")
        void theFileIsLeftAlone() {
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + """
                    try [make-dir %f.txt/]
                    reduce [exists? %f.txt  to string! read %f.txt]"""))
                    .isEqualTo("[file \"test\"]");
        }

        @Test
        @DisplayName("but a directory already there answers the path")
        void aDirectoryAlreadyThereAnswersThePath() {
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "make-dir %d/")).isEqualTo("%d/");
        }

        @Test
        @DisplayName("and reaching through a file with /DEEP is refused")
        void reachingThroughAFileWithDeepIsRefused() {
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "make-dir/deep %f.txt/sub/"))
                    .isEqualTo("[cannot-open %f.txt/]");
        }
    }
}
