package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpeningAFileMakesItFromTheSourceTest {

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = Interpreter.createOn(
                Interpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(root)),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorFrom(Path root, String source) {
        return answerTo(root, "e: try [" + source
                + "] either error? e [reduce [e/id e/arg1]] ['no-error]");
    }

    @Nested
    @DisplayName("a file that is not there")
    class AFileThatIsNotThere {

        @Test
        @DisplayName("is made by any open that may write, and left empty")
        void isMadeByAnyOpenThatMayWrite() {
            assertThat(answerTo(root(), """
                    reduce [port? open %made.txt  exists? %made.txt  size? %made.txt]"""))
                    .isEqualTo("[#(true) file 0]");
        }

        @Test
        @DisplayName("including when the open names writing or seeking")
        void isMadeWhenTheOpenNamesWritingOrSeeking() {
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

        @Test
        @DisplayName("but an open that only reads is refused, naming the file")
        void refusesAnOpenThatOnlyReads() {
            assertThat(readTheErrorFrom(root(), "open/read %gone.txt"))
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

    @Test
    @DisplayName("and a directory that is not there is refused, not made")
    void refusesADirectoryThatIsNotThere(@TempDir Path root) {
        assertThat(readTheErrorFrom(root, "open %no-dir/")).isEqualTo("[cannot-open %no-dir/]");
        assertThat(readTheErrorFrom(root, "open/write %no-dir/"))
                .isEqualTo("[cannot-open %no-dir/]");
        assertThat(answerTo(root, "try [open %no-dir/] exists? %no-dir/")).isEqualTo("_");
    }

    @Test
    @DisplayName("but one that is there opens")
    void opensOneThatIsThere(@TempDir Path root) {
        assertThat(answerTo(root, """
                make-dir %a-dir/
                port? open %a-dir/""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and a pattern opens when something matches it, not otherwise")
    void opensAPatternWhenSomethingMatchesIt(@TempDir Path root) {
        assertThat(answerTo(root, """
                write %a.r3 "x"
                p: open %*.r3
                reduce [port? p  'dir = p/spec/scheme]"""))
                .isEqualTo("[#(true) #(true)]");
        assertThat(readTheErrorFrom(root, """
                write %a.r3 "x"
                open %*.zzz""")).isEqualTo("[cannot-open %*.zzz]");
        assertThat(readTheErrorFrom(root, "open %nodir/*.r3"))
                .isEqualTo("[cannot-open %nodir/*.r3]");
    }

    @Test
    @DisplayName("where reading the same pattern answers nothing and never raises")
    void answersNothingOnReadingTheSamePattern(@TempDir Path root) {
        assertThat(answerTo(root, "mold read %*.zzz")).isEqualTo("\"[]\"");
        assertThat(answerTo(root, "mold read %nodir/*.r3")).isEqualTo("\"[]\"");
    }

    private static Path made;

    private static Path root() {
        return made;
    }

    @org.junit.jupiter.api.BeforeEach
    void makeAFreshRoot(@TempDir Path root) {
        made = root;
    }
}
