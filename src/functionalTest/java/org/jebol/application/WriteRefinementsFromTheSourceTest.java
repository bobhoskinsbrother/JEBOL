package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WriteRefinementsFromTheSourceTest {

    private static Interpreter makeAnInterpreterWritingUnder(Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().grant(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootFileSystem(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(Interpreter interpreter, String source) {
        String wrapped = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        interpreter.defineNewWordsIn(wrapped);
        return interpreter.display(interpreter.run(wrapped));
    }

    private static String textIn(Path directory, String name) throws Exception {
        return Files.readString(directory.resolve(name));
    }

    private static byte[] readTheBytesIn(Path directory, String name) throws Exception {
        return Files.readAllBytes(directory.resolve(name));
    }

    @Nested
    @DisplayName("where the write lands: plain, /seek, /append")
    class WhereTheWriteLands {

        @Test
        @DisplayName("a plain write replaces the file, and what was there is gone")
        void replacesTheFileOnAPlainWrite(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {a longer earlier content}""");
            answerTo(interpreter, """
                    write %a.txt {x}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("x");
        }

        @Test
        @DisplayName("seeking overwrites in place and leaves the tail")
        void leavesTheTailBeyondTheDataOnSeeking(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {abcd}""");
            answerTo(interpreter, """
                    write/seek %a.txt {XY} 0""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("XYcd");
        }

        @Test
        @DisplayName("a seek in the middle overwrites only what the data covers")
        void overwritesOnlyThatMuchSeekingInTheMiddle(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {abcd}""");
            answerTo(interpreter, """
                    write/seek %a.txt {X} 1""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("aXcd");
        }

        @Test
        @DisplayName("a seek at the size writes at the end")
        void appendsOnSeekingTheSize(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {ab}""");
            answerTo(interpreter, """
                    write/seek %a.txt {c} 2""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("a seek past the end is clipped to the size, leaving no hole")
        void clipsASeekPastTheEndToTheSize(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {ab}""");
            answerTo(interpreter, """
                    write/seek %a.txt {c} 100""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("seeking into a missing file creates it")
        void createsAMissingFileOnSeeking(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/seek %new.txt {x} 0""");
            assertThat(textIn(directory, "new.txt")).isEqualTo("x");
        }

        @Test
        @DisplayName("a decimal seek position is a number and is accepted")
        void acceptsADecimalSeek(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {abcd}""");
            answerTo(interpreter, """
                    write/seek %a.txt {X} 1.0""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("aXcd");
        }

        @Test
        @DisplayName("a negative seek raises out-of-range")
        void raisesOutOfRangeForANegativeSeek(@TempDir Path directory) {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            assertThat(readTheErrorIdOf(interpreter, """
                    write/seek %a.txt {x} -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("appending adds at the end and keeps what was there")
        void addsAtTheEndOnAppending(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {ab}""");
            answerTo(interpreter, """
                    write/append %a.txt {c}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("appending to a missing file creates it")
        void createsAMissingFileOnAppending(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/append %new.txt {x}""");
            assertThat(textIn(directory, "new.txt")).isEqualTo("x");
        }

        @Test
        @DisplayName("when /seek and /append are both given, the seek position wins")
        void letsTheSeekPositionBeatAnAppend(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
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
        void clipsText(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt {abc} 2""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("ab");
        }

        @Test
        @DisplayName("a bound equal to the length writes it all")
        void writesItAllAtTheLength(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt {abc} 3""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("a bound larger than the data writes the whole data")
        void writesItAllWhenLargerThanTheData(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt {abc} 4""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("abc");
        }

        @Test
        @DisplayName("a bound of zero writes nothing and still makes the file")
        void writesNothingAtZeroButStillMakesTheFile(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {earlier}""");
            answerTo(interpreter, """
                    write/part %a.txt {abc} 0""");
            assertThat(textIn(directory, "a.txt")).isEmpty();
        }

        @Test
        @DisplayName("a negative bound raises out-of-range")
        void raisesOutOfRangeWhenNegative(@TempDir Path directory) {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            assertThat(readTheErrorIdOf(interpreter, """
                    write/part %a.txt {abc} -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a negative bound is refused even where the bound would not apply")
        void refusesANegativeEvenForAChar(@TempDir Path directory) {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            assertThat(readTheErrorIdOf(interpreter, """
                    write/part %a.txt #"a" -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a decimal bound is a number and is accepted")
        void acceptsADecimal(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt {abc} 2.0""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("ab");
        }

        @Test
        @DisplayName("the bound counts bytes of a binary")
        void clipsTheBytesOfABinary(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.bin #{010203} 2""");
            assertThat(readTheBytesIn(directory, "a.bin"))
                    .containsExactly(0x01, 0x02);
        }

        @Test
        @DisplayName("the bound counts from the series position")
        void countsFromTheSeriesPosition(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt next {abc} 1""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("b");
        }

        @Test
        @DisplayName("the bound applies to the molded text of any other value")
        void clipsTheMoldedText(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt [1 2] 3""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("[1 ");
        }

        @Test
        @DisplayName("a char is written whole whatever the bound")
        void writesACharWholeWhateverTheBound(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/part %a.txt #"a" 0""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("a");
        }

        @Test
        @DisplayName("a block with /lines ignores the bound")
        void isIgnoredForABlockWithLines(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/lines/part %a.txt [{a} {b}] 1""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("a\nb\n");
        }
    }

    @Nested
    @DisplayName("what each type of data becomes in the file")
    class WhatTheDataBecomes {

        @Test
        @DisplayName("writing starts from the series position")
        void startsFromTheSeriesPosition(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt next {ab}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("b");
        }

        @Test
        @DisplayName("a binary is written from its position too")
        void writesABinaryFromItsPosition(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.bin next #{0102}""");
            assertThat(readTheBytesIn(directory, "a.bin")).containsExactly(0x02);
        }

        @Test
        @DisplayName("text crosses the boundary as UTF-8")
        void textCrossesAsUtf8(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt {á}""");
            assertThat(readTheBytesIn(directory, "a.txt")).containsExactly(0xC3, 0xA1);
        }

        @Test
        @DisplayName("a char writes its UTF-8 bytes")
        void writesACharsUtf8Bytes(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt #"á\"""");
            assertThat(readTheBytesIn(directory, "a.txt")).containsExactly(0xC3, 0xA1);
        }

        @Test
        @DisplayName("a block without /lines is molded, brackets and all")
        void moldsABlockWithoutLines(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt [1 2]""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("any other value is molded and the text written")
        void moldsAndWritesAnyOtherValue(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt 12""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("12");
        }

        @Test
        @DisplayName("a file as data is molded too: only string! passes as text")
        void moldsAFileAsData(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write %a.txt %b.txt""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("%b.txt");
        }

        @Test
        @DisplayName("a molded value with /lines gains the line feed too")
        void addsALineFeedToAMoldedValueWithLines(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt 12""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("12\n");
        }

        @Test
        @DisplayName("a block with /lines writes each value formed with a break")
        void writesEachValueAndABreakForABlockWithLines(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt [{a} {b}]""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("a\nb\n");
        }

        @Test
        @DisplayName("the values of a /lines block are formed, not molded")
        void formsTheValuesOfALinesBlock(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt [1 2]""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("1\n2\n");
        }

        @Test
        @DisplayName("an empty block with /lines writes nothing")
        void writesNothingForAnEmptyBlockWithLines(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt []""");
            assertThat(textIn(directory, "a.txt")).isEmpty();
        }

        @Test
        @DisplayName("a binary with /lines gains the line feed byte too")
        void addsALineFeedToABinaryWithLines(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.bin #{41}""");
            assertThat(readTheBytesIn(directory, "a.bin")).containsExactly(0x41, 0x0A);
        }

        @Test
        @DisplayName("a string with /lines gains one line feed")
        void addsOneLineFeedToAStringWithLines(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/lines %a.txt {a^/}""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("a\n\n");
        }

        @Test
        @DisplayName("an empty string with /lines writes the line feed alone")
        void writesOneLineFeedForAnEmptyStringWithLines(@TempDir Path directory)
                throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
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
        void answerTheDestination(@TempDir Path directory) {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            assertThat(answerTo(interpreter, """
                    write %a.txt {x}""")).isEqualTo("%a.txt");
            assertThat(answerTo(interpreter, """
                    read/string write %b.txt {hi}""")).isEqualTo("\"hi\"");
        }

        @Test
        @DisplayName("/binary changes nothing for a file destination")
        void changeNothingForBinary(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/binary %a.txt {a^/}""");
            answerTo(interpreter, """
                    write %b.txt {a^/}""");
            assertThat(readTheBytesIn(directory, "a.txt")).isEqualTo(readTheBytesIn(directory, "b.txt"));
        }

        @Test
        @DisplayName("/allow is accepted and its modes never reach the write")
        void allowChangesNothing(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            answerTo(interpreter, """
                    write/allow %a.txt {x} [owner-read owner-write]""");
            assertThat(textIn(directory, "a.txt")).isEqualTo("x");
        }

        @Test
        @DisplayName("/all is accepted and changes nothing")
        void changeNothingForAll(@TempDir Path directory) throws Exception {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
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
        void refuseABoundOfTheWrongType(@TempDir Path directory) {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            assertThat(readTheErrorIdOf(interpreter, """
                    write/part %a.txt {abc} {2}""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("a seek position that is not a number is refused")
        void refuseASeekOfTheWrongType(@TempDir Path directory) {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            assertThat(readTheErrorIdOf(interpreter, """
                    write/seek %a.txt {x} {0}""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("access modes that are not a block are refused")
        void allowNeedsABlock(@TempDir Path directory) {
            Interpreter interpreter = makeAnInterpreterWritingUnder(directory);
            assertThat(readTheErrorIdOf(interpreter, """
                    write/allow %a.txt {x} 5""")).isEqualTo("expect-arg");
        }
    }

    @Test
    @DisplayName("a read-only port refuses the seeking and appending forms too")
    void refusesEveryFormOnAReadOnlyPort(@TempDir Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().grant(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootFileSystem(directory).readOnlyFileSystem());
        assertThat(readTheErrorIdOf(interpreter, """
                write/append %a.txt {x}""")).isEqualTo("read-only");
        assertThat(readTheErrorIdOf(interpreter, """
                write/seek %a.txt {x} 0""")).isEqualTo("read-only");
    }

    @Test
    @DisplayName("a refinement write still needs the filePort grant")
    void stillNeedsTheGrantWithRefinements(@TempDir Path directory) {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard());
        interpreter.useFileSystem(FileSystemPort.rootFileSystem(directory));
        assertThat(readTheErrorIdOf(interpreter, """
                write/append %a.txt {x}""")).isEqualTo("no-service");
    }
}
