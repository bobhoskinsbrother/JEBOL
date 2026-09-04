package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An open file, which is the one port that behaves like a series.
 *
 * <p>{@code p-file.c}. It has a position: SKIP and BACK move it, HEAD and TAIL
 * go to the ends, INDEX? reports it, and READ takes from it and leaves it past
 * what it took. So reading a port twice gives the whole file and then nothing,
 * which is the assertion the suite opens this section with.
 *
 * <p>LENGTH? and SIZE? are the pair worth keeping straight. LENGTH? counts
 * what is left from the position, SIZE? counts the whole file wherever the
 * position stands -- so after writing one byte at the head, the length is
 * nothing and the size is one.
 *
 * <p>Which operations mind a closed port is not obvious and is not arbitrary.
 * Everything about the position raises not-open, because a closed port has no
 * position. SIZE? does not, being about the file. READ and WRITE do not
 * either: they open it, do the work and close it again, which is why a script
 * can read the same closed port three times and get the whole file each time.
 */
class FilePortFromTheSourceTest {

    private static Interpreter grantedFilesUnder(Path root) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        return interpreter;
    }

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = grantedFilesUnder(root);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(Path root, String source) {
        return answerTo(root, "failure: try [" + source + "] failure/id");
    }

    @Nested
    @DisplayName("reading, which moves the position")
    class Reading {

        @Test
        @DisplayName("a second read answers nothing, the position being past it all")
        void aSecondReadAnswersNothing(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "abc"
                    p: open %f
                    reduce [read p empty? read p open? p]"""))
                    .isEqualTo("[#{616263} #(true) #(true)]");
        }

        @Test
        @DisplayName("and reading a closed port opens it, reads it and closes it again")
        void readingAClosedPort(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "abc"
                    p: open %f
                    read p
                    close p
                    reduce [read p open? p read p]"""))
                    .isEqualTo("[#{616263} #(false) #{616263}]");
        }

        @Test
        @DisplayName("/PART takes that many and advances by them")
        void partTakesThatMany(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open/read/seek %f
                    reduce [read/part p 1 read/part p 2]"""))
                    .isEqualTo("[#{31} #{3233}]");
        }

        @Test
        @DisplayName("a negative /PART reads backwards from where the position stands")
        void aNegativePartReadsBackwards(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open/read/seek %f
                    reduce [read/part tail p -1 read/part tail p -2]"""))
                    .isEqualTo("[#{35} #{3435}]");
        }

        @Test
        @DisplayName("and one that reaches back past the start is out of range")
        void backPastTheStart(@TempDir Path root) {
            assertThat(errorIdFrom(root, """
                    write %f "12345"
                    p: open/read/seek %f
                    read/part p -20""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("/SEEK moves first, so the same port can be read from the head twice")
        void seekMovesFirst(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open/read/seek %f
                    read p
                    reduce [read/seek p 0 read/seek p 3]"""))
                    .isEqualTo("[#{3132333435} #{3435}]");
        }
    }

    @Nested
    @DisplayName("writing, which also moves it")
    class Writing {

        @Test
        @DisplayName("a write goes at the position and pushes it along")
        void aWriteGoesAtThePosition(@TempDir Path root) {
            assertThat(answerTo(root, """
                    p: open/new %f
                    write p "a"
                    write p "b"
                    reduce [read/seek p 0 length? p size? p]"""))
                    .isEqualTo("[#{6162} 0 2]");
        }

        @Test
        @DisplayName("BACK then write overwrites what was there")
        void backThenWrite(@TempDir Path root) {
            assertThat(answerTo(root, """
                    p: open/new %f
                    write p "ab"
                    write back p "xy"
                    read head p""")).isEqualTo("#{617879}");
        }

        @Test
        @DisplayName("/SEEK past the end lengthens the file rather than refusing")
        void seekPastTheEnd(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f to-binary "Hello World!"
                    p: open/seek %f
                    write/seek p to-binary "a" 4
                    write/seek p to-binary " Goodbye World!" 12
                    to-string read/seek p 0"""))
                    .isEqualTo("\"Hella World! Goodbye World!\"");
        }

        @Test
        @DisplayName("and /PART writes only that many bytes of what it was given")
        void partWritesOnlyThatMany(@TempDir Path root) {
            assertThat(answerTo(root, """
                    p: open/new %f
                    write/part p #{1020304050} 3
                    read head p""")).isEqualTo("#{102030}");
        }
    }

    @Nested
    @DisplayName("where the position is, and what minds it being closed")
    class ThePosition {

        @Test
        @DisplayName("INDEX? is one past the position, and the moves change it in place")
        void indexIsOnePastThePosition(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open/read/seek %f
                    reduce [index? head p index? skip p 2 index? p]"""))
                    .isEqualTo("[1 3 3]");
        }

        @Test
        @DisplayName("LENGTH? is what is left and SIZE? is the whole of it")
        void lengthLeftAndWholeSize(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open/read/seek %f
                    skip p 2
                    reduce [length? p size? p]""")).isEqualTo("[3 5]");
        }

        @Test
        @DisplayName("the position questions refuse a closed port")
        void thePositionQuestionsRefuseIt(@TempDir Path root) {
            for (String asking : new String[] {
                    "index? p", "length? p", "tail? p",
                    "head p", "tail p", "skip p 1", "back p", "next p"}) {
                assertThat(errorIdFrom(root, """
                        write %f "12345"
                        p: open %f
                        close p
                        """ + asking))
                        .as("%s on a closed port", asking)
                        .isEqualTo("not-open");
            }
        }

        @Test
        @DisplayName("and SIZE? does not, being about the file rather than the port")
        void sizeDoesNotRefuseIt(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open %f
                    close p
                    size? p""")).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("opening one")
    class Opening {

        @Test
        @DisplayName("/NEW makes the file, so a name that is not there can be opened")
        void newMakesTheFile(@TempDir Path root) {
            assertThat(answerTo(root, """
                    p: open/new %never-existed
                    reduce [port? p size? p exists? %never-existed]"""))
                    .as("EXISTS? answers what kind of thing is there, not a logic")
                    .isEqualTo("[#(true) 0 file]");
        }

        @Test
        @DisplayName("and without it a name that is not there cannot be")
        void withoutItTheFileMustExist(@TempDir Path root) {
            assertThat(errorIdFrom(root, "open %never-existed"))
                    .isEqualTo("cannot-open");
        }

        @Test
        @DisplayName("a directory opens as a dir port and names itself so")
        void aDirectoryOpensAsADirPort(@TempDir Path root) {
            assertThat(answerTo(root, """
                    p: open %.
                    reduce [port? p p/scheme/name p/spec/scheme]"""))
                    .isEqualTo("[#(true) dir dir]");
        }

        @Test
        @DisplayName("reading a dir port answers its names")
        void readingADirPort(@TempDir Path root) throws IOException {
            Files.writeString(root.resolve("one.txt"), "a");
            Files.writeString(root.resolve("two.txt"), "b");

            assertThat(answerTo(root, "sort read open %."))
                    .isEqualTo("[%one.txt %two.txt]");
        }

        @Test
        @DisplayName("and EMPTY? on one asks whether the directory is empty")
        void emptyOnADirPort(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("nothing-in-here"));
            Files.writeString(root.resolve("something.txt"), "a");

            assertThat(answerTo(root, """
                    reduce [empty? open %nothing-in-here/ empty? open %.]"""))
                    .isEqualTo("[#(true) #(false)]");
        }
    }
}
