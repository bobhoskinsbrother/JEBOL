package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Specified in {@code spec/natives.allium} under "Writing a file", read from
 * {@code p-file.c} and {@code port-test.r3}.
 */
class WriteRefinementsFromTheSourceTest {

    private static Interpreter writingUnder(Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(Interpreter interpreter, String source) {
        String wrapped = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        interpreter.defineFreshWordsIn(wrapped);
        return interpreter.display(interpreter.run(wrapped));
    }

    private static String textIn(Path directory, String name) throws Exception {
        return Files.readString(directory.resolve(name));
    }

    private static byte[] bytesIn(Path directory, String name) throws Exception {
        return Files.readAllBytes(directory.resolve(name));
    }

    @Nested
    @DisplayName("where the write lands: plain, /seek, /append")
    class WhereTheWriteLands {

        @Test
        @DisplayName("a plain write replaces the file, and what was there is gone")
        void aPlainWriteReplacesTheFile(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {a longer earlier content}""");
            answerTo(interpreter, """
                    write %a.txt {x}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("x");
        }

        @Test
        @DisplayName("seeking overwrites in place and leaves the tail")
        void seekingLeavesTheTailBeyondTheData(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {abcd}""");
            answerTo(interpreter, """
                    write/seek %a.txt {XY} 0""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("XYcd");
        }

        @Test
        @DisplayName("a seek in the middle overwrites only what the data covers")
        void seekingInTheMiddleOverwritesOnlyThatMuch(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {abcd}""");
            answerTo(interpreter, """
                    write/seek %a.txt {X} 1""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("aXcd");
        }

        @Test
        @DisplayName("a seek at the size writes at the end")
        void seekingTheSizeAppends(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {ab}""");
            answerTo(interpreter, """
                    write/seek %a.txt {c} 2""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("a seek past the end is clipped to the size, leaving no hole")
        void seekingPastTheEndIsClippedToTheSize(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {ab}""");
            answerTo(interpreter, """
                    write/seek %a.txt {c} 100""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("seeking into a missing file creates it")
        void seekingIntoAMissingFileCreatesIt(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/seek %new.txt {x} 0""");
            assertThat(textIn(directory, "new.txt")).isEqualTo("x");
        }

        @Test
        @DisplayName("a decimal seek position is a number and is accepted")
        void aDecimalSeekIsAccepted(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {abcd}""");
            answerTo(interpreter, """
                    write/seek %a.txt {X} 1.0""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("aXcd");
        }

        @Test
        @DisplayName("a negative seek raises out-of-range")
        void aNegativeSeekRaisesOutOfRange(@TempDir Path directory) {
            Interpreter interpreter = writingUnder(directory);
            assertThat(errorIdOf(interpreter, """
                    write/seek %a.txt {x} -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("appending adds at the end and keeps what was there")
        void appendingAddsAtTheEnd(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {ab}""");
            answerTo(interpreter, """
                    write/append %a.txt {c}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("appending to a missing file creates it")
        void appendingToAMissingFileCreatesIt(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/append %new.txt {x}""");
            assertThat(textIn(directory, "new.txt")).isEqualTo("x");
        }

        @Test
        @DisplayName("when /seek and /append are both given, the seek position wins")
        void theSeekPositionBeatsAnAppend(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {abcd}""");
            answerTo(interpreter, """
                    write/append/seek %a.txt {X} 1""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("aXcd");
        }
    }

    @Nested
    @DisplayName("/part bounds what is written")
    class TheBound {

        @Test
        @DisplayName("a bound inside the data clips it")
        void theBoundClipsText(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt {abc} 2""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("ab");
        }

        @Test
        @DisplayName("a bound equal to the length writes it all")
        void aBoundEqualToTheLengthWritesItAll(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt {abc} 3""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("a bound larger than the data writes the whole data")
        void aBoundLargerThanTheDataWritesItAll(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt {abc} 4""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("a bound of zero writes nothing and still makes the file")
        void aBoundOfZeroWritesNothingAndStillMakesTheFile(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {earlier}""");
            answerTo(interpreter, """
                    write/part %a.txt {abc} 0""");
            assertThat(textIn(directory, "a.txt")).isEmpty();
        }

        @Test
        @DisplayName("a negative bound raises out-of-range")
        void aNegativeBoundRaisesOutOfRange(@TempDir Path directory) {
            Interpreter interpreter = writingUnder(directory);
            assertThat(errorIdOf(interpreter, """
                    write/part %a.txt {abc} -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a negative bound is refused even where the bound would not apply")
        void aNegativeBoundIsRefusedEvenForAChar(@TempDir Path directory) {
            Interpreter interpreter = writingUnder(directory);
            assertThat(errorIdOf(interpreter, """
                    write/part %a.txt #"a" -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a decimal bound is a number and is accepted")
        void aDecimalBoundIsAccepted(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt {abc} 2.0""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("ab");
        }

        @Test
        @DisplayName("the bound counts bytes of a binary")
        void theBoundClipsBytesOfABinary(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.bin #{010203} 2""");
            assertThat(bytesIn(directory, "a.bin"))
                    .containsExactly(0x01, 0x02);
        }

        @Test
        @DisplayName("the bound counts from the series position")
        void theBoundCountsFromTheSeriesPosition(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt next {abc} 1""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("b");
        }

        @Test
        @DisplayName("the bound applies to the molded text of any other value")
        void theBoundClipsTheMoldedText(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt [1 2] 3""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("[1 ");
        }

        @Test
        @DisplayName("a char is written whole whatever the bound")
        void aCharIsWrittenWholeWhateverTheBound(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt #"a" 0""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("a");
        }

        @Test
        @DisplayName("a block with /lines ignores the bound")
        void aBlockWithLinesIgnoresTheBound(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/lines/part %a.txt [{a} {b}] 1""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("a\nb\n");
        }
    }

    @Nested
    @DisplayName("what each kind of data becomes in the file")
    class WhatTheDataBecomes {

        @Test
        @DisplayName("writing starts from the series position")
        void writingStartsFromTheSeriesPosition(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt next {ab}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("b");
        }

        @Test
        @DisplayName("a binary is written from its position too")
        void aBinaryIsWrittenFromItsPosition(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.bin next #{0102}""");
            assertThat(bytesIn(directory, "a.bin")).containsExactly(0x02);
        }

        @Test
        @DisplayName("text crosses the boundary as UTF-8")
        void textCrossesAsUtf8(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {á}""");
            assertThat(bytesIn(directory, "a.txt")).containsExactly(0xC3, 0xA1);
        }

        @Test
        @DisplayName("a char writes its UTF-8 bytes")
        void aCharWritesItsUtf8Bytes(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt #"á\"""");
            assertThat(bytesIn(directory, "a.txt")).containsExactly(0xC3, 0xA1);
        }

        @Test
        @DisplayName("a block without /lines is molded, brackets and all")
        void aBlockWithoutLinesIsMolded(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt [1 2]""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("any other value is molded and the text written")
        void anyOtherValueIsMoldedAndWritten(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt 12""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("12");
        }

        @Test
        @DisplayName("a file as data is molded too: only string! passes as text")
        void aFileAsDataIsMolded(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt %b.txt""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("%b.txt");
        }

        @Test
        @DisplayName("a molded value with /lines gains the line feed too")
        void aMoldedValueWithLinesGainsTheLineFeed(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt 12""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("12\n");
        }

        @Test
        @DisplayName("a block with /lines writes each value formed with a break")
        void aBlockWithLinesWritesEachValueAndABreak(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt [{a} {b}]""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("a\nb\n");
        }

        @Test
        @DisplayName("the values of a /lines block are formed, not molded")
        void theValuesOfALinesBlockAreFormed(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt [1 2]""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("1\n2\n");
        }

        @Test
        @DisplayName("an empty block with /lines writes nothing")
        void anEmptyBlockWithLinesWritesNothing(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt []""");
            assertThat(textIn(directory, "a.txt")).isEmpty();
        }

        @Test
        @DisplayName("a binary with /lines gains the line feed byte too")
        void aBinaryWithLinesGainsTheLineFeed(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.bin #{41}""");
            assertThat(bytesIn(directory, "a.bin")).containsExactly(0x41, 0x0A);
        }

        @Test
        @DisplayName("a string with /lines gains one line feed")
        void aStringWithLinesGainsOneLineFeed(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt {a^/}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("a\n\n");
        }

        @Test
        @DisplayName("an empty string with /lines writes the line feed alone")
        void anEmptyStringWithLinesWritesOneLineFeed(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt {}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("\n");
        }
    }

    @Nested
    @DisplayName("the answer, and the refinements that change nothing")
    class TheAnswerAndTheVestiges {

        @Test
        @DisplayName("the answer is the destination, so calls chain")
        void theAnswerIsTheDestination(@TempDir Path directory) {
            Interpreter interpreter = writingUnder(directory);
            assertThat(answerTo(interpreter, """
                    write %a.txt {x}""")).isEqualTo("%a.txt");
            assertThat(answerTo(interpreter, """
                    read/string write %b.txt {hi}""")).isEqualTo("\"hi\"");
        }

        @Test
        @DisplayName("/binary changes nothing for a file destination")
        void binaryChangesNothing(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/binary %a.txt {a^/}""");
            answerTo(interpreter, """
                    write %b.txt {a^/}""");
            assertThat(bytesIn(directory, "a.txt")).isEqualTo(bytesIn(directory, "b.txt"));
        }

        @Test
        @DisplayName("/allow is accepted and its modes never reach the write")
        void allowChangesNothing(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/allow %a.txt {x} [owner-read owner-write]""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("x");
        }

        @Test
        @DisplayName("/all is accepted and changes nothing")
        void allChangesNothing(@TempDir Path directory) throws Exception {
            Interpreter interpreter = writingUnder(directory);
            answerTo(interpreter, """
                    write/all %a.txt {x}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("x");
        }
    }

    @Nested
    @DisplayName("wrong types are refused, not coerced")
    class WrongTypes {

        @Test
        @DisplayName("a bound that is not a number is refused")
        void aBoundOfTheWrongTypeIsRefused(@TempDir Path directory) {
            Interpreter interpreter = writingUnder(directory);
            assertThat(errorIdOf(interpreter, """
                    write/part %a.txt {abc} {2}""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("a seek position that is not a number is refused")
        void aSeekOfTheWrongTypeIsRefused(@TempDir Path directory) {
            Interpreter interpreter = writingUnder(directory);
            assertThat(errorIdOf(interpreter, """
                    write/seek %a.txt {x} {0}""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("access modes that are not a block are refused")
        void allowNeedsABlock(@TempDir Path directory) {
            Interpreter interpreter = writingUnder(directory);
            assertThat(errorIdOf(interpreter, """
                    write/allow %a.txt {x} 5""")).isEqualTo("expect-arg");
        }
    }

    @Test
    @DisplayName("a read-only port refuses the seeking and appending forms too")
    void aReadOnlyPortRefusesEveryForm(@TempDir Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory).readOnly());
        assertThat(errorIdOf(interpreter, """
                write/append %a.txt {x}""")).isEqualTo("read-only");
        assertThat(errorIdOf(interpreter, """
                write/seek %a.txt {x} 0""")).isEqualTo("read-only");
    }

    @Test
    @DisplayName("a refinement write still needs the files grant")
    void refinementsStillNeedTheGrant(@TempDir Path directory) {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard());
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        assertThat(errorIdOf(interpreter, """
                write/append %a.txt {x}""")).isEqualTo("no-service");
    }
}
