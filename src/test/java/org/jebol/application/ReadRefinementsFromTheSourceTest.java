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
 * Specified in {@code spec/natives.allium} under "Reading a file", read from
 * {@code p-file.c} and {@code port-test.r3}.
 */
class ReadRefinementsFromTheSourceTest {

    private static Interpreter readingUnder(Path directory) {
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

    private static void given(Path directory, String name, byte[] content)
            throws Exception {
        Files.write(directory.resolve(name), content);
    }

    @Nested
    @DisplayName("the shape of the answer")
    class TheShapeOfTheAnswer {

        @Test
        @DisplayName("a plain read answers the file's bytes")
        void aPlainReadAnswersTheBytes(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(readingUnder(directory), """
                    read %a.txt""")).isEqualTo("#{6162}");
        }

        @Test
        @DisplayName("/binary is the same answer asked for outright")
        void binaryIsTheSameAnswer(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x0A});
            assertThat(answerTo(readingUnder(directory), """
                    read/binary %a.txt""")).isEqualTo("#{610A}");
        }

        @Test
        @DisplayName("/string decodes UTF-8 and folds CRLF to LF")
        void stringDecodesAndFoldsLineEndings(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", "a\r\nb\nc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(answerTo(readingUnder(directory), """
                    (read/string %a.txt) = {a^/b^/c}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/lines answers a block of the lines, terminators kept out")
        void linesAnswersABlock(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", "a\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(answerTo(readingUnder(directory), """
                    read/lines %a.txt""")).isEqualTo("[\"a\" \"\"]");
        }

        @Test
        @DisplayName("an empty file has no lines at all")
        void anEmptyFileHasNoLines(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[0]);
            assertThat(answerTo(readingUnder(directory), """
                    read/lines %a.txt""")).isEqualTo("[]");
        }

        @Test
        @DisplayName("a lone carriage return splits a line too")
        void aLoneCarriageReturnSplits(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x0D, 0x62});
            assertThat(answerTo(readingUnder(directory), """
                    read/lines %a.txt""")).isEqualTo("[\"a\" \"b\"]");
        }

        @Test
        @DisplayName("/string on bytes that do not decode answers the bytes")
        void undecodableBytesAnswerTheBytes(@TempDir Path directory) throws Exception {
            given(directory, "a.bin", new byte[] {(byte) 0xFF, 0x61});
            assertThat(answerTo(readingUnder(directory), """
                    read/string %a.bin""")).isEqualTo("#{FF61}");
        }

        @Test
        @DisplayName("a bound that cuts a character in half answers the bytes")
        void aBoundCuttingACharacterAnswersTheBytes(@TempDir Path directory)
                throws Exception {
            given(directory, "a.txt", "á".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(answerTo(readingUnder(directory), """
                    read/string/part %a.txt 1""")).isEqualTo("#{C3}");
        }

        @Test
        @DisplayName("/string strips a UTF-8 byte order mark")
        void aUtf8ByteOrderMarkIsStripped(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {
                    (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x61});
            assertThat(answerTo(readingUnder(directory), """
                    (read/string %a.txt) = {a}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/string reads UTF-16 by its byte order mark")
        void utf16IsReadByItsMark(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", "a".getBytes(
                    java.nio.charset.StandardCharsets.UTF_16));
            assertThat(answerTo(readingUnder(directory), """
                    (read/string %a.txt) = {a}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/all is accepted and changes nothing for a file")
        void allChangesNothing(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61});
            assertThat(answerTo(readingUnder(directory), """
                    read/all %a.txt""")).isEqualTo("#{61}");
        }

        @Test
        @DisplayName("reading a directory answers the names in it")
        void readingADirectoryAnswersTheNames(@TempDir Path directory) throws Exception {
            given(directory, "one.txt", new byte[0]);
            given(directory, "two.txt", new byte[0]);
            assertThat(answerTo(readingUnder(directory), """
                    read %./""")).isEqualTo("[%one.txt %two.txt]");
        }
    }

    @Nested
    @DisplayName("/seek skips into the file")
    class TheSeek {

        @Test
        @DisplayName("a seek skips that many bytes")
        void aSeekSkipsBytes(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x62, 0x63});
            assertThat(answerTo(readingUnder(directory), """
                    read/seek %a.txt 1""")).isEqualTo("#{6263}");
        }

        @Test
        @DisplayName("a seek of zero reads the whole file")
        void aSeekOfZeroReadsTheWhole(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(readingUnder(directory), """
                    read/seek %a.txt 0""")).isEqualTo("#{6162}");
        }

        @Test
        @DisplayName("a seek at the size answers empty")
        void aSeekAtTheSizeAnswersEmpty(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(readingUnder(directory), """
                    read/seek %a.txt 2""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("a seek past the end is clipped, so the answer is empty")
        void aSeekPastTheEndIsClipped(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(readingUnder(directory), """
                    read/seek %a.txt 100""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("a negative seek raises out-of-range")
        void aNegativeSeekRaises(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61});
            assertThat(errorIdOf(readingUnder(directory), """
                    read/seek %a.txt -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a seek position that is not a number is refused")
        void aSeekOfTheWrongTypeIsRefused(@TempDir Path directory) {
            assertThat(errorIdOf(readingUnder(directory), """
                    read/seek %a.txt {0}""")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("/part bounds the read, in bytes")
    class TheBound {

        @Test
        @DisplayName("a bound reads that many bytes")
        void aBoundReadsThatMany(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x62, 0x63});
            assertThat(answerTo(readingUnder(directory), """
                    read/part %a.txt 2""")).isEqualTo("#{6162}");
        }

        @Test
        @DisplayName("a bound of zero reads nothing")
        void aBoundOfZeroReadsNothing(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61});
            assertThat(answerTo(readingUnder(directory), """
                    read/part %a.txt 0""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("a bound past what remains reads what remains")
        void aBoundPastTheEndReadsWhatRemains(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(answerTo(readingUnder(directory), """
                    read/part %a.txt 5""")).isEqualTo("#{6162}");
        }

        @Test
        @DisplayName("a negative bound reads backwards from the seek position")
        void aNegativeBoundReadsBackwards(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x62, 0x63, 0x64, 0x65});
            assertThat(answerTo(readingUnder(directory), """
                    read/seek/part %a.txt 5 -2""")).isEqualTo("#{6465}");
        }

        @Test
        @DisplayName("backwards past the start raises out-of-range")
        void backwardsPastTheStartRaises(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61, 0x62});
            assertThat(errorIdOf(readingUnder(directory), """
                    read/seek/part %a.txt 1 -2""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("with no seek any negative bound raises")
        void withNoSeekAnyNegativeBoundRaises(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", new byte[] {0x61});
            assertThat(errorIdOf(readingUnder(directory), """
                    read/part %a.txt -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("the bound counts bytes even under /string")
        void theBoundCountsBytesUnderString(@TempDir Path directory) throws Exception {
            given(directory, "a.txt", "áb".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(answerTo(readingUnder(directory), """
                    (read/string/part %a.txt 2) = {á}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a bound that is not a number is refused")
        void aBoundOfTheWrongTypeIsRefused(@TempDir Path directory) {
            assertThat(errorIdOf(readingUnder(directory), """
                    read/part %a.txt {2}""")).isEqualTo("expect-arg");
        }
    }

    @Test
    @DisplayName("a file that is not there still raises cannot-open")
    void aMissingFileRaises(@TempDir Path directory) {
        assertThat(errorIdOf(readingUnder(directory), """
                read %nothing.txt""")).isEqualTo("cannot-open");
    }
}
