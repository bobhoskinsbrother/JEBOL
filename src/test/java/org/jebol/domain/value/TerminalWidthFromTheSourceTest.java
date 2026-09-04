package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code s/width}, which is how many columns a string takes on a terminal.
 *
 * <p>Three different questions a string answers about its length, and they
 * disagree on purpose: {@code length?} counts characters, {@code s/size}
 * counts the bytes they encode to, and {@code s/width} counts the columns they
 * occupy. So a string of three characters can be five bytes long and two
 * columns wide.
 *
 * <p>JEBOL worked the width out from Java's own character properties -- a
 * combining mark takes none, a CJK script takes two, plus a handful of ranges
 * written out by hand. That is a reasonable guess and it is not the answer:
 * the C looks each character up in four sorted tables generated from
 * {@code UnicodeData.txt} and {@code EastAsianWidth.txt}. Those tables are now
 * ported by {@code scripts/terminal-width-table.py} rather than approximated,
 * which is what makes the lightning bolt two columns wide -- it is in the East
 * Asian wide table and belongs to no CJK script at all.
 */
class TerminalWidthFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("characters that take no columns")
    class TheZeroWidthOnes {

        @Test
        @DisplayName("a zero-width space is not counted, and walking past it shows so")
        void aZeroWidthSpace() {
            assertThat(answerTo("""
                    s: "a​b"
                    t: next s
                    u: next t
                    reduce [length? s s/width t/width u/width]"""))
                    .isEqualTo("[3 2 1 1]");
        }

        @Test
        @DisplayName("and a string with nothing left in it is no columns wide")
        void nothingLeftIsNoColumns() {
            assertThat(answerTo("""
                    s: tail "a​b"
                    s/width""")).isEqualTo("0");
        }

        @Test
        @DisplayName("a combining mark takes none either")
        void aCombiningMarkTakesNone() {
            assertThat(answerTo("""
                    s: "é"
                    reduce [length? s s/width]""")).isEqualTo("[2 1]");
        }
    }

    @Nested
    @DisplayName("characters that take two")
    class TheWideOnes {

        @Test
        @DisplayName("a CJK character, and a symbol that belongs to no CJK script")
        void aCjkCharacterAndASymbol() {
            assertThat(answerTo("""
                    s: "a⚡中"
                    t: next s
                    u: next t
                    reduce [length? s s/width t/width u/width]"""))
                    .isEqualTo("[3 5 4 2]");
        }

        @Test
        @DisplayName("an emoji outside the basic plane takes two as well")
        void anEmojiTakesTwo() {
            assertThat(answerTo("""
                    s: "🙂"
                    reduce [length? s s/width s/size]""")).isEqualTo("[1 2 4]");
        }
    }

    @Nested
    @DisplayName("the three lengths, which are three different questions")
    class TheThreeLengths {

        @Test
        @DisplayName("characters, bytes and columns for one awkward string")
        void charactersBytesAndColumns() {
            assertThat(answerTo("""
                    s: "a​b"
                    reduce [length? s s/size s/width]""")).isEqualTo("[3 5 2]");
        }

        @Test
        @DisplayName("and all three agree while the text is plain")
        void theyAgreeOnPlainText() {
            assertThat(answerTo("""
                    s: "abc"
                    reduce [length? s s/size s/width]""")).isEqualTo("[3 3 3]");
        }
    }

    @Nested
    @DisplayName("the table itself")
    class TheTable {

        @Test
        @DisplayName("a printable ASCII character is one column without a lookup")
        void asciiIsOneColumn() {
            assertThat(TerminalWidth.of('a')).isEqualTo(1);
            assertThat(TerminalWidth.of(' ')).isEqualTo(1);
            assertThat(TerminalWidth.of('~')).isEqualTo(1);
        }

        @Test
        @DisplayName("a control character is none, delete included")
        void controlCharactersAreNone() {
            assertThat(TerminalWidth.of(0)).isZero();
            assertThat(TerminalWidth.of('\n')).isZero();
            assertThat(TerminalWidth.of(0x7F)).isZero();
        }

        @Test
        @DisplayName("the boundaries of a wide range are wide and just outside it is not")
        void theBoundariesOfARange() {
            assertThat(TerminalWidth.of(0x1100)).isEqualTo(2);
            assertThat(TerminalWidth.of(0x115F)).isEqualTo(2);
            assertThat(TerminalWidth.of(0x10FF)).isEqualTo(1);
            assertThat(TerminalWidth.of(0x1160)).isEqualTo(1);
        }

        @Test
        @DisplayName("and a run of characters is the sum of them")
        void aRunIsTheSum() {
            assertThat(TerminalWidth.of(new int[] {'a', 0x200B, 'b'})).isEqualTo(2);
            assertThat(TerminalWidth.of(new int[] {})).isZero();
        }
    }
}
