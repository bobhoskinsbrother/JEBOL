package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheDeflateModuleFromTheSourceTest {

    private static final String HELLO = """
            d: import 'deflate
            hello: {hello hello hello hello}
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("importing it")
    class Importing {

        @Test
        @DisplayName("answers the module, which the table then holds, naming libdeflate's version")
        void answersTheModule() {
            assertThat(answerTo("""
                    d: import 'deflate
                    reduce [module? d same? d system/modules/deflate d/version find words-of d 'init-words]""")).isEqualTo("""
                    [#(true) #(true) 1.25.0 _]""");
        }

        @Test
        @DisplayName("adds no name to the catalog, every one it registers being there already")
        void addsNoName() {
            assertThat(answerTo("""
                    before: copy system/catalog/compressions
                    import 'deflate
                    before = system/catalog/compressions""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("leaves Accept-Encoding as it was, since it names deflate already")
        void leavesAcceptEncoding() {
            assertThat(answerTo("""
                    before: copy system/schemes/http/headers/Accept-Encoding
                    import 'deflate
                    reduce [before = system/schemes/http/headers/Accept-Encoding
                        to logic! find before "deflate"]""")).isEqualTo("""
                    [#(true) #(true)]""");
        }
    }

    @Nested
    @DisplayName("its own commands")
    class TheCommands {

        @Test
        @DisplayName("store at level 0 when no level is asked, as cmd_compress passes nought")
        void storeWithoutLevel() {
            assertThat(answerTo(HELLO + """
                    d/compress hello""")).isEqualTo("#{011700E8FF68656C6C6F2068656C6C6F2068656C6C6F2068656C6C6F}");
        }

        @Test
        @DisplayName("compress at a level as the built-in method does, the gzip wrapper winning over zlib")
        void compressAsTheBuiltIn() {
            assertThat(answerTo(HELLO + """
                    reduce [(d/compress/level/zlib hello 6) = compress/level hello 'zlib 6
                        (d/compress/level/zlib/gzip hello 9) = compress/level hello 'gzip 9
                        (d/compress/level hello -3) = compress/level hello 'deflate 12]""")).isEqualTo("""
                    [#(true) #(true) #(true)]""");
        }

        @Test
        @DisplayName("decompress only to the exact size asked, and answer nothing at all for a size of nought")
        void decompressToTheExactSize() {
            assertThat(answerTo(HELLO + """
                    packed: d/compress/level hello 6
                    e: try [d/decompress/size packed 5]
                    reduce [to string! d/decompress/size packed 23 d/decompress/size packed 0 e/id e/arg1]""")).isEqualTo("""
                    ["hello hello hello hello" #{} command-fail "Failed to decompress using the Deflate decoder!"]""");
        }

        @Test
        @DisplayName("read only the part asked of its input")
        void readThePart() {
            assertThat(answerTo(HELLO + """
                    reduce [to string! d/decompress d/compress/part/level hello 5 6]""")).isEqualTo("""
                    ["hello"]""");
        }
    }
}
