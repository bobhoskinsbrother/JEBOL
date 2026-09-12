package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumOfAFileFromTheSourceTest {

    private static final String THE_SUBJECT =
            "The quick brown fox jumps over the lazy dog";

    private static Interpreter reaching(Path directory) throws IOException {
        Files.writeString(directory.resolve("subject.txt"), THE_SUBJECT);
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(Interpreter interpreter, String source) {
        return answerTo(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("a file is hashed by its contents and not by its name")
    class TheSixteenDigests {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "md4,       1BEE69A46BA811185C194762ABAEAE90",
                "md5,       9E107D9D372BB6826BD81D3542A419D6",
                "ripemd160, 37F332F68DB77BD9D7EDD4969571AD671CF9DD3B",
                "sha1,      2FD4E1C67A2D28FCED849EE1BB76E7391B93EB12",
                "sha224,    730E109BD7A8A32B1CB9D9A09AA2325D2430587DDBC0C38BAD911525",
                "sha256,    D7A8FBB307D7809469CA9ABCB0082E4F8D5651E46D3CDB762D02D0BF37C9E592",
                "sha384,    CA737F1014A48F4C0B6DD43CB177B0AFD9E5169367544C494011E3317DBF9A509"
                        + "CB1E5DC1E85A941BBEE3D7F2AFBC9B1",
                "sha512,    07E547D9586F6A73F73FBAC0435ED76951218FB7D0C8D788A309D785436BBB642"
                        + "E93A252A954F23912547D1E8A3B5ED6E1BFD7097821233FA0538F3DB854FEE6",
                "sha3-224,  D15DADCEAA4D5D7BB3B48F446421D542E08AD8887305E28D58335795",
                "sha3-256,  69070DDA01975C8C120C3AADA1B282394E7F032FA9CF32F4CB2259A0897DFC04",
                "sha3-384,  7063465E08A93BCE31CD89D2E3CA8F602498696E253592ED26F07BF7E703CF328"
                        + "581E1471A7BA7AB119B1A9EBDF8BE41",
                "sha3-512,  01DEDD5DE4EF14642445BA5F5B97C15E47B9AD931326E4B0727CD94CEFC44FFF2"
                        + "3F07BF543139939B49128CAF436DC1BDEE54FCB24023A08D9403F9B4BF0D450",
                "xxh3,      CE7D19A5418FB365",
                "xxh32,     E85EA4DE",
                "xxh64,     0B242D361FDA71BC",
                "xxh128,    DDD650205CA3E7FA24A1CC2E3A8A7651",
        })
        @DisplayName("every digest method reads the file")
        void everyDigestMethodReadsTheFile(String method, String expected,
                @TempDir Path directory) throws IOException {

            assertThat(answerTo(reaching(directory),
                    "(enbase/flat (checksum %subject.txt '" + method + ") 16)"
                            + " = {" + expected + "}"))
                    .isEqualTo("#(true)");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"md5", "sha256", "xxh3", "xxh128"})
        @DisplayName("and answers exactly what FILE-CHECKSUM answers")
        void itAnswersWhatFileChecksumAnswers(String method, @TempDir Path directory)
                throws IOException {

            assertThat(answerTo(reaching(directory),
                    "(checksum %subject.txt '" + method + ")"
                            + " = (file-checksum %subject.txt '" + method + ")"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and not the digest of the path, which is what hashing the name gives")
        void itIsNotTheDigestOfThePath(@TempDir Path directory) throws IOException {
            Interpreter interpreter = reaching(directory);

            assertThat(answerTo(interpreter,
                    "(checksum %subject.txt 'md5) = (checksum \"subject.txt\" 'md5)"))
                    .isEqualTo("#(false)");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"xxh32", "xxh64", "xxh3", "xxh128"})
        @DisplayName("a port and a one-shot answer the same digest, however long the input")
        void aPortAndAOneShotAgree(String method, @TempDir Path directory)
                throws IOException {

            assertThat(answerTo(reaching(directory), """
                    bytes: read %subject.txt
                    p: open checksum://METHOD
                    write p bytes
                    same: (read p) = (checksum bytes 'METHOD)
                    close p
                    same""".replace("METHOD", method)))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a method that is not a digest is refused")
    class TheFiveThatDoNotDispatch {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"adler32", "crc24", "crc32", "tcp", "hash"})
        @DisplayName("because FILE-CHECKSUM takes a digest and nothing else")
        void aMethodThatIsNotADigestIsRefused(String method, @TempDir Path directory)
                throws IOException {

            assertThat(errorIdFrom(reaching(directory),
                    "checksum %subject.txt '" + method))
                    .isEqualTo("feature-na");
        }
    }

    @Nested
    @DisplayName("neither /PART nor /WITH survives a file")
    class TheRefinements {

        @Test
        @DisplayName("both are refused, because FILE-CHECKSUM takes neither")
        void bothAreRefused(@TempDir Path directory) throws IOException {
            Interpreter interpreter = reaching(directory);

            assertThat(errorIdFrom(interpreter, "checksum/part %subject.txt 'md5 1"))
                    .isEqualTo("bad-refines");
            assertThat(errorIdFrom(interpreter, "checksum/with %subject.txt 'md5 1"))
                    .isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("and the method is judged first, so a non-digest answers feature-na")
        void theMethodIsJudgedBeforeTheRefinements(@TempDir Path directory)
                throws IOException {

            Interpreter interpreter = reaching(directory);

            assertThat(errorIdFrom(interpreter, "checksum/part %subject.txt 'adler32 1"))
                    .isEqualTo("feature-na");
            assertThat(errorIdFrom(interpreter, "checksum/with %subject.txt 'tcp 1"))
                    .isEqualTo("feature-na");
        }
    }

    @Nested
    @DisplayName("what the dispatch does with a file it cannot read")
    class WhenTheFileIsNotThere {

        @Test
        @DisplayName("a file that does not exist cannot be opened")
        void aFileThatIsNotThereCannotBeOpened(@TempDir Path directory)
                throws IOException {

            assertThat(errorIdFrom(reaching(directory), "checksum %nothing.txt 'md5"))
                    .isEqualTo("cannot-open");
        }

        @Test
        @DisplayName("and without the filesystem grant the service is refused")
        void withoutTheGrantTheServiceIsRefused() {
            Interpreter interpreter = Interpreter.create();

            assertThat(errorIdFrom(interpreter, "checksum %subject.txt 'md5"))
                    .isEqualTo("no-service");
        }
    }

    @Nested
    @DisplayName("what CHECKSUM will take as data at all")
    class TheDeclaredTypes {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"5", "1.5", "[1 2]", "#(none)", "'word", "10:00"})
        @DisplayName("anything that is not a binary, a string or a file is refused")
        void anythingElseIsRefused(String data, @TempDir Path directory)
                throws IOException {

            assertThat(errorIdFrom(reaching(directory), "checksum " + data + " 'md5"))
                    .isEqualTo("expect-arg");
        }
    }
}
