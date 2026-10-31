package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheZlibNgModuleFromTheSourceTest {

    private static final String HELLO = """
            z: import 'zlib-ng
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
        @DisplayName("answers the module, which the table then holds, with its commands and its four strategies")
        void answersTheModule() {
            assertThat(answerTo("""
                    z: import 'zlib-ng
                    reduce [module? z same? z system/modules/zlib-ng z/version z/Z_FILTERED z/Z_FIXED]""")).isEqualTo("""
                    [#(true) #(true) 2.3.2 1 4]""");
        }

        @Test
        @DisplayName("hides init-words, as the module's own body does")
        void hidesInitWords() {
            assertThat(answerTo("""
                    z: import 'zlib-ng
                    reduce [find words-of z 'init-words find words-of z 'make-encoder]""")).startsWith("[_ [make-encoder");
        }

        @Test
        @DisplayName("adds deflate-ng, zlib-ng and gzip-ng to the catalog once, and leaves the names it replaces")
        void catalogues() {
            assertThat(answerTo("""
                    before: length? system/catalog/compressions
                    import 'zlib-ng
                    import 'zlib-ng
                    reduce [(length? system/catalog/compressions) - before
                        copy skip tail system/catalog/compressions -3]""")).isEqualTo("""
                    [3 [deflate-ng zlib-ng gzip-ng]]""");
        }

        @Test
        @DisplayName("leaves the http scheme's Accept-Encoding alone, its edit being commented out")
        void leavesAcceptEncoding() {
            assertThat(answerTo("""
                    before: copy system/schemes/http/headers/Accept-Encoding
                    import 'zlib-ng
                    before = system/schemes/http/headers/Accept-Encoding""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("COMPRESS and DECOMPRESS, once it is imported")
    class TheNatives {

        @Test
        @DisplayName("compress zlib and gzip as zlib-ng does, not as libdeflate does")
        void compressAsZlibNg() {
            assertThat(answerTo(HELLO + """
                    reduce [compress/level hello 'zlib 6 compress/level hello 'gzip 6]""")).isEqualTo("""
                    [#{789CCB48CDC9C957C8402701680308B1} #{1F8B0800000000000003CB48CDC9C957C8402701E3513D8D17000000}]""");
        }

        @Test
        @DisplayName("answer the same bytes under the new names")
        void answerUnderTheNewNames() {
            assertThat(answerTo(HELLO + """
                    reduce [compress/level hello 'zlib-ng 6 compress/level hello 'deflate-ng 9]""")).isEqualTo("""
                    [#{789CCB48CDC9C957C8402701680308B1} #{CB48CDC9C957C8402701}]""");
        }

        @Test
        @DisplayName("go back to libdeflate when deflate is imported after it")
        void goBackToLibdeflate() {
            assertThat(answerTo(HELLO + """
                    ours: compress/level hello 'zlib 6
                    import 'deflate
                    reduce [ours = compress/level hello 'zlib 6 compress/level hello 'zlib-ng 6]""")).isEqualTo("""
                    [#(false) #{789CCB48CDC9C957C8402701680308B1}]""");
        }

        @Test
        @DisplayName("refuse data that does not read with zlib-ng's own error number")
        void refuseWithZlibNgsNumber() {
            assertThat(answerTo(HELLO + """
                    packed: compress/level hello 'zlib 6
                    damaged: copy packed
                    change back tail damaged #{B0}
                    cut: try [decompress copy/part packed (length? packed) - 3 'zlib]
                    bad: try [decompress damaged 'zlib]
                    reduce [cut/id cut/arg1 bad/id bad/arg1]""")).isEqualTo("""
                    [bad-press -5 bad-press -3]""");
        }

        @Test
        @DisplayName("cut a decompression short to the size asked rather than refusing it")
        void cutShort() {
            assertThat(answerTo(HELLO + """
                    decompress/size compress hello 'gzip 'gzip 5""")).isEqualTo("#{68656C6C6F}");
        }
    }

    @Nested
    @DisplayName("its own commands")
    class TheCommands {

        @Test
        @DisplayName("store at level 0 when no level is asked, as cmd_compress passes nought")
        void storeWithoutLevel() {
            assertThat(answerTo(HELLO + """
                    z/compress hello""")).isEqualTo("#{011700E8FF68656C6C6F2068656C6C6F2068656C6C6F2068656C6C6F}");
        }

        @Test
        @DisplayName("take -1 as the default level and any other negative as nine")
        void readNegativeLevelsUnsigned() {
            assertThat(answerTo(HELLO + """
                    reduce [z/compress/level/zlib hello -1 z/compress/level/zlib hello -5]""")).isEqualTo("""
                    [#{789CCB48CDC9C957C8402701680308B1} #{78DACB48CDC9C957C8402701680308B1}]""");
        }

        @Test
        @DisplayName("decode the whole stream when asked for a size of nought")
        void decodeAllForNought() {
            assertThat(answerTo(HELLO + """
                    reduce [to string! z/decompress/size z/compress/level hello 6 0]""")).isEqualTo("""
                    ["hello hello hello hello"]""");
        }

        @Test
        @DisplayName("fail as a command, with its own message, on data that does not read")
        void failAsACommand() {
            assertThat(answerTo(HELLO + """
                    e: try [z/decompress #{FF}]
                    reduce [e/id e/arg1]""")).isEqualTo("""
                    [command-fail "Failed to decompress using the Zlib decoder!"]""");
        }
    }

    @Nested
    @DisplayName("streaming")
    class Streaming {

        @Test
        @DisplayName("writes what zlib-ng writes for a stream flushed and then finished")
        void writesWhatZlibNgWrites() {
            assertThat(answerTo(HELLO + """
                    enc: z/make-encoder
                    kept: z/write enc {the first part of the stream, }
                    flushed: z/write/flush enc {the second part}
                    finished: z/write enc none
                    reduce [same? kept enc
                        flushed = #{2AC9485548CB2C2A2E5128482C2A51C84F530089149714A526E6EA40D8A9C9F97929606900000000FFFF}
                        finished]""")).isEqualTo("""
                    [#(true) #(true) #{0300}]""");
        }

        @Test
        @DisplayName("refuses as no-memory where output fills the room exactly and the C's guess at growth underflows")
        void refusesTheUnderflowedGrowth() {
            assertThat(answerTo(HELLO + """
                    enc: z/make-encoder
                    z/write enc {the first part, }
                    e: try [z/write/flush enc {the second part}]
                    e/id""")).isEqualTo("no-memory");
        }

        @Test
        @DisplayName("compresses at level 1 whatever level the encoder is made with")
        void compressesAtLevelOne() {
            assertThat(answerTo(HELLO + """
                    one: z/write/finish z/make-encoder/level 1 hello
                    nine: z/write/finish z/make-encoder/level 9 hello
                    one = nine""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("decodes a stream given in pieces when flushed, past the room its buffer started with")
        void decodesInPieces() {
            assertThat(answerTo(HELLO + """
                    packed: z/compress/level hello 6
                    dec: z/make-decoder
                    z/write dec copy/part packed 4
                    reduce [to string! z/write/flush dec skip packed 4]""")).isEqualTo("""
                    ["hello hello hello hello"]""");
        }

        @Test
        @DisplayName("fails to finish a decoder whose output outgrows its buffer, as zlib-ng's Z_FINISH answers Z_BUF_ERROR")
        void failsToFinishPastTheBuffer() {
            assertThat(answerTo(HELLO + """
                    packed: z/compress/level hello 6
                    dec: z/make-decoder
                    z/write dec copy/part packed 4
                    e: try [z/write/finish dec skip packed 4]
                    reduce [e/id e/arg1]""")).isEqualTo("""
                    [command-fail "Failed to compress using the Zlib encoder!"]""");
        }

        @Test
        @DisplayName("refuses a second write that brings nothing new, zlib-ng's Z_BUF_ERROR")
        void refusesAnEmptySecondWrite() {
            assertThat(answerTo(HELLO + """
                    enc: z/make-encoder
                    z/write enc #{}
                    e: try [z/write enc #{}]
                    e/arg1 = {Failed to compress using the Zlib encoder!}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("refuses another extension's handle with its own message")
        void refusesAnotherHandle() {
            assertThat(answerTo(HELLO + """
                    e: try [z/read rc4/key #{00}]
                    reduce [e/id e/arg1]""")).isEqualTo("""
                    [command-fail "Invalid Zlib encoder or decoder handle!"]""");
        }
    }
}
