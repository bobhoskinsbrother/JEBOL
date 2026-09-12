package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which opens make a file, and which empty one that is already there.
 *
 * <p>Two questions with two different answers, and the C settles both in four
 * lines. {@code A_OPEN} fills in the modes that were not asked for --
 * {@code if (!(args & (AM_OPEN_READ | AM_OPEN_WRITE))) args |= (AM_OPEN_READ |
 * AM_OPEN_WRITE);} -- so a bare OPEN is opened to read <em>and</em> write. The
 * device then carries {@code O_CREAT} for any open that may write, and
 * {@code O_TRUNC} only when /NEW was asked for or the open names neither
 * reading nor seeking.
 *
 * <p>So {@code open %not-there} answers a port and leaves an empty file behind,
 * and only {@code open/read %not-there} is refused. Rebol's own port test opens
 * a file that way to make one -- "create locked file..." is its comment -- and
 * every assertion after that line was lost here to a refusal.
 *
 * <p>The truncation half is the one that costs a caller something if it is
 * wrong in the other direction: opening a file to work in and finding it
 * emptied loses the file, so the default is the one that keeps it.
 *
 * <p>Every expectation was read off {@code ./r3-head} first.
 */
class OpeningAFileMakesItFromTheSourceTest {

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorFrom(Path root, String source) {
        return answerTo(root, "e: try [" + source
                + "] either error? e [reduce [e/id e/arg1]] ['no-error]");
    }

    @Nested
    @DisplayName("a file that is not there")
    class AFileThatIsNotThere {

        /** Opened to write, and a bare OPEN is opened to write. */
        @Test
        @DisplayName("is made by any open that may write, and left empty")
        void isMadeByAnyOpenThatMayWrite() {
            assertThat(answerTo(root(), """
                    reduce [port? open %made.txt  exists? %made.txt  size? %made.txt]"""))
                    .isEqualTo("[#(true) file 0]");
        }

        @Test
        @DisplayName("including when the open names writing or seeking")
        void includingWhenTheOpenNamesWritingOrSeeking() {
            assertThat(answerTo(root(), """
                    reduce [port? open/write %w.txt  exists? %w.txt]"""))
                    .isEqualTo("[#(true) file]");
            assertThat(answerTo(root(), """
                    reduce [port? open/seek %s.txt  exists? %s.txt]"""))
                    .isEqualTo("[#(true) file]");
            assertThat(answerTo(root(), """
                    reduce [port? open/new %n.txt  exists? %n.txt]"""))
                    .isEqualTo("[#(true) file]");
        }

        /**
         * Only an open that cannot write is refused, and the file itself is the
         * argument -- a file rather than its text, so a script that caught the
         * error can retry with it or make it.
         */
        @Test
        @DisplayName("but an open that only reads is refused, naming the file")
        void anOpenThatOnlyReadsIsRefused() {
            assertThat(errorFrom(root(), "open/read %gone.txt"))
                    .isEqualTo("[cannot-open %gone.txt]");
            assertThat(answerTo(root(), """
                    e: try [open/read %gone.txt] file? e/arg1""")).isEqualTo("#(true)");
            assertThat(answerTo(root(), """
                    try [open/read %gone.txt] exists? %gone.txt""")).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("a file that is there")
    class AFileThatIsThere {

        private static final String WITH_FIVE_BYTES = """
                write %kept.txt "hello"
                """;

        /**
         * {@code modes |= O_TRUNC} only when /NEW was asked for, or when the
         * open names neither reading nor seeking.
         */
        @Test
        @DisplayName("is emptied by a write that names nothing else")
        void isEmptiedByAWriteThatNamesNothingElse() {
            assertThat(answerTo(root(), WITH_FIVE_BYTES + """
                    close open/write %kept.txt
                    size? %kept.txt""")).isEqualTo("0");
            assertThat(answerTo(root(), WITH_FIVE_BYTES + """
                    close open/new %kept.txt
                    size? %kept.txt""")).isEqualTo("0");
        }

        @Test
        @DisplayName("and kept by every other open")
        void isKeptByEveryOtherOpen() {
            assertThat(answerTo(root(), WITH_FIVE_BYTES + """
                    close open %kept.txt
                    size? %kept.txt""")).isEqualTo("5");
            assertThat(answerTo(root(), WITH_FIVE_BYTES + """
                    close open/read/write %kept.txt
                    size? %kept.txt""")).isEqualTo("5");
            assertThat(answerTo(root(), WITH_FIVE_BYTES + """
                    close open/seek %kept.txt
                    size? %kept.txt""")).isEqualTo("5");
            assertThat(answerTo(root(), WITH_FIVE_BYTES + """
                    close open/read %kept.txt
                    size? %kept.txt""")).isEqualTo("5");
        }
    }

    /**
     * A directory is not made by opening one. There is no mode that means "make
     * this directory" -- MAKE-DIR is the verb for that -- so a directory port
     * names something that has to be there already.
     *
     * <p>/NEW is left out of this on purpose: a real 3.22.5 answers a port on
     * one run and {@code no-create} on the next, in a fresh directory both
     * times, and the spec records that as an open question rather than picking
     * one of the two.
     */
    @Test
    @DisplayName("and a directory that is not there is refused, not made")
    void aDirectoryThatIsNotThereIsRefused(@TempDir Path root) {
        assertThat(errorFrom(root, "open %no-dir/")).isEqualTo("[cannot-open %no-dir/]");
        assertThat(errorFrom(root, "open/write %no-dir/"))
                .isEqualTo("[cannot-open %no-dir/]");
        assertThat(answerTo(root, "try [open %no-dir/] exists? %no-dir/")).isEqualTo("_");
    }

    @Test
    @DisplayName("but one that is there opens")
    void oneThatIsThereOpens(@TempDir Path root) {
        assertThat(answerTo(root, """
                make-dir %a-dir/
                port? open %a-dir/""")).isEqualTo("#(true)");
    }

    /**
     * A pattern names what matches it, so it is there when at least one name
     * is. Which is where OPEN and READ part company: the C reads the directory
     * as it opens and raises if that fails, where READ of the same pattern
     * answers an empty block and never raises. Opening asks for a thing and
     * reading asks a question -- no matches is an answer to the second and not
     * to the first.
     */
    @Test
    @DisplayName("and a pattern opens when something matches it, not otherwise")
    void aPatternOpensWhenSomethingMatchesIt(@TempDir Path root) {
        assertThat(answerTo(root, """
                write %a.r3 "x"
                p: open %*.r3
                reduce [port? p  'dir = p/spec/scheme]"""))
                .isEqualTo("[#(true) #(true)]");
        assertThat(errorFrom(root, """
                write %a.r3 "x"
                open %*.zzz""")).isEqualTo("[cannot-open %*.zzz]");
        assertThat(errorFrom(root, "open %nodir/*.r3"))
                .isEqualTo("[cannot-open %nodir/*.r3]");
    }

    @Test
    @DisplayName("where reading the same pattern answers nothing and never raises")
    void readingTheSamePatternAnswersNothing(@TempDir Path root) {
        assertThat(answerTo(root, "mold read %*.zzz")).isEqualTo("\"[]\"");
        assertThat(answerTo(root, "mold read %nodir/*.r3")).isEqualTo("\"[]\"");
    }

    private static Path made;

    private static Path root() {
        return made;
    }

    @org.junit.jupiter.api.BeforeEach
    void freshRoot(@TempDir Path root) {
        made = root;
    }
}
