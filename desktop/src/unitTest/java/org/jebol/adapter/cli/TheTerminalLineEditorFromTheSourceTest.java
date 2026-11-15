package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TheTerminalLineEditorFromTheSourceTest {

    private static final class ChunkedInput extends InputStream {

        private final Deque<byte[]> chunks = new ArrayDeque<>();

        private ChunkedInput(String... chunks) {
            Arrays.stream(chunks).map(TheTerminalLineEditorFromTheSourceTest::bytes).forEach(this.chunks::add);
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] into, int offset, int length) {
            if (chunks.isEmpty()) {
                return -1;
            }
            byte[] next = chunks.poll();
            int taken = Math.min(length, next.length);
            System.arraycopy(next, 0, into, offset, taken);
            if (taken < next.length) {
                chunks.push(Arrays.copyOfRange(next, taken, next.length));
            }
            return taken;
        }
    }

    private record Edited(List<String> lines, String shown) {
    }

    private static byte[] bytes(String eachCharacterAByte) {
        return eachCharacterAByte.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static Edited edit(int lineCount, String... chunks) {
        ByteArrayOutputStream shown = new ByteArrayOutputStream();
        TerminalLineEditor editor = new TerminalLineEditor(new ChunkedInput(chunks), shown, () -> {
        });
        List<String> lines = new ArrayList<>();
        for (int read = 0; read < lineCount; read++) {
            lines.add(editor.readLine());
        }
        return new Edited(lines, shown.toString(StandardCharsets.ISO_8859_1));
    }

    private static Edited editOneLine(String typed) {
        return edit(1, typed);
    }

    @Nested
    @DisplayName("typing and ending a line, as Read_Line echoes it")
    class TypingALine {

        @Test
        @DisplayName("each character is echoed, and Enter writes CR LF and answers the line with a LF")
        void echoesAndEndsTheLine() {
            Edited edited = editOneLine("hello\r");

            assertThat(edited.lines()).containsExactly("hello\n");
            assertThat(edited.shown()).isEqualTo("hello\r\n");
        }

        @ParameterizedTest(name = "ended by {0}")
        @CsvSource({"CR, '\r'", "LF, '\n'", "CR LF, '\r\n'"})
        void endsOnCrOrLfOrBoth(String named, String ending) {
            Edited edited = edit(2, "abc" + ending + "def\r");

            assertThat(edited.lines()).containsExactly("abc\n", "def\n");
        }

        @Test
        @DisplayName("an empty line answers a lone LF")
        void answersAnEmptyLine() {
            assertThat(editOneLine("\r").lines()).containsExactly("\n");
        }

        @Test
        @DisplayName("a tab becomes four spaces, echoed, with no redraw")
        void expandsATab() {
            Edited edited = editOneLine("a\tb\r");

            assertThat(edited.lines()).containsExactly("a    b\n");
            assertThat(edited.shown()).isEqualTo("a    b\r\n");
        }

        @Test
        @DisplayName("a control character the editor does not know rings the bell and is dropped")
        void ringsForAnUnknownControl() {
            Edited edited = editOneLine("a\u0010b\u0017c\r");

            assertThat(edited.lines()).containsExactly("abc\n");
            assertThat(edited.shown()).isEqualTo("a\u0007b\u0007c\r\n");
        }

        @Test
        @DisplayName("what is typed after Enter is kept for the next line, and echoed when that line is read")
        void keepsWhatWasTypedAhead() {
            Edited first = edit(1, "one\rtwo\r");
            Edited both = edit(2, "one\rtwo\r");

            assertThat(first.shown()).isEqualTo("one\r\n");
            assertThat(both.lines()).containsExactly("one\n", "two\n");
            assertThat(both.shown()).isEqualTo("one\r\ntwo\r\n");
        }

        @Test
        @DisplayName("a line typed across several reads is one line")
        void gathersALineAcrossReads() {
            assertThat(edit(1, "ab", "c", "d\r").lines()).containsExactly("abcd\n");
        }

        @Test
        @DisplayName("the end of input with nothing pending answers null")
        void answersNullAtTheEnd() {
            assertThat(edit(2, "a\r").lines()).containsExactly("a\n", null);
        }
    }

    @Nested
    @DisplayName("moving and inserting")
    class MovingAndInserting {

        @Test
        @DisplayName("left, then a character inserted, redraws the rest and steps back over it")
        void insertsAfterLeft() {
            Edited edited = editOneLine("ac\u001b[Db\r");

            assertThat(edited.lines()).containsExactly("abc\n");
            assertThat(edited.shown()).isEqualTo("ac\bbc\b\r\n");
        }

        @Test
        @DisplayName("right writes the character it steps over")
        void stepsRight() {
            Edited edited = editOneLine("abc\u001b[D\u001b[D\u001b[CX\r");

            assertThat(edited.lines()).containsExactly("abXc\n");
            assertThat(edited.shown()).isEqualTo("abc\b\bbXc\b\r\n");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "ESC [ H and ESC [ F,  'bc\u001b[Ha\u001b[Fd\r'",
                "ESC [ 1 ~ and ESC [ 4 ~, 'bc\u001b[1~a\u001b[4~d\r'",
                "Ctrl-A and Ctrl-E, 'bc\u0001a\u0005d\r'",
                "ESC H and ESC F, 'bc\u001bHa\u001bFd\r'",
                "ESC O H and ESC O F, 'bc\u001bOHa\u001bOFd\r'",
        })
        void goesHomeAndToTheEnd(String named, String typed) {
            Edited edited = editOneLine(typed);

            assertThat(edited.lines()).containsExactly("abcd\n");
            assertThat(edited.shown()).isEqualTo("bc\b\babc\b\bbcd\r\n");
        }

        @Test
        @DisplayName("Ctrl-B and Ctrl-F move one character, as left and right do")
        void movesWithCtrlBAndCtrlF() {
            Edited edited = editOneLine("ac\u0002b\u0006d\r");

            assertThat(edited.lines()).containsExactly("abcd\n");
            assertThat(edited.shown()).isEqualTo("ac\bbc\bcd\r\n");
        }

        @Test
        @DisplayName("ESC b and ESC f jump a word back and forward")
        void jumpsAWord() {
            Edited edited = editOneLine("ab cd\u001bb\u001bbX\u001bfY\r");

            assertThat(edited.lines()).containsExactly("XabY cd\n");
            assertThat(edited.shown()).isEqualTo("ab cd\b\b\b\b\bXab cd\b\b\b\b\babY cd\b\b\b\r\n");
        }

        @Test
        @DisplayName("left at the start and right at the end do nothing")
        void staysAtTheEdges() {
            Edited edited = editOneLine("\u001b[Da\u001b[C\r");

            assertThat(edited.lines()).containsExactly("a\n");
            assertThat(edited.shown()).isEqualTo("a\r\n");
        }
    }

    @Nested
    @DisplayName("deleting")
    class Deleting {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"DEL, '\u007f'", "Ctrl-H, '\b'"})
        void backsOverACharacter(String named, String key) {
            Edited edited = editOneLine("ab" + key + "c\r");

            assertThat(edited.lines()).containsExactly("ac\n");
            assertThat(edited.shown()).isEqualTo("ab\b \bc\r\n");
        }

        @Test
        @DisplayName("backspace at the start of the line does nothing")
        void backsOverNothingAtTheStart() {
            Edited edited = editOneLine("\u007fab\r");

            assertThat(edited.lines()).containsExactly("ab\n");
            assertThat(edited.shown()).isEqualTo("ab\r\n");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"Ctrl-D, '\u0004'", "the delete key, '\u001b[3~'"})
        void deletesUnderTheCursor(String named, String key) {
            Edited edited = editOneLine("ab\u0001" + key + "\r");

            assertThat(edited.lines()).containsExactly("b\n");
            assertThat(edited.shown()).isEqualTo("ab\b\bb \b\b\r\n");
        }

        @Test
        @DisplayName("Ctrl-D at the end of the line is not the end of input and does nothing")
        void ignoresCtrlDAtTheEnd() {
            Edited edited = editOneLine("ab\u0004\r");

            assertThat(edited.lines()).containsExactly("ab\n");
            assertThat(edited.shown()).isEqualTo("ab\r\n");
        }

        @Test
        @DisplayName("Ctrl-U clears back to the start")
        void clearsToTheStart() {
            Edited edited = editOneLine("abc\u0015x\r");

            assertThat(edited.lines()).containsExactly("x\n");
            assertThat(edited.shown()).isEqualTo("abc\b\b\b   \b\b\bx\r\n");
        }

        @Test
        @DisplayName("Ctrl-U in the middle keeps what is after the cursor")
        void clearsToTheStartFromTheMiddle() {
            Edited edited = editOneLine("abcd\u001b[D\u001b[D\u0015x\r");

            assertThat(edited.lines()).containsExactly("xcd\n");
            assertThat(edited.shown()).isEqualTo("abcd\b\b\b\b    \b\b\b\bcd\b\bxcd\b\b\r\n");
        }

        @Test
        @DisplayName("Ctrl-K clears to the end")
        void clearsToTheEnd() {
            Edited edited = editOneLine("abcd\u001b[D\u001b[D\u000bx\r");

            assertThat(edited.lines()).containsExactly("abx\n");
            assertThat(edited.shown()).isEqualTo("abcd\b\b  \b\bx\r\n");
        }
    }

    @Nested
    @DisplayName("escapes the editor does not take")
    class OtherEscapes {

        @Test
        @DisplayName("a lone ESC at the end of a read writes [ESC] and answers a LF and a NUL")
        void answersALoneEscape() {
            Edited edited = editOneLine("ab\u001b");

            assertThat(edited.lines()).containsExactly("\n\u0000");
            assertThat(edited.shown()).isEqualTo("ab[ESC]\n");
        }

        @Test
        @DisplayName("ESC [ with a letter it does not know writes [ESC] and types the letter")
        void typesTheUnknownLetter() {
            Edited edited = editOneLine("a\u001b[Zb\r");

            assertThat(edited.lines()).containsExactly("aZb\n");
            assertThat(edited.shown()).isEqualTo("a[ESC]Zb\r\n");
        }

        @Test
        @DisplayName("Ctrl-C escapes and drops what came with it in the same read; what was read before stays")
        void escapesOnCtrlC() {
            ByteArrayOutputStream shown = new ByteArrayOutputStream();
            int[] escapes = {0};
            TerminalLineEditor editor = new TerminalLineEditor(
                    new ChunkedInput("ab", "cd\u0003ef", "g\r"), shown, () -> escapes[0]++);

            assertThat(editor.readLine()).isEqualTo("abefg\n");
            assertThat(escapes[0]).isEqualTo(1);
            assertThat(shown.toString(StandardCharsets.ISO_8859_1)).isEqualTo("abefg\r\n");
        }

        @Test
        @DisplayName("ESC with a letter it does not know rings the bell and drops both")
        void ringsForAnUnknownAltKey() {
            Edited edited = editOneLine("a\u001bxb\r");

            assertThat(edited.lines()).containsExactly("ab\n");
            assertThat(edited.shown()).isEqualTo("a\u0007b\r\n");
        }
    }

    @Nested
    @DisplayName("characters past ASCII, as wide as the terminal draws them")
    class WideCharacters {

        @Test
        @DisplayName("a two-byte character is echoed whole and answered as itself")
        void echoesATwoByteCharacter() {
            Edited edited = editOneLine("aÃ©b\r");

            assertThat(edited.lines()).containsExactly("aéb\n");
            assertThat(edited.shown()).isEqualTo("aÃ©b\r\n");
        }

        @Test
        @DisplayName("DEL backs over the whole character, one column")
        void backsOverATwoByteCharacter() {
            Edited edited = editOneLine("aÃ©\u007fb\r");

            assertThat(edited.lines()).containsExactly("ab\n");
            assertThat(edited.shown()).isEqualTo("aÃ©\b \bb\r\n");
        }

        @Test
        @DisplayName("a character two columns wide takes two backspaces to step over")
        void stepsOverAWideCharacter() {
            Edited edited = editOneLine("aä¸­b\u001b[D\u001b[DX\r");

            assertThat(edited.lines()).containsExactly("aX中b\n");
            assertThat(edited.shown()).isEqualTo("aä¸­b\b\b\bXä¸­b\b\b\b\r\n");
        }

        @Test
        @DisplayName("deleting a wide character steps back two columns and blanks one")
        void backsOverAWideCharacter() {
            Edited edited = editOneLine("aä¸­\u007fb\r");

            assertThat(edited.lines()).containsExactly("ab\n");
            assertThat(edited.shown()).isEqualTo("aä¸­\b\b \bb\r\n");
        }
    }

    @Nested
    @DisplayName("the history, recalled with up and down")
    class TheHistory {

        @Test
        @DisplayName("up recalls the lines before, newest first; past the oldest it rings and empties; down comes back")
        void recallsWithUpAndDown() {
            Edited edited = edit(3, "one\r", "two\r", "\u001b[A\u001b[A\u001b[A\u001b[B\r");

            assertThat(edited.lines()).containsExactly("one\n", "two\n", "one\n");
            assertThat(edited.shown()).isEqualTo("one\r\ntwo\r\n"
                    + "two \b\b\b\bone \b\b\b\b\u0007    \b\b\b\bone \b\r\n");
        }

        @Test
        @DisplayName("down past the newest empties the line, blanking what was there")
        void emptiesPastTheNewest() {
            Edited edited = edit(2, "one\r", "abc\u001b[B\u001b[Bx\r");

            assertThat(edited.lines()).containsExactly("one\n", "x\n");
            assertThat(edited.shown()).isEqualTo("one\r\nabc\b\b\b    \b\b\b\b \bx\r\n");
        }

        @Test
        @DisplayName("a shorter line recalled over a longer one blanks the rest")
        void blanksOverALongerLine() {
            Edited edited = edit(2, "a\r", "abcdef\u001b[A\r");

            assertThat(edited.lines()).containsExactly("a\n", "a\n");
            assertThat(edited.shown()).isEqualTo("a\r\nabcdef\b\b\b\b\b\ba      \b\b\b\b\b\b\r\n");
        }

        @Test
        @DisplayName("it keeps the last 299 lines: the 300th line typed pushes out the first")
        void keepsTwoHundredAndNinetyNine() {
            List<String> chunks = new ArrayList<>();
            for (int line = 1; line <= 300; line++) {
                chunks.add(line + "\r");
            }
            chunks.addAll(Collections.nCopies(299, "\u001b[A"));
            chunks.add("\r");
            chunks.addAll(Collections.nCopies(300, "\u001b[A"));
            chunks.add("\r");

            Edited edited = edit(302, chunks.toArray(String[]::new));

            assertThat(edited.lines().get(300)).isEqualTo("2\n");
            assertThat(edited.lines().get(301)).isEqualTo("\n");
        }

        @Test
        @DisplayName("an escape sequence split across two reads is a lone ESC, then the rest typed")
        void splitsASequenceAcrossReads() {
            Edited edited = edit(2, "a\u001b", "[Db\r");

            assertThat(edited.lines()).containsExactly("\n\u0000", "[Db\n");
        }
    }
}
