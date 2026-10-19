package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadRefinementsFromTheSourceTest {

    private static Interpreter makeAnInterpreterReadingUnder(Path directory) {
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

    private static void writeAFile(Path directory, String name, byte[] content)
            throws Exception {
        Files.write(directory.resolve(name), content);
    }

    @Nested
    @DisplayName("the shape of the answer")
    class TheShapeOfTheAnswer {

        @Test
        @DisplayName("a plain read answers the file's bytes")
        void answersTheBytesForAPlainRead(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read %a.txt""")).isEqualTo("#{6162}");
        }

        @Test
        @DisplayName("/binary is the same answer asked for outright")
        void answersTheSameForBinary(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x0A});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/binary %a.txt""")).isEqualTo("#{610A}");
        }

        @Test
        @DisplayName("/string decodes UTF-8 and folds CRLF to LF")
        void stringDecodesAndFoldsLineEndings(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", "a\r\nb\nc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    (read/string %a.txt) = {a^/b^/c}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/lines answers a block of the lines, terminators kept out")
        void linesAnswersABlock(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", "a\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/lines %a.txt""")).isEqualTo("[\"a\" \"\"]");
        }

        @Test
        @DisplayName("an empty file has no lines at all")
        void findsNoLinesInAnEmptyFile(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[0]);
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/lines %a.txt""")).isEqualTo("[]");
        }

        @Test
        @DisplayName("a lone carriage return splits a line too")
        void splitsAtALoneCarriageReturn(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x0D, 0x62});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/lines %a.txt""")).isEqualTo("[\"a\" \"b\"]");
        }

        @Test
        @DisplayName("/string on bytes that do not decode answers the bytes")
        void undecodableBytesAnswerTheBytes(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.bin", new byte[] {(byte) 0xFF, 0x61});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/string %a.bin""")).isEqualTo("#{FF61}");
        }

        @Test
        @DisplayName("a bound that cuts a character in half answers the bytes")
        void answersTheBytesForABoundCuttingACharacter(@TempDir Path directory)
                throws Exception {
            writeAFile(directory, "a.txt", "á".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/string/part %a.txt 1""")).isEqualTo("#{C3}");
        }

        @Test
        @DisplayName("/string strips a UTF-8 byte order mark")
        void stripsAUtf8ByteOrderMark(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {
                    (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x61});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    (read/string %a.txt) = {a}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/string reads UTF-16 by its byte order mark")
        void readsUtf16ByItsMark(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", "a".getBytes(
                    java.nio.charset.StandardCharsets.UTF_16));
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    (read/string %a.txt) = {a}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/all is accepted and changes nothing for a file")
        void changesNothingForAll(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/all %a.txt""")).isEqualTo("#{61}");
        }

        @Test
        @DisplayName("reading a directory answers the names in it")
        void answersTheNamesInADirectory(@TempDir Path directory) throws Exception {
            writeAFile(directory, "one.txt", new byte[0]);
            writeAFile(directory, "two.txt", new byte[0]);
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read %./""")).isEqualTo("[%one.txt %two.txt]");
        }
    }

    @Nested
    @DisplayName("/seek skips into the file")
    class TheSeek {

        @Test
        @DisplayName("a seek skips that many bytes")
        void skipsBytes(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x62, 0x63});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/seek %a.txt 1""")).isEqualTo("#{6263}");
        }

        @Test
        @DisplayName("a seek of zero reads the whole file")
        void readsTheWholeAtZero(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/seek %a.txt 0""")).isEqualTo("#{6162}");
        }

        @Test
        @DisplayName("a seek at the size answers empty")
        void answersEmptyAtTheSize(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/seek %a.txt 2""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("a seek past the end is clipped, so the answer is empty")
        void clipsPastTheEnd(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/seek %a.txt 100""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("a negative seek raises out-of-range")
        void raisesWhenNegative(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61});
            assertThat(readTheErrorIdOf(makeAnInterpreterReadingUnder(directory), """
                    read/seek %a.txt -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a seek position that is not a number is refused")
        void refusesTheWrongType(@TempDir Path directory) {
            assertThat(readTheErrorIdOf(makeAnInterpreterReadingUnder(directory), """
                    read/seek %a.txt {0}""")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("/part bounds the read, in bytes")
    class TheBound {

        @Test
        @DisplayName("a bound reads that many bytes")
        void readsThatMany(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x62, 0x63});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/part %a.txt 2""")).isEqualTo("#{6162}");
        }

        @Test
        @DisplayName("a bound of zero reads nothing")
        void readsNothingAtZero(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/part %a.txt 0""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("a bound past what remains reads what remains")
        void readsWhatRemainsPastTheEnd(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/part %a.txt 5""")).isEqualTo("#{6162}");
        }

        @Test
        @DisplayName("a negative bound reads backwards from the seek position")
        void readsBackwardsWhenNegative(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x62, 0x63, 0x64, 0x65});
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    read/seek/part %a.txt 5 -2""")).isEqualTo("#{6465}");
        }

        @Test
        @DisplayName("backwards past the start raises out-of-range")
        void backwardsPastTheStartRaises(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(readTheErrorIdOf(makeAnInterpreterReadingUnder(directory), """
                    read/seek/part %a.txt 1 -2""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("with no seek any negative bound raises")
        void raisesForANegativeBoundWithNoSeek(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", new byte[] {0x61});
            assertThat(readTheErrorIdOf(makeAnInterpreterReadingUnder(directory), """
                    read/part %a.txt -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("the bound counts bytes even under /string")
        void countsBytesUnderString(@TempDir Path directory) throws Exception {
            writeAFile(directory, "a.txt", "áb".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(answerTo(makeAnInterpreterReadingUnder(directory), """
                    (read/string/part %a.txt 2) = {á}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a bound that is not a number is refused")
        void refusesTheWrongType(@TempDir Path directory) {
            assertThat(readTheErrorIdOf(makeAnInterpreterReadingUnder(directory), """
                    read/part %a.txt {2}""")).isEqualTo("expect-arg");
        }
    }

    @Test
    @DisplayName("a file that is not there still raises cannot-open")
    void raisesForAMissingFile(@TempDir Path directory) {
        assertThat(readTheErrorIdOf(makeAnInterpreterReadingUnder(directory), """
                read %nothing.txt""")).isEqualTo("cannot-open");
    }
}
