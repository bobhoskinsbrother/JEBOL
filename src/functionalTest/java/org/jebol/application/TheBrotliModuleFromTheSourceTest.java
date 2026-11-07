package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TheBrotliModuleFromTheSourceTest {

    private static final String REFERENCE = "/brotli/module/";

    private static final String WORDS_OF_THE_MODULE = "[words-of b] =>";

    @TempDir
    Path directory;

    private final StringBuilder printed = new StringBuilder();

    private Interpreter makeInterpreter() {
        Bounds bounds = Bounds.standard().withoutTimeLimit();
        for (HostService service : HostService.values()) {
            if (service != HostService.ENVIRONMENT) {
                bounds = bounds.grant(service);
            }
        }
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(directory)),
                printed::append,
                bounds);
    }

    private static String readReference(String name) {
        try (InputStream in = TheBrotliModuleFromTheSourceTest.class.getResourceAsStream(REFERENCE + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private List<String> runReference(String script) throws IOException {
        Files.writeString(directory.resolve(script), readReference(script));
        Interpreter interpreter = makeInterpreter();
        String source = "system/options/quiet: true system/options/args: [] do %" + script;
        interpreter.defineNewWordsIn(source);
        interpreter.run(source);
        return printed.toString().replace("\r\n", "\n").lines().toList();
    }

    private String answerTo(String source) {
        Interpreter interpreter = makeInterpreter();
        String whole = "b: import 'brotli\n" + source;
        interpreter.defineNewWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Test
    @DisplayName("the module and its one-shot commands answer as module-answers.txt records the C answering")
    void answersAsTheC() throws IOException {
        List<String> expected = readReference("module-answers.txt").replace("\r\n", "\n").lines()
                .filter(line -> !line.startsWith(WORDS_OF_THE_MODULE))
                .toList();

        assertThat(runReference("module-answers.r3").stream()
                .filter(line -> !line.startsWith(WORDS_OF_THE_MODULE)))
                .containsExactlyElementsOf(expected);
    }

    @Nested
    @DisplayName("the module")
    class TheModule {

        @Test
        @DisplayName("waits in system/modules until import, and import makes it")
        void waitsForImport() {
            assertThat(answerTo("reduce [module? b same? b system/modules/brotli]")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("carries the header its builder writes, named brotli")
        void carriesItsHeader() {
            assertThat(answerTo("""
                    s: spec-of b
                    reduce [s/title s/name s/version s/needs]""")).isEqualTo("""
                    ["Rebol Brotli Extension" brotli 0.1.0 3.20.5]""");
        }

        @Test
        @DisplayName("hides init-words and holds the one-shot commands")
        void holdsTheCommands() {
            assertThat(answerTo("reduce [none? find words-of b 'init-words copy skip words-of b 4]"))
                    .isEqualTo("[#(true) [version compress decompress]]");
        }

        @Test
        @DisplayName("answers Brotli's version as a tuple")
        void answersTheVersion() {
            assertThat(answerTo("reduce [b/version tuple? b/version]")).isEqualTo("[1.2.0 #(true)]");
        }

        @Test
        @DisplayName("appends a second br to Accept-Encoding, as its import check misses the one already there")
        void appendsASecondBr() {
            Interpreter interpreter = makeInterpreter();
            String source = """
                    before: copy system/schemes/http/headers/Accept-Encoding
                    import 'brotli
                    import 'brotli
                    reduce [before system/schemes/http/headers/Accept-Encoding]""";
            interpreter.defineNewWordsIn(source);

            assertThat(interpreter.display(interpreter.run(source))).isEqualTo("""
                    ["gzip,deflate,br" "gzip,deflate,br,br"]""");
        }
    }

    @Nested
    @DisplayName("compress")
    class Compress {

        @ParameterizedTest(name = "/level {0} writes what level {1} writes")
        @CsvSource({"-1, 6", "-2, 11", "12, 11", "99, 11", "4294967295, 6", "4294967294, 11"})
        @DisplayName("passes its level through an unsigned 32 bits, so -1 is no level, 6, and any other negative 11")
        void clampsTheLevel(long asked, int meant) {
            assertThat(answerTo("equal? b/compress/level {hello hello} " + asked + " b/compress/level {hello hello} "
                    + meant)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("without /level writes level 6")
        void defaultsToSix() {
            assertThat(answerTo("equal? b/compress {hello hello} b/compress/level {hello hello} 6"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("writes the core's bytes, so compress 'br is unchanged by importing")
        void writesTheCoresBytes() {
            Interpreter interpreter = makeInterpreter();
            String source = """
                    t: copy #{} repeat i 30000 [append t remainder i * 31 + (i // 7) 256]
                    before: collect [foreach level [0 1 5 9 11] [keep compress/level t 'br level]]
                    b: import 'brotli
                    after: collect [foreach level [0 1 5 9 11] [keep compress/level t 'br level]]
                    own: collect [foreach level [0 1 5 9 11] [keep b/compress/level t level]]
                    reduce [equal? before after equal? before own]""";
            interpreter.defineNewWordsIn(source);

            assertThat(interpreter.display(interpreter.run(source))).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("reads a string as the UTF-8 R3 stores, from its index, and /part counts within it")
        void readsStrings() {
            assertThat(answerTo("""
                    reduce [
                        b/compress {ěšč}
                        b/compress next {Xhello}
                        b/compress/part {abcdefghij} 5
                        b/compress/part {abcdefghij} -3
                    ]""")).isEqualTo("[#{8B0280C49BC5A1C48D03} #{0B028068656C6C6F03} #{0B0280616263646503} #{3B}]");
        }
    }

    @Nested
    @DisplayName("decompress")
    class Decompress {

        @Test
        @DisplayName("answers #{} without decoding for empty input, /size 0, /part 0 and a negative /part")
        void shortCuts() {
            assertThat(answerTo("""
                    c: b/compress {hello hello}
                    reduce [b/decompress #{} b/decompress/size c 0 b/decompress/part c 0 b/decompress/part c -1]"""))
                    .isEqualTo("[#{} #{} #{} #{}]");
        }

        @Test
        @DisplayName("cuts the output at /size exactly, and a negative /size is no limit")
        void stopsAtTheLimit() {
            assertThat(answerTo("""
                    c: b/compress {hello hello hello hello}
                    reduce [to string! b/decompress/size c 5 length? b/decompress/size c -1 length? b/decompress/size c 1000]"""))
                    .isEqualTo("""
                            ["hello" 23 23]""");
        }

        @Test
        @DisplayName("ignores bytes after the end of a stream")
        void ignoresTrailingBytes() {
            assertThat(answerTo("{hello} = to string! b/decompress join b/compress {hello} #{0102}"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("fails with the C's message for a stream cut short, damaged or not a stream at all")
        void failsAsTheCSays() {
            assertThat(answerTo("""
                    c: b/compress {hello hello hello hello}
                    unique collect [foreach code [
                        [b/decompress #{00}]
                        [b/decompress copy/part c (length? c) - 1]
                        [b/decompress #{FFFFFFFF}]
                        [b/decompress/part c 5]
                    ] [e: try code keep/only reduce [e/id e/arg1]]]""")).isEqualTo("""
                    [[command-fail "Failed to decompress using the Brotli decoder!"]]""");
        }
    }
}
