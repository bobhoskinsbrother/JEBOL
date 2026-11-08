package org.jebol.adapter.cli;

import org.jebol.domain.host.ConsoleKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TheTerminalKeyDecoderFromTheSourceTest {

    private static final int DEL_IS_ERASE = 0x7F;

    private static final class KeysPressed extends InputStream {

        private final Deque<byte[]> presses = new ArrayDeque<>();
        private byte[] arriving = new byte[0];
        private int readSoFar;

        private KeysPressed(String... presses) {
            Arrays.stream(presses)
                    .map(each -> each.getBytes(StandardCharsets.ISO_8859_1))
                    .forEach(this.presses::add);
        }

        @Override
        public int read() {
            if (readSoFar == arriving.length) {
                if (presses.isEmpty()) {
                    return -1;
                }
                arriving = presses.poll();
                readSoFar = 0;
            }
            return arriving[readSoFar++] & 0xFF;
        }

        @Override
        public int available() {
            return arriving.length - readSoFar;
        }
    }

    private static Optional<ConsoleKey> decode(int erase, String... presses) {
        return new TerminalKeyDecoder(new KeysPressed(presses), erase).readKey();
    }

    private static ConsoleKey decodeOne(String pressed) {
        return decode(DEL_IS_ERASE, pressed).orElseThrow();
    }

    private static String describe(ConsoleKey key) {
        return key.character() + " " + key.virtualKey() + " "
                + (key.control() ? "c" : "-") + (key.shift() ? "s" : "-") + (key.alt() ? "a" : "-");
    }

    @Nested
    @DisplayName("ESC [ sequences, the CSI keys")
    class CsiSequences {

        @ParameterizedTest(name = "ESC [ {0} is {1}")
        @CsvSource({
                "A,      0 6 ---",
                "B,      0 8 ---",
                "C,      0 7 ---",
                "D,      0 5 ---",
                "E,      0 33 ---",
                "F,      0 3 ---",
                "H,      0 4 ---",
                "Z,      0 31 ---",
                "1;2P,   0 11 -s-",
                "1;3Q,   0 12 --a",
                "1;4H,   0 4 -sa",
                "1;5A,   0 6 c--",
                "1;6R,   0 13 cs-",
                "1;7F,   0 3 c-a",
                "1;8S,   0 14 csa",
                "11~,    0 11 ---",
                "12~,    0 12 ---",
                "13~,    0 13 ---",
                "14~,    0 14 ---",
                "15~,    0 15 ---",
                "17~,    0 16 ---",
                "18~,    0 17 ---",
                "19~,    0 18 ---",
                "17^,    0 16 c--",
                "2~,     0 9 ---",
                "20~,    0 19 ---",
                "21~,    0 20 ---",
                "23~,    0 21 ---",
                "24^,    0 22 c--",
                "25~,    0 15 -s-",
                "26~,    0 16 -s-",
                "28~,    0 17 -s-",
                "29~,    0 18 -s-",
                "200~,   0 23 ---",
                "201~,   0 24 ---",
                "3~,     0 10 ---",
                "3^,     0 10 c--",
                "3;5~,   0 10 c--",
                "31~,    0 19 -s-",
                "32~,    0 20 -s-",
                "33~,    0 21 -s-",
                "34~,    0 22 -s-",
                "31^,    0 19 c--",
                "34^,    0 22 c--",
                "4~,     0 3 ---",
                "5~,     0 1 ---",
                "5;2~,   0 1 -s-",
                "6~,     0 2 ---",
                "6;2~,   0 2 -s-",
                "7~,     0 4 ---",
                "8~,     0 3 ---",
        })
        void decodesEachSequence(String sequence, String decoded) {
            assertThat(describe(decodeOne("\u001b[" + sequence))).isEqualTo(decoded);
        }
    }

    @Nested
    @DisplayName("ESC O sequences, the SS3 keys and the keypad")
    class Ss3Sequences {

        @ParameterizedTest(name = "ESC O {0} is {1}")
        @CsvSource({
                "A, 0 6 ---",
                "B, 0 8 ---",
                "C, 0 7 ---",
                "D, 0 5 ---",
                "H, 0 4 ---",
                "F, 0 3 ---",
                "P, 0 11 ---",
                "Q, 0 12 ---",
                "R, 0 13 ---",
                "S, 0 14 ---",
                "M, 13 0 ---",
                "X, 61 0 ---",
                "j, 42 0 ---",
                "k, 43 0 ---",
                "l, 44 0 ---",
                "m, 45 0 ---",
                "n, 46 0 ---",
                "o, 47 0 ---",
                "p, 48 0 ---",
                "y, 57 0 ---",
        })
        void decodesEachSequence(String letter, String decoded) {
            assertThat(describe(decodeOne("\u001bO" + letter))).isEqualTo(decoded);
        }
    }

    @Nested
    @DisplayName("ESC with one character, and ESC alone")
    class EscapeAndOneCharacter {

        @Test
        @DisplayName("ESC b and ESC f are left and right with control")
        void readsTheWordJumps() {
            assertThat(describe(decodeOne("\u001bb"))).isEqualTo("0 5 c--");
            assertThat(describe(decodeOne("\u001bf"))).isEqualTo("0 7 c--");
        }

        @Test
        @DisplayName("ESC with a printable character is that character with alt")
        void readsAltAndAPrintable() {
            assertThat(describe(decodeOne("\u001ba"))).isEqualTo("97 0 --a");
            assertThat(describe(decodeOne("\u001bA"))).isEqualTo("65 0 --a");
        }

        @Test
        @DisplayName("ESC with a control character is a control key numbered by the character, with control and alt")
        void readsAltAndAControlCharacter() {
            assertThat(describe(decodeOne("\u001b\u0001"))).isEqualTo("0 1 c-a");
        }

        @Test
        @DisplayName("ESC with nothing waiting after it is the escape key")
        void readsALoneEscape() {
            assertThat(describe(decode(DEL_IS_ERASE, "\u001b", "a").orElseThrow())).isEqualTo("0 25 ---");
        }

        @Test
        @DisplayName("a sequence it does not know is read and dropped, and the next key answered")
        void dropsAnUnknownSequence() {
            assertThat(describe(decode(DEL_IS_ERASE, "\u001b[9~", "a").orElseThrow())).isEqualTo("97 0 ---");
            assertThat(describe(decode(DEL_IS_ERASE, "\u001bOz", "b").orElseThrow())).isEqualTo("98 0 ---");
            assertThat(describe(decode(DEL_IS_ERASE, "\u001b\u0080", "c").orElseThrow())).isEqualTo("99 0 ---");
        }

        @Test
        @DisplayName("a sequence cut off by the end of input answers nothing")
        void answersNothingForACutSequence() {
            assertThat(decode(DEL_IS_ERASE, "\u001b[")).isEmpty();
            assertThat(decode(DEL_IS_ERASE, "\u001b[1;")).isEmpty();
        }
    }

    @Nested
    @DisplayName("one byte, and the characters past ASCII")
    class PlainKeys {

        @Test
        @DisplayName("a printable character is itself, with no modifier")
        void readsAPrintable() {
            assertThat(describe(decodeOne("a"))).isEqualTo("97 0 ---");
        }

        @ParameterizedTest(name = "byte {0} is character {0} with control")
        @CsvSource({"1", "9", "13", "31"})
        void readsAControlCharacterWithControl(int code) {
            assertThat(describe(decodeOne(String.valueOf((char) code)))).isEqualTo(code + " 0 c--");
        }

        @Test
        @DisplayName("the terminal's erase character is backspace, and the other of DEL and Ctrl-H is backspace with control")
        void readsBackspaceByTheEraseCharacter() {
            assertThat(describe(decode(0x7F, "\u007f").orElseThrow())).isEqualTo("0 32 ---");
            assertThat(describe(decode(0x7F, "\b").orElseThrow())).isEqualTo("0 32 c--");
            assertThat(describe(decode(0x08, "\b").orElseThrow())).isEqualTo("0 32 ---");
            assertThat(describe(decode(0x08, "\u007f").orElseThrow())).isEqualTo("0 32 c--");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "two bytes,   'Ã©', 233",
                "three bytes, 'ä¸­', 20013",
                "four bytes,  'ð\u009f\u0098\u0080', 128512",
        })
        void readsAUtf8Character(String named, String pressed, int codepoint) {
            assertThat(describe(decodeOne(pressed))).isEqualTo(codepoint + " 0 ---");
        }

        @Test
        @DisplayName("a lead byte without its continuation, or a byte that cannot lead, is dropped and the next key answered")
        void dropsAMalformedCharacter() {
            assertThat(describe(decode(DEL_IS_ERASE, "ÃA", "a").orElseThrow())).isEqualTo("97 0 ---");
            assertThat(describe(decode(DEL_IS_ERASE, "\u0080", "b").orElseThrow())).isEqualTo("98 0 ---");
            assertThat(describe(decode(DEL_IS_ERASE, "ÿ", "c").orElseThrow())).isEqualTo("99 0 ---");
        }

        @Test
        @DisplayName("the end of input answers nothing")
        void answersNothingAtTheEnd() {
            assertThat(decode(DEL_IS_ERASE)).isEmpty();
            assertThat(decode(DEL_IS_ERASE, "ä¸")).isEmpty();
        }
    }
}
