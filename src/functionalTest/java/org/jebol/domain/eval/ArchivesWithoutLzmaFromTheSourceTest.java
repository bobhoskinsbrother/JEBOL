package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.CarriedArchives;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchivesWithoutLzmaFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String withArchive(String name, String source) {
        return "bin: " + CarriedArchives.asBinary(name) + "\n" + source;
    }

    @Test
    @DisplayName("the fixtures are the files their names promise")
    void fixturesAreWhatTheySay() {
        assertThat(CarriedArchives.bytes("test-deflate.zip")).hasSize(358);
        assertThat(CarriedArchives.bytes("test-stored.zip")).hasSize(419);
        assertThat(answerTo("checksum " + CarriedArchives.asBinary("test-lzma.zip") + " 'crc32"))
                .isEqualTo("2205292669");
        assertThat(CarriedArchives.bytes("test-lzma.zip")).hasSize(623);
        assertThat(CarriedArchives.bytes("test1-deflate.swf")).hasSize(262).startsWith('C', 'W', 'S');
        assertThat(CarriedArchives.bytes("test2-lzma.swf")).hasSize(252).startsWith('Z', 'W', 'S');
    }

    @Test
    @DisplayName("the core carries no LZMA, so these tests run where R3's Core product would")
    void coreCarriesNoLzma() {
        assertThat(answerTo("to logic! find system/catalog/compressions 'lzma")).isEqualTo("#(false)");
    }

    @Nested
    class Zip {

        @Test
        @DisplayName("a deflated entry decodes, to its full 333 bytes")
        void decodesDeflated() {
            assertThat(answerTo(withArchive("test-deflate.zip", """
                    data: decode 'zip bin
                    new-line/all reduce [data/1 length? data/2/2] false""")))
                    .isEqualTo("[%host-table.c 333]");
        }

        @Test
        @DisplayName("stored entries decode, a directory and an empty file among them")
        void decodesStored() {
            assertThat(answerTo(withArchive("test-stored.zip", """
                    data: decode 'zip bin
                    new-line/all reduce [new-line/all extract data 2 false  "2" = to string! data/6/2] false""")))
                    .isEqualTo("[[%0.txt %directory/ %directory/1.txt] #(true)]");
        }

        @Test
        @DisplayName("an LZMA entry is an invalid argument naming lzma, as R3's Core product answers")
        void refusesLzmaEntry() {
            assertThat(answerTo(withArchive("test-lzma.zip", """
                    raised: try [decode 'zip bin]
                    reduce [raised/id raised/arg1]""")))
                    .isEqualTo("[invalid-arg lzma]");
        }

        @Test
        @DisplayName("the info of an archive with an LZMA entry still reads, because it decompresses nothing")
        void readsInfoOfLzmaArchive() {
            assertThat(answerTo(withArchive("test-lzma.zip", """
                    info: codecs/zip/decode/info bin
                    new-line/all reduce [info/1 info/2/1] false""")))
                    .isEqualTo("[%xJSFL.komodoproject 18-Aug-2012/5:20:28]");
        }

        @Test
        @DisplayName("writing an archive needs no LZMA: what encode writes, decode reads back")
        void encodesWithoutLzma() {
            assertThat(answerTo("""
                    data: decode 'zip encode 'zip [
                        %a.txt [1-Jan-2021 "Hello, Hello, Hello, Hello!"]
                        %b.txt [1-Jan-2021 "kept" store]
                    ]
                    new-line/all reduce [
                        data/1 data/3
                        "Hello, Hello, Hello, Hello!" = to string! data/2/2
                        "kept" = to string! data/4/2
                    ] false"""))
                    .isEqualTo("[%a.txt %b.txt #(true) #(true)]");
        }
    }

    @Nested
    class Swf {

        private static final String QUIETLY = """
                import 'swf
                codecs/swf/verbose: 0
                """;

        @Test
        @DisplayName("a zlib-compressed movie decodes, all 25 frames")
        void decodesCompressedWithZlib() {
            assertThat(answerTo(withArchive("test1-deflate.swf", QUIETLY + """
                    movie: decode 'swf bin
                    movie/header/frames""")))
                    .isEqualTo("25");
        }

        @Test
        @DisplayName("an LZMA-compressed movie is an invalid argument naming lzma, as R3's Core product answers")
        void refusesCompressedWithLzma() {
            assertThat(answerTo(withArchive("test2-lzma.swf", QUIETLY + """
                    raised: try [decode 'swf bin]
                    reduce [raised/id raised/arg1]""")))
                    .isEqualTo("[invalid-arg lzma]");
        }
    }
}
