package org.jebol.adapter.cli;

import org.jebol.domain.host.ConsoleKey;
import org.jebol.domain.host.ConsoleMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TheCommandLinesConsoleFromTheSourceTest {

    private static final String AS_FOUND = "as-found";
    private static final String EDITORS_MODE = "editors-mode";

    private static final class FakeStty implements TerminalSettings {

        private final List<String> run = new ArrayList<>();
        private final Deque<String> answers = new ArrayDeque<>(List.of(AS_FOUND, EDITORS_MODE));

        @Override
        public String read() {
            run.add("read");
            return answers.isEmpty() ? "" : answers.poll();
        }

        @Override
        public void change(List<String> settings) {
            run.add("change " + String.join(" ", settings));
        }

        @Override
        public void restore(String saved) {
            run.add("restore " + saved);
        }

        @Override
        public int eraseCharacter() {
            return 0x7F;
        }
    }

    private static final class Typed extends InputStream {

        private final Deque<byte[]> reads = new ArrayDeque<>();
        private byte[] arriving = new byte[0];
        private int readSoFar;

        private Typed(String... reads) {
            Arrays.stream(reads).map(each -> each.getBytes(StandardCharsets.ISO_8859_1)).forEach(this.reads::add);
        }

        @Override
        public int read() {
            if (readSoFar == arriving.length) {
                if (reads.isEmpty()) {
                    return -1;
                }
                arriving = reads.poll();
                readSoFar = 0;
            }
            return arriving[readSoFar++] & 0xFF;
        }

        @Override
        public int read(byte[] into, int offset, int length) {
            if (readSoFar == arriving.length) {
                if (reads.isEmpty()) {
                    return -1;
                }
                arriving = reads.poll();
                readSoFar = 0;
            }
            int taken = Math.min(length, arriving.length - readSoFar);
            System.arraycopy(arriving, readSoFar, into, offset, taken);
            readSoFar += taken;
            return taken;
        }

        @Override
        public int available() {
            return arriving.length - readSoFar + (reads.isEmpty() ? 0 : reads.peek().length);
        }
    }

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();
    private final ByteArrayOutputStream complained = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(printed, true, StandardCharsets.UTF_8);
    private final PrintStream errors = new PrintStream(complained, true, StandardCharsets.UTF_8);
    private final StreamOutput output = new StreamOutput(out, errors);
    private final FakeStty stty = new FakeStty();
    private final AtomicInteger escapes = new AtomicInteger();

    private CommandLineConsole atTerminal(InputStream typed) {
        return CommandLineConsole.atTerminal(typed, output, out, stty);
    }

    private CommandLineConsole atTerminal(String... typed) {
        return atTerminal(new Typed(typed));
    }

    private CommandLineConsole throughPipes(String... typed) {
        return CommandLineConsole.throughPipes(new Typed(typed), output, out);
    }

    private String printed() {
        return printed.toString(StandardCharsets.ISO_8859_1);
    }

    private String complained() {
        return complained.toString(StandardCharsets.ISO_8859_1);
    }

    @Nested
    @DisplayName("at a terminal, the settings R3's posix host makes")
    class TheTerminalsSettings {

        @Test
        @DisplayName("opening keeps the terminal as found, puts it in the editor's mode, and keeps that too")
        void putsTheTerminalInTheEditorsMode() {
            atTerminal();

            assertThat(stty.run).containsExactly(
                    "read",
                    "change -echo -icanon -icrnl -inlcr onlcr min 1 time 0",
                    "read");
            assertThat(printed()).isEmpty();
        }

        @Test
        @DisplayName("closing puts the terminal back as found and writes nothing")
        void restoresTheTerminalOnClose() {
            CommandLineConsole console = atTerminal();
            console.switchMode(ConsoleMode.LINE, false);
            printed.reset();

            console.close();

            assertThat(stty.run.getLast()).isEqualTo("restore " + AS_FOUND);
            assertThat(printed()).isEmpty();
        }

        @Test
        @DisplayName("closing twice restores once")
        void restoresOnce() {
            CommandLineConsole console = atTerminal();

            console.close();
            console.close();

            assertThat(stty.run).filteredOn(each -> each.startsWith("restore")).hasSize(1);
        }

        @Test
        @DisplayName("line false goes raw and turns bracketed paste on; true comes back to the editor's mode and turns it off")
        void switchesLineMode() {
            CommandLineConsole console = atTerminal();
            stty.run.clear();

            console.switchMode(ConsoleMode.LINE, false);
            console.switchMode(ConsoleMode.LINE, false);
            console.switchMode(ConsoleMode.LINE, true);
            console.switchMode(ConsoleMode.LINE, true);

            assertThat(stty.run).containsExactly(
                    "change -ixon -ixoff susp undef quit undef",
                    "restore " + EDITORS_MODE);
            assertThat(printed()).isEqualTo("\u001b[?2004h\u001b[?2004l");
        }

        @Test
        @DisplayName("line true when it already is changes nothing")
        void leavesLineModeAlone() {
            CommandLineConsole console = atTerminal();
            stty.run.clear();

            console.switchMode(ConsoleMode.LINE, true);

            assertThat(stty.run).isEmpty();
            assertThat(printed()).isEmpty();
        }

        @Test
        @DisplayName("the console is a terminal")
        void isATerminal() {
            assertThat(atTerminal().isATerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("echo and error, at a terminal and through pipes alike")
    class EchoAndError {

        @Test
        @DisplayName("echo writes the conceal escape and takes it off, every time it is set")
        void writesTheConcealEscapes() {
            CommandLineConsole console = throughPipes();

            console.switchMode(ConsoleMode.ECHO, false);
            console.switchMode(ConsoleMode.ECHO, false);
            console.switchMode(ConsoleMode.ECHO, true);

            assertThat(printed()).isEqualTo("\u001b[8m\u001b[8m\u001b[28m");
        }

        @Test
        @DisplayName("error sends what is printed to the error stream, and back")
        void movesOutputToTheErrorStream() {
            CommandLineConsole console = throughPipes();

            output.write("a");
            console.switchMode(ConsoleMode.ERROR, true);
            output.write("b");
            console.switchMode(ConsoleMode.ERROR, false);
            output.write("c");

            assertThat(printed()).isEqualTo("ac");
            assertThat(complained()).isEqualTo("b");
        }

        @Test
        @DisplayName("the escapes follow the output to the error stream")
        void writesEscapesWhereOutputGoes() {
            CommandLineConsole console = atTerminal();

            console.switchMode(ConsoleMode.ERROR, true);
            console.switchMode(ConsoleMode.ECHO, false);
            console.switchMode(ConsoleMode.LINE, false);

            assertThat(printed()).isEmpty();
            assertThat(complained()).isEqualTo("\u001b[8m\u001b[?2004h");
        }

        @Test
        @DisplayName("line writes its escapes through pipes too, and runs no stty")
        void writesLineEscapesThroughPipes() {
            CommandLineConsole console = throughPipes();

            console.switchMode(ConsoleMode.LINE, false);
            console.switchMode(ConsoleMode.LINE, true);

            assertThat(printed()).isEqualTo("\u001b[?2004h\u001b[?2004l");
            assertThat(stty.run).isEmpty();
        }

        @Test
        @DisplayName("on Windows the three modes are accepted and do nothing")
        void doesNothingOnWindows() {
            CommandLineConsole console = CommandLineConsole.onWindows(new Typed(), output, true);

            console.switchMode(ConsoleMode.ECHO, false);
            console.switchMode(ConsoleMode.LINE, false);
            console.switchMode(ConsoleMode.ERROR, true);
            output.write("x");

            assertThat(printed()).isEqualTo("x");
            assertThat(complained()).isEmpty();
            assertThat(console.isATerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("a line through pipes is what one read gives")
    class ALineThroughPipes {

        @Test
        @DisplayName("a final LF is taken off and a CR before it kept")
        void takesOffOnlyTheLineFeed() {
            CommandLineConsole console = throughPipes("hello\n", "abc\r\n");

            assertThat(console.readLine()).isEqualTo("hello");
            assertThat(console.readLine()).isEqualTo("abc\r");
        }

        @Test
        @DisplayName("two lines in one read come back together")
        void answersTwoLinesInOneRead() {
            assertThat(throughPipes("a\nb\n").readLine()).isEqualTo("a\nb");
        }

        @Test
        @DisplayName("a part line at the end of input, then nothing")
        void answersThePartLineThenNull() {
            CommandLineConsole console = throughPipes("abc");

            assertThat(console.readLine()).isEqualTo("abc");
            assertThat(console.readLine()).isNull();
        }

        @Test
        @DisplayName("with line false the LF is kept")
        void keepsTheLineFeedWithLineFalse() {
            CommandLineConsole console = throughPipes("x\n");
            console.switchMode(ConsoleMode.LINE, false);

            assertThat(console.readLine()).isEqualTo("x\n");
        }

        @Test
        @DisplayName("the prompt reads a line at a time from the same bytes, and a line left over is the next READ")
        void sharesTheBytesWithThePrompt() {
            CommandLineConsole console = throughPipes("one\ntwo\nthree\n");

            assertThat(console.nextPromptLine()).isEqualTo("one");
            assertThat(console.readLine()).isEqualTo("two");
            assertThat(console.nextPromptLine()).isEqualTo("three");
            assertThat(console.nextPromptLine()).isNull();
        }

        @Test
        @DisplayName("the prompt takes a CR before the LF off, and a last line with no LF")
        void readsPromptLines() {
            CommandLineConsole console = throughPipes("a\r\nb");

            assertThat(console.nextPromptLine()).isEqualTo("a");
            assertThat(console.nextPromptLine()).isEqualTo("b");
            assertThat(console.nextPromptLine()).isNull();
        }

        @Test
        @DisplayName("the console is not a terminal")
        void isNotATerminal() {
            assertThat(throughPipes().isATerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("a line at a terminal goes through the editor")
    class ALineAtATerminal {

        @Test
        @DisplayName("the editor echoes what is typed and the line comes back without its LF")
        void readsThroughTheEditor() {
            CommandLineConsole console = atTerminal("hi\r");

            assertThat(console.readLine()).isEqualTo("hi");
            assertThat(printed()).isEqualTo("hi\r\n");
        }

        @Test
        @DisplayName("a lone ESC answers a LF and a NUL, as the port strips only a final LF")
        void answersALoneEscape() {
            assertThat(atTerminal("ab\u001b").readLine()).isEqualTo("\n\u0000");
        }

        @Test
        @DisplayName("the prompt reads through the same editor, so its history is shared")
        void sharesTheEditorWithThePrompt() {
            CommandLineConsole console = atTerminal("one\r", "\u001b[A\r");

            assertThat(console.nextPromptLine()).isEqualTo("one");
            assertThat(console.readLine()).isEqualTo("one");
        }

        @Test
        @DisplayName("the end of input answers null")
        void answersNullAtTheEnd() {
            assertThat(atTerminal().readLine()).isNull();
        }

        @Test
        @DisplayName("Ctrl-C is a byte while the line is read, and the terminal makes signals again after")
        void turnsSignalsOffWhileReading() {
            CommandLineConsole console = atTerminal("hi\r");
            stty.run.clear();

            console.readLine();

            assertThat(stty.run).containsExactly("change -isig", "change isig");
        }

        @Test
        @DisplayName("Ctrl-C in a line raises the escape and drops what came with it unread, as the terminal's flush does")
        void escapesFromALine() {
            CommandLineConsole console = atTerminal("ab\u0003", "cd\r");
            console.whenEscaped(escapes::incrementAndGet);

            assertThat(console.readLine()).isEqualTo("cd");
            assertThat(escapes).hasValue(1);
            assertThat(printed()).isEqualTo("cd\r\n");
        }
    }

    @Nested
    @DisplayName("a hidden line is read a byte at a time to a CR, as OS_Request_Password reads it")
    class AHiddenLine {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "ends at a CR,                            'secret\rafter', secret",
                "keeps a LF,                              'sec\nret\r',    'sec\nret'",
                "keeps a NUL,                             'a\u0000b\r',    'a\u0000b'",
                "backs over a whole character with DEL,   'aÃ©\u007fb\r', ab",
                "backs over nothing at the start,         '\u007fab\r',    ab",
                "reads UTF-8,                             'aÃ©b\r', aéb",
        })
        void readsToACr(String named, String typed, String expected) {
            assertThat(throughPipes(typed).readHiddenLine()).isEqualTo(expected);
        }

        @Test
        @DisplayName("the end of input ends it, and with nothing typed answers an empty string")
        void endsAtTheEndOfInput() {
            assertThat(throughPipes("abc").readHiddenLine()).isEqualTo("abc");
            assertThat(throughPipes().readHiddenLine()).isEmpty();
        }

        @Test
        @DisplayName("ESC answers null, which the script sees as none")
        void answersNullForEscape() {
            assertThat(throughPipes("ab\u001bcd\r").readHiddenLine()).isNull();
        }

        @Test
        @DisplayName("at a terminal Ctrl-C raises the escape and is not part of it; through pipes it is just a byte")
        void escapesFromAPasswordAtATerminal() {
            CommandLineConsole terminal = atTerminal("ab\u0003cd\r");
            terminal.whenEscaped(escapes::incrementAndGet);

            assertThat(terminal.readHiddenLine()).isEqualTo("abcd");
            assertThat(escapes).hasValue(1);
            assertThat(throughPipes("a\u0003b\r").readHiddenLine()).isEqualTo("a\u0003b");
        }

        @Test
        @DisplayName("at a terminal nothing typed is shown, and the bytes after the CR are left for the next read")
        void showsNothing() {
            CommandLineConsole console = atTerminal("secret\rnext\r");

            assertThat(console.readHiddenLine()).isEqualTo("secret");
            assertThat(printed()).isEmpty();
            assertThat(console.readLine()).isEqualTo("next");
        }
    }

    @Nested
    @DisplayName("a key")
    class AKey {

        @Test
        @DisplayName("at a terminal it leaves line mode first, then decodes the key with Ctrl-C turned into a byte")
        void decodesAtATerminal() {
            CommandLineConsole console = atTerminal("\u001b[A");
            stty.run.clear();

            ConsoleKey key = console.readKey().orElseThrow();

            assertThat(key).isEqualTo(new ConsoleKey(0, 6, false, false, false));
            assertThat(stty.run).containsExactly(
                    "change -ixon -ixoff susp undef quit undef", "change -isig", "change isig");
            assertThat(printed()).isEqualTo("\u001b[?2004h");
        }

        @Test
        @DisplayName("at a terminal, Ctrl-C while it waits is the key Ctrl-C with control, and no escape")
        void answersCtrlCWhileWaiting() {
            CommandLineConsole console = atTerminal("\u0003");
            console.whenEscaped(escapes::incrementAndGet);

            assertThat(console.readKey()).contains(new ConsoleKey(3, 0, true, false, false));
            assertThat(escapes).hasValue(0);
        }

        @Test
        @DisplayName("at a terminal, an escape pending while it waits, from Ctrl-C come as a signal, is the key Ctrl-C")
        void answersCtrlCForAPendingEscape() {
            CommandLineConsole console = atTerminal(new Typed());
            console.askWhetherEscaped(() -> true);

            assertThat(console.readKey()).contains(new ConsoleKey(3, 0, true, false, false));
        }

        @Test
        @DisplayName("a terminal that has gone, whose input fails, gives no key")
        void answersNothingWhenTheTerminalHasGone() {
            InputStream gone = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Input/output error");
                }

                @Override
                public int available() throws IOException {
                    throw new IOException("Input/output error");
                }
            };

            assertThat(atTerminal(gone).readKey()).isEmpty();
        }

        @Test
        @DisplayName("a key read to the end of input at a terminal gives no key")
        void answersNothingAtTheEnd() {
            assertThat(atTerminal("ä").readKey()).isEmpty();
        }

        @Test
        @DisplayName("through pipes it reads nothing, writes the line escape, and answers the line mode's number")
        void readsNothingThroughPipes() {
            CommandLineConsole console = throughPipes("abc\n");

            assertThat(console.readKey()).contains(new ConsoleKey(1, 0, false, false, false));
            assertThat(console.readKey()).contains(new ConsoleKey(1, 0, false, false, false));
            assertThat(printed()).isEqualTo("\u001b[?2004h");
            assertThat(console.readLine()).isEqualTo("abc\n");
        }

        @ParameterizedTest(name = "after {0} {1}")
        @CsvSource({
                "ECHO,  true,  0, 1",
                "ECHO,  false, 0, 0",
                "ERROR, true,  2, 1",
                "ERROR, false, 2, 0",
                "LINE,  false, 1, 0",
        })
        void answersTheLastModeSet(ConsoleMode mode, boolean on, int character, int virtualKey) {
            CommandLineConsole console = throughPipes();
            console.switchMode(ConsoleMode.LINE, false);
            console.switchMode(mode, on);

            assertThat(console.readKey()).contains(new ConsoleKey(character, virtualKey, false, false, false));
        }

        @Test
        @DisplayName("through pipes at the end of input it still answers")
        void answersAtTheEndThroughPipes() {
            assertThat(throughPipes().readKey()).contains(new ConsoleKey(1, 0, false, false, false));
        }
    }

    @Test
    @DisplayName("bytes read through pipes are not read twice: a hidden line, then a line, share one stream")
    void sharesOneStream() {
        CommandLineConsole console = CommandLineConsole.throughPipes(
                new ByteArrayInputStream("pw\rrest\n".getBytes(StandardCharsets.UTF_8)), output, out);

        assertThat(console.readHiddenLine()).isEqualTo("pw");
        assertThat(console.readLine()).isEqualTo("rest");
    }
}
