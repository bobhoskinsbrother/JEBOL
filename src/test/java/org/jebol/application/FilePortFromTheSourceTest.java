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

        @Test
        @DisplayName("AT counts from one and ATZ from nothing, both moving in place")
        void atAndAtzMoveInPlace(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open/read/seek %f
                    reduce [index? at p 3 index? atz p 1 indexz? p]"""))
                    .isEqualTo("[3 2 1]");
        }
    }

    @Nested
    @DisplayName("clearing one, which cuts it off rather than emptying it")
    class Clearing {

        @Test
        @DisplayName("what is from the position onwards goes, and what is before stays")
        void fromThePositionOnwardsGoes(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open %f
                    skip p 2
                    clear p
                    close p
                    read %f""")).isEqualTo("#{3132}");
        }

        @Test
        @DisplayName("so clearing at the head empties it")
        void clearingAtTheHeadEmptiesIt(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open %f
                    clear p
                    close p
                    read %f""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("and clearing at the tail does nothing at all")
        void clearingAtTheTailDoesNothing(@TempDir Path root) {
            assertThat(answerTo(root, """
                    write %f "12345"
                    p: open %f
                    tail p
                    clear p
                    close p
                    read %f""")).isEqualTo("#{3132333435}");
        }

        @Test
        @DisplayName("and a closed port cannot be cleared, having no position")
        void aClosedPortCannotBeCleared(@TempDir Path root) {
            assertThat(errorIdFrom(root, """
                    write %f "12345"
                    p: open %f
                    close p
                    clear p""")).isEqualTo("not-open");
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

        /**
         * Only an open that cannot write is refused. A bare OPEN names neither
         * reading nor writing, so the C fills in both -- {@code if (!(args &
         * (AM_OPEN_READ | AM_OPEN_WRITE))) args |= (AM_OPEN_READ |
         * AM_OPEN_WRITE);} -- and anything that may write carries
         * {@code O_CREAT}.
         *
         * <p>This asserted the opposite until it was measured, which is what
         * kept Rebol's own port test from getting past the line whose comment
         * is "create locked file...". `OpeningAFileMakesItFromTheSourceTest`
         * has the whole rule.
         */
        @Test
        @DisplayName("and without it, only an open that cannot write is refused")
        void withoutItOnlyAReadIsRefused(@TempDir Path root) {
            assertThat(errorIdFrom(root, "open/read %never-existed"))
                    .isEqualTo("cannot-open");
            assertThat(answerTo(root, """
                    p: open %never-existed
                    reduce [port? p exists? %never-existed]"""))
                    .isEqualTo("[#(true) file]");
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

    /**
     * The file scheme is one this host serves, so a url naming it is another
     * way of writing a path rather than a protocol nobody implemented.
     *
     * <p>Where the path begins is a parse rule in {@code sys-ports.reb} and
     * not the {@code ://} it looks like:
     * {@code parse port/spec/ref [thru #":" 0 2 slash path:]}. At most two
     * slashes belong to the notation, so a third is the start of an absolute
     * path, and none at all is allowed too. Every figure below was read off
     * {@code ./r3-head} 3.22.5.
     */
    @Nested
    @DisplayName("a file: url, which names a file and not a protocol")
    class TheFileScheme {

        @Test
        @DisplayName("reading one reads the file the path would have")
        void readingAFileUrl(@TempDir Path root) throws IOException {
            Files.writeString(root.resolve("temp.txt"), "hello");

            assertThat(answerTo(root, "read file://temp.txt"))
                    .isEqualTo("#{68656C6C6F}");
            assertThat(answerTo(root, "read/string file://temp.txt"))
                    .isEqualTo("\"hello\"");
        }

        @Test
        @DisplayName("an empty file reads as no bytes rather than failing")
        void readingAnEmptyFileUrl(@TempDir Path root) throws IOException {
            Files.writeString(root.resolve("temp.txt"), "");

            assertThat(answerTo(root, "#{} = read file://temp.txt"))
                    .as("Rebol's own file test asks exactly this, for issue 834")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("none, one and two slashes all name the same relative file")
        void theSlashesAreOptional(@TempDir Path root) throws IOException {
            Files.writeString(root.resolve("temp.txt"), "hello");

            assertThat(answerTo(root, "read file:temp.txt")).isEqualTo("#{68656C6C6F}");
            assertThat(answerTo(root, "read file:/temp.txt")).isEqualTo("#{68656C6C6F}");
            assertThat(answerTo(root, "read file://temp.txt")).isEqualTo("#{68656C6C6F}");
        }

        @Test
        @DisplayName("and a third slash starts an absolute path")
        void thethirdSlashIsPartOfThePath(@TempDir Path root) throws IOException {
            Files.writeString(root.resolve("temp.txt"), "hello");

            assertThat(answerTo(root, "read file:///temp.txt"))
                    .as("the run's root is the sandbox root, so this one does reach it")
                    .isEqualTo("#{68656C6C6F}");
        }

        @Test
        @DisplayName("a file that is not there is cannot-open, as a path would be")
        void amissingFileUrl(@TempDir Path root) {
            assertThat(errorIdFrom(root, "read file://nope.txt")).isEqualTo("cannot-open");
        }

        @Test
        @DisplayName("the port keeps the path it worked out beside the url it was given")
        void theSpecCarriesBoth(@TempDir Path root) throws IOException {
            Files.writeString(root.resolve("temp.txt"), "hello");

            assertThat(answerTo(root, "p: open file://temp.txt  p/spec/path"))
                    .isEqualTo("%temp.txt");
            assertThat(answerTo(root, "p: open file://temp.txt  p/spec/ref"))
                    .isEqualTo("file://temp.txt");
        }

        @Test
        @DisplayName("writing, deleting and asking after one work the same way")
        void theOtherVerbsReachItToo(@TempDir Path root) throws IOException {
            assertThat(answerTo(root, """
                    write file://w.txt "w"  read %w.txt"""))
                    .isEqualTo("#{77}");
            assertThat(answerTo(root, """
                    write %e.txt "e"  exists? file://e.txt"""))
                    .isEqualTo("file");

            Files.writeString(root.resolve("gone.txt"), "x");
            answerTo(root, "delete file://gone.txt");
            assertThat(Files.exists(root.resolve("gone.txt"))).isFalse();
        }

        @Test
        @DisplayName("and a dir: url lists the directory it names")
        void adirectoryUrl(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("sub"));
            Files.writeString(root.resolve("sub/a.txt"), "x");

            assertThat(answerTo(root, "read dir://sub/")).isEqualTo("[%a.txt]");
        }
    }
}
