package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A name that ends in a slash says the thing is a directory, and the claim is
 * checked.
 *
 * <p>The slash is a hint in one direction and a claim in the other, and the
 * asymmetry is the whole of it. {@code %somewhere} says nothing about what is
 * there, so it works on a file and on a directory alike. {@code %somewhere/}
 * says it is a directory, and a name that says so about a file finds nothing.
 *
 * <p>That is the operating system's rule rather than a decision Rebol made.
 * POSIX requires a path ending in a slash to name a directory, so
 * {@code stat("f.txt/")} fails with ENOTDIR, and the C hands the path straight
 * through -- {@code Query_File} is {@code Get_File_Info} is {@code stat}. Every
 * verb inherits it.
 *
 * <p>A JVM does not. {@code Files.exists} trims a trailing slash before it
 * looks, so the claim went unchecked here and {@code exists? %f.txt/} answered
 * {@code file}. What that cost was an error id two levels up: Rebol's own
 * MAKE-DIR asks EXISTS? first and refuses with cannot-open when it sees a file,
 * and in a real R3 it never sees one -- it falls through to CREATE, whose
 * {@code mkdir} gives the no-create that names what actually went wrong.
 *
 * <p>Every expectation was read off {@code ./r3-head} first.
 */
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

        /** The other way round, the slash is a hint and the filesystem decides. */
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

        /**
         * And none rather than a raise, which is what lets MAKE-DIR walk past
         * it. The question "what is at this name" has "nothing" as an answer.
         */
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

        /** Where reading a directory without one lists it, the slash being a hint. */
        @Test
        @DisplayName("where reading a directory without one still lists it")
        void readingADirectoryWithoutOneStillListsIt() {
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "mold read %d")).isEqualTo("\"[]\"");
        }

        /** {@code rmdir} on a file, which fails. */
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

        /**
         * The id a caller acts on. cannot-open says the path could not be
         * reached; no-create says it was reached and the directory could not be
         * made there.
         */
        @Test
        @DisplayName("is no-create, not cannot-open")
        void isNoCreateNotCannotOpen() {
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "make-dir %f.txt/"))
                    .isEqualTo("[no-create %f.txt/]");
        }

        /** And with or without the slash, because MAKE-DIR dirizes first. */
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

        /** The file is left exactly as it was. */
        @Test
        @DisplayName("and the file is left alone")
        void theFileIsLeftAlone() {
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + """
                    try [make-dir %f.txt/]
                    reduce [exists? %f.txt  to string! read %f.txt]"""))
                    .isEqualTo("[file \"test\"]");
        }

        /**
         * A directory that is already there is not an error at all, which is
         * what MAKE-DIR's own docstring promises: "No error if already exists".
         */
        @Test
        @DisplayName("but a directory already there answers the path")
        void aDirectoryAlreadyThereAnswersThePath() {
            assertThat(answerTo(A_FILE_AND_A_DIRECTORY + "make-dir %d/")).isEqualTo("%d/");
        }

        /** And /DEEP through a file cannot get past it either. */
        @Test
        @DisplayName("and reaching through a file with /DEEP is refused")
        void reachingThroughAFileWithDeepIsRefused() {
            assertThat(errorFrom(A_FILE_AND_A_DIRECTORY + "make-dir/deep %f.txt/sub/"))
                    .isEqualTo("[cannot-open %f.txt/]");
        }
    }
}
