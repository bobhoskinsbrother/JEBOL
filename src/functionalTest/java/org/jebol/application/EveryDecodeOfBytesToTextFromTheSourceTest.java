package org.jebol.application;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class EveryDecodeOfBytesToTextFromTheSourceTest {

    private static final String FROM_U_PARSE = "#{E1626320} #{2D61213D7021206F6B}";

    private static final String FROM_U_PARSE_DECODED = "#{EFBFBD626320F0A890A1206F6B}";

    private static final String WITH_NO_PAIR = "#{E162632068656C6C6F206F6B}";

    private static final String TRUE = "#(true)";

    private static final class TypedBytes implements ConsolePort {

        private final Iterator<byte[]> waiting;

        TypedBytes(byte[]... lines) {
            this.waiting = List.of(lines).iterator();
        }

        @Override
        public byte[] readLineBytes() {
            return waiting.hasNext() ? waiting.next() : null;
        }

        @Override
        public String readLine() {
            byte[] line = readLineBytes();
            return line == null ? null : new String(line, StandardCharsets.UTF_8);
        }

        @Override
        public String readHiddenLine() {
            return readLine();
        }
    }

    private static Interpreter makeInterpreterReadingUnder(Path directory) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(directory)),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.FILES));
    }

    private static Interpreter makeInterpreterTypedAt(byte[]... lines) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withConsole(new TypedBytes(lines)),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.CONSOLE));
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerTo(String source) {
        return answerTo(AnInterpreter.create(), source);
    }

    private static void writeFile(Path directory, String name, String digits) throws Exception {
        Files.write(directory.resolve(name), HexFormat.of().parseHex(digits));
    }

    private static byte[] hex(String digits) {
        return HexFormat.of().parseHex(digits);
    }

    @Nested
    @DisplayName("TO STRING! and MAKE STRING! of a binary")
    class ToString {

        @Test
        @DisplayName("a false pair after an invalid byte makes text, the invalid byte replaced")
        void makesTextAfterAFalsePair() {
            assertThat(answerTo("to binary! to string! join " + FROM_U_PARSE))
                    .isEqualTo(FROM_U_PARSE_DECODED);
            assertThat(answerTo("to binary! make string! join " + FROM_U_PARSE))
                    .isEqualTo(FROM_U_PARSE_DECODED);
        }

        @Test
        @DisplayName("a real pair of ED sequences is joined into one character")
        void joinsARealPair() {
            assertThat(answerTo("to binary! to string! #{41EDA080EDB08042}")).isEqualTo("#{41F090808042}");
        }

        @Test
        @DisplayName("with no pair after it, an invalid byte is invalid-utf naming the binary where the good bytes end")
        void refusesWithNoPair() {
            assertThat(answerTo("e: try [to string! " + WITH_NO_PAIR + "] reduce [e/id index? e/arg1]"))
                    .isEqualTo("[invalid-utf 1]");
            assertThat(answerTo("e: try [to string! #{6162FF6364}] reduce [e/id e/arg1 index? e/arg1]"))
                    .isEqualTo("[invalid-utf #{FF6364} 3]");
        }

        @Test
        @DisplayName("on a binary walked into, the position is counted from where the binary stands")
        void countsFromTheBinarysPosition() {
            assertThat(answerTo("e: try [to string! next #{6162FF6364}] reduce [e/arg1 index? e/arg1]"))
                    .isEqualTo("[#{FF6364} 3]");
        }

        @Test
        @DisplayName("after a pair, a failure is placed after the good bytes that followed it")
        void placesAFailureAfterAPair() {
            assertThat(answerTo("e: try [to string! #{FF2D61213D7021616280}] reduce [e/arg1 index? e/arg1]"))
                    .isEqualTo("[#{80} 10]");
        }

        @Test
        @DisplayName("the byte after a pair is not read, so its continuation is read alone")
        void readsTheContinuationAlone() {
            assertThat(answerTo("to binary! to string! #{FF2D61213D7021C37A}"))
                    .isEqualTo("#{EFBFBDF0A890A1EFBFBD7A}");
            assertThat(answerTo("e: try [to string! #{FF2D61213D7021C3A9}] e/id"))
                    .isEqualTo("invalid-utf");
        }
    }

    @Nested
    @DisplayName("READ/STRING and READ/LINES of a file")
    class ReadingAFile {

        @Test
        @DisplayName("bytes the C decodes come back as text")
        void answersText(@TempDir Path directory) throws Exception {
            writeFile(directory, "u.c", "E16263202D61213D7021206F6B");
            Interpreter interpreter = makeInterpreterReadingUnder(directory);
            assertThat(answerTo(interpreter, "to binary! read/string %u.c")).isEqualTo(FROM_U_PARSE_DECODED);
            assertThat(answerTo(interpreter, "to binary! first read/lines %u.c")).isEqualTo(FROM_U_PARSE_DECODED);
        }

        @Test
        @DisplayName("a real pair is joined, as TO STRING! joins it")
        void joinsARealPair(@TempDir Path directory) throws Exception {
            writeFile(directory, "p.txt", "41EDA080EDB08042");
            assertThat(answerTo(makeInterpreterReadingUnder(directory), "to binary! read/string %p.txt"))
                    .isEqualTo("#{41F090808042}");
        }

        @Test
        @DisplayName("bytes it does not decode come back as the bytes")
        void answersBytes(@TempDir Path directory) throws Exception {
            writeFile(directory, "n.c", "E162632068656C6C6F206F6B");
            Interpreter interpreter = makeInterpreterReadingUnder(directory);
            assertThat(answerTo(interpreter, "read/string %n.c")).isEqualTo(WITH_NO_PAIR);
            assertThat(answerTo(interpreter, "read/lines %n.c")).isEqualTo(WITH_NO_PAIR);
        }

        @Test
        @DisplayName("line endings after a pair are folded as anywhere else")
        void foldsLineEndingsAfterAPair(@TempDir Path directory) throws Exception {
            writeFile(directory, "c.txt", "FF2D61213D70210D0A78");
            assertThat(answerTo(makeInterpreterReadingUnder(directory), "to binary! read/string %c.txt"))
                    .isEqualTo("#{EFBFBDF0A890A10A78}");
        }

        @Test
        @DisplayName("an open file port decides alike")
        void decidesAlikeThroughAPort(@TempDir Path directory) throws Exception {
            writeFile(directory, "u.c", "E16263202D61213D7021206F6B");
            writeFile(directory, "n.c", "E162632068656C6C6F206F6B");
            Interpreter interpreter = makeInterpreterReadingUnder(directory);
            assertThat(answerTo(interpreter, "p: open %u.c to binary! read/string p"))
                    .isEqualTo(FROM_U_PARSE_DECODED);
            assertThat(answerTo(interpreter, "q: open %n.c read/string q")).isEqualTo(WITH_NO_PAIR);
        }
    }

    @Nested
    @DisplayName("DECODE 'TEXT")
    class TheTextCodec {

        @Test
        @DisplayName("bytes the C decodes come back as text, CRLF folded")
        void answersText() {
            assertThat(answerTo("to binary! decode 'text join " + FROM_U_PARSE)).isEqualTo(FROM_U_PARSE_DECODED);
            assertThat(answerTo("to binary! decode 'text #{FF2D61213D70210D0A78}"))
                    .isEqualTo("#{EFBFBDF0A890A10A78}");
            assertThat(answerTo("to binary! decode 'text #{41EDA080EDB080}")).isEqualTo("#{41F0908080}");
        }

        @Test
        @DisplayName("bytes it does not decode are bad-media, past good bytes or at the first")
        void refusesAsBadMedia() {
            assertThat(answerTo("e: try [decode 'text #{4180}] e/id")).isEqualTo("bad-media");
            assertThat(answerTo("e: try [decode 'text #{80}] e/id")).isEqualTo("bad-media");
            assertThat(answerTo("e: try [decode 'text " + WITH_NO_PAIR + "] e/id")).isEqualTo("bad-media");
        }
    }

    @Nested
    @DisplayName("the console's READ")
    class TheConsole {

        @Test
        @DisplayName("READ/STRING of bytes the C decodes answers the text")
        void answersText() {
            assertThat(answerTo(makeInterpreterTypedAt(hex("E16263202D61213D7021206F6B")),
                    "to binary! read/string open [scheme: 'console]")).isEqualTo(FROM_U_PARSE_DECODED);
        }

        @Test
        @DisplayName("READ/STRING and READ/LINES of bytes it does not decode answer none")
        void answersNone() {
            Interpreter interpreter = makeInterpreterTypedAt(
                    hex("E162632068656C6C6F206F6B"), hex("6180"));
            assertThat(answerTo(interpreter, "p: open [scheme: 'console] none? read/string p")).isEqualTo(TRUE);
            assertThat(answerTo(interpreter, "none? read/lines p")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a plain READ answers the bytes typed, whether they decode or not")
        void answersTheBytes() {
            assertThat(answerTo(makeInterpreterTypedAt(hex("E162632068656C6C6F206F6B")),
                    "read open [scheme: 'console]")).isEqualTo(WITH_NO_PAIR);
        }
    }
}
