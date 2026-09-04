package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Putting things into a binary, read out of {@code Join_Binary} in
 * {@code s-make.c} and {@code Modify_String} in {@code f-modify.c}.
 *
 * <p>One rule underneath all of it: text put into a binary becomes its
 * UTF-8 bytes. A string, a file, a character and each item of a block all
 * go the same way, so a character above the ASCII range contributes
 * several bytes rather than one.
 *
 * <p>That makes the count of bytes added different from the count of
 * things added, which is where {@code /part} gets interesting: it counts
 * characters of the source and the encoding happens afterwards, so one
 * character can be three bytes.
 */
class BinaryModifyFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("a binary into a binary")
    class FromABinary {

        @Test
        @DisplayName("the bytes go in as they are")
        void bytesAreCopied() {
            assertThat(answerTo("#{0102} = append #{01} #{02}")).isEqualTo("#(true)");
            assertThat(answerTo("#{0201} = head insert #{01} #{02}")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("APPEND ignores the position and INSERT does not")
        void thePositionMatters() {
            assertThat(answerTo("#{0102} = append next #{01} #{02}"))
                    .as("APPEND always writes at the end")
                    .isEqualTo("#(true)");
            assertThat(answerTo("#{0102} = head insert next #{01} #{02}"))
                    .as("INSERT writes where the position is")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/part counts bytes when the source is a binary")
        void partCountsBytes() {
            assertThat(answerTo("#{01} = append/part #{} #{0102} 1")).isEqualTo("#(true)");
            assertThat(answerTo("#{0100} = head insert/part #{00} #{0102} 1"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a binary appended to itself doubles rather than looping")
        void appendingToItself() {
            assertThat(answerTo("b: #{FF} #{FFFF} = append b b")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("text into a binary")
    class FromText {

        @Test
        @DisplayName("an ASCII character is one byte")
        void theOnByteCase() {
            assertThat(answerTo("#{0001} = append #{00} \"^(01)\"")).isEqualTo("#(true)");
            assertThat(answerTo("#{0100} = head insert #{00} \"^(01)\"")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a character above ASCII is its UTF-8 bytes")
        void aWideCharacterIsSeveralBytes() {
            assertThat(answerTo("#{00E28690} = append #{00} \"^(2190)\""))
                    .isEqualTo("#(true)");
            assertThat(answerTo("#{E2869000} = head insert #{00} \"^(2190)\""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a file goes in as UTF-8 too, without its percent sign")
        void aFileIsText() {
            assertThat(answerTo("#{616263} = append #{} %abc")).isEqualTo("#(true)");
            assertThat(answerTo("#{C3A162} = append #{} %áb"))
                    .as("the accented letter is two bytes")
                    .isEqualTo("#(true)");
            assertThat(answerTo("#{61626300} = head insert #{00} %abc")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a character value goes the same way as a string of one")
        void aCharacterIsEncodedToo() {
            assertThat(answerTo("#{0001} = append #{00} #\"^(01)\"")).isEqualTo("#(true)");
            assertThat(answerTo("#{00E28690} = append #{00} #\"^(2190)\""))
                    .isEqualTo("#(true)");
            assertThat(answerTo("#{E2869000} = head insert #{00} #\"^(2190)\""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/part on a character changes nothing, however large")
        void aCharacterHasNoParts() {
            assertThat(answerTo("#{0001} = append/part #{00} #\"^(01)\" 10"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("#{00E28690} = append/part #{00} #\"^(2190)\" 10"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/part counts characters of the source, not bytes of the answer")
        void partCountsCharacters() {
            assertThat(answerTo("#{01} = append/part #{} \"^(01)^(02)\" 1"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("#{E28690} = append/part #{} \"^(2190)\" 1"))
                    .as("one character, and it is three bytes")
                    .isEqualTo("#(true)");
            assertThat(answerTo("#{E2869000} = head insert/part #{00} \"^(2190)\" 1"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty string adds nothing")
        void theDegenerateSource() {
            assertThat(answerTo("#{00} = append #{00} \"\"")).isEqualTo("#(true)");
            assertThat(answerTo("#{00} = append/part #{00} \"ab\" 0")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("Join_Binary: a block into a binary")
    class FromABlock {

        @Test
        @DisplayName("each item is converted and the results run together")
        void aBlockSpreads() {
            assertThat(answerTo("#{00010361} = append #{} [#{00} #{01} 3 #\"a\"]"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("#{00010361} = head insert #{} [#{00} #{01} 3 #\"a\"]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an integer in a block is one byte and must fit in one")
        void anIntegerIsAnOctet() {
            assertThat(answerTo("#{00} = append #{} [0]"))
                    .as("nought is the floor")
                    .isEqualTo("#(true)");
            assertThat(answerTo("#{FF} = append #{} [255]"))
                    .as("and 255 is the ceiling")
                    .isEqualTo("#(true)");
            assertThat(errorIdOf("append #{} [300]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("append #{} [256]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("append #{} [-1]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("insert #{} [300]")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("text in a block is UTF-8 like text anywhere else")
        void textInABlock() {
            assertThat(answerTo("#{616263} = append #{} [\"abc\"]")).isEqualTo("#(true)");
            assertThat(answerTo("#{E28690} = append #{} [\"^(2190)\"]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty block adds nothing")
        void theDegenerateBlock() {
            assertThat(answerTo("#{00} = append #{00} []")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("anything else in the block is refused")
        void aWrongTypeIsRefused() {
            assertThat(errorIdOf("append #{} [none]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("append #{} [1.5]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("append #{} [[1]]")).isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("other values into a binary")
    class FromOtherValues {

        @Test
        @DisplayName("a bare integer is one byte")
        void anIntegerIsOneByte() {
            assertThat(answerTo("#{0003} = append #{00} 3")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a tuple gives the octets it keeps")
        void aTupleIsItsOctets() {
            assertThat(answerTo("#{010203} = append #{} 1.2.3")).isEqualTo("#(true)");
        }
    }
}
