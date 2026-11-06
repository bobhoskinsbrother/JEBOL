package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheZstdModuleFromTheSourceTest {

    private static final String HELLO = """
            z: import 'zstd
            hello: {hello hello hello hello}
            """;
    private static final String AT_SIX = "#{28B52FFD20176500003068656C6C6F200100994B11}";
    private static final String AT_TWENTY_TWO = "#{28B52FFD20176D00003868656C6C6F20680100418A11}";

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("importing it")
    class Importing {

        @Test
        @DisplayName("answers the module the table then holds, whose version reads Zstandard 1.5.7 as the C packs it")
        void answersTheModule() {
            assertThat(answerTo("""
                    z: import 'zstd
                    reduce [module? z same? z system/modules/zstd z/version]""")).isEqualTo("""
                    [#(true) #(true) 0.2.11]""");
        }

        @Test
        @DisplayName("hides init-words and exports the seven commands")
        void hidesInitWords() {
            assertThat(answerTo("""
                    z: import 'zstd
                    reduce [find words-of z 'init-words find words-of z 'make-encoder]""")).startsWith("[_ [make-encoder");
        }

        @Test
        @DisplayName("adds zstd to the catalog once, however often it is imported")
        void catalogues() {
            assertThat(answerTo("""
                    before: length? system/catalog/compressions
                    import 'zstd
                    import 'zstd
                    reduce [(length? system/catalog/compressions) - before last system/catalog/compressions]""")).isEqualTo("""
                    [1 zstd]""");
        }

        @Test
        @DisplayName("appends ,zstd to the http scheme's Accept-Encoding once")
        void editsAcceptEncoding() {
            assertThat(answerTo("""
                    before: copy system/schemes/http/headers/Accept-Encoding
                    import 'zstd
                    import 'zstd
                    (join before ",zstd") = system/schemes/http/headers/Accept-Encoding""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("COMPRESS and DECOMPRESS, the commands")
    class TheCommands {

        @Test
        @DisplayName("compress at level 6 unless told, as Zstandard's own encoder does")
        void compressAtSix() {
            assertThat(answerTo(HELLO + "z/compress hello")).isEqualTo(AT_SIX);
        }

        @Test
        @DisplayName("clamp a level above 22, and a negative one, which the C reads unsigned, to 22")
        void clampLevels() {
            assertThat(answerTo(HELLO + """
                    reduce [z/compress/level hello 22 z/compress/level hello 99 z/compress/level hello -1]""")).isEqualTo(
                    "[" + AT_TWENTY_TWO + " " + AT_TWENTY_TWO + " " + AT_TWENTY_TWO + "]");
        }

        @Test
        @DisplayName("decompress what they compress, and keep only so many octets when /size is given")
        void roundTrip() {
            assertThat(answerTo(HELLO + """
                    bin: z/compress hello
                    reduce [to string! z/decompress bin to string! z/decompress/size bin 5 z/decompress/part bin 0]""")).isEqualTo("""
                    ["hello hello hello hello" "hello" #{}]""");
        }

        @Test
        @DisplayName("decompress half a megabyte from a few hundred octets, growing their output as R3 grows a series")
        void decompressLarge() {
            assertThat(answerTo("""
                    z: import 'zstd
                    big: append/dup copy "" "abcdefghij" 50000
                    bin: z/compress big
                    reduce [length? bin big = to string! z/decompress bin]""")).endsWith("#(true)]");
        }

        @Test
        @DisplayName("refuse octets that are no Zstandard frame with the extension's own message")
        void refuseGarbage() {
            assertThat(answerTo("""
                    z: import 'zstd
                    e: try [z/decompress #{0102030405060708}]
                    e/arg1""")).contains("Failed to decompress using the Zstd decoder!");
        }
    }

    @Nested
    @DisplayName("COMPRESS and DECOMPRESS, the natives, once it is imported")
    class TheNatives {

        @Test
        @DisplayName("compress at level 22 without /level, the C reading R3's unknown level as the largest")
        void compressWithoutLevel() {
            assertThat(answerTo(HELLO + """
                    reduce [compress hello 'zstd compress/level hello 'zstd 6]""")).isEqualTo(
                    "[" + AT_TWENTY_TWO + " " + AT_SIX + "]");
        }

        @Test
        @DisplayName("decompress through the same shared decoder, and refuse garbage as bad-press")
        void decompressThroughTheNative() {
            assertThat(answerTo(HELLO + """
                    e: try [decompress #{0102030405060708} 'zstd]
                    reduce [to string! decompress compress hello 'zstd 'zstd e/id]""")).isEqualTo("""
                    ["hello hello hello hello" bad-press]""");
        }
    }

    @Nested
    @DisplayName("the streaming handles, as the extension's own CI test drives them")
    class Streaming {

        @Test
        @DisplayName("answer none from a fresh encoder, whether read or finished")
        void freshEncoder() {
            assertThat(answerTo("""
                    z: import 'zstd
                    enc: z/make-encoder
                    reduce [handle? :enc none? z/read :enc none? z/write :enc none]""")).isEqualTo("""
                    [#(true) #(true) #(true)]""");
        }

        @Test
        @DisplayName("read a flushed chunk, then finish the frame, octet for octet as Zstandard writes them")
        void chunks() {
            assertThat(answerTo("""
                    z: import 'zstd
                    enc: z/make-encoder
                    z/write :enc "Hello"
                    z/write :enc " "
                    z/write :enc "Zstandard"
                    bin1: z/read :enc
                    bin2: z/write/finish :enc " from Rebol!"
                    reduce [bin1 bin2 to string! z/decompress join bin1 bin2]""")).isEqualTo("""
                    [#{28B52FFD005878000048656C6C6F205A7374616E64617264} #{6100002066726F6D205265626F6C21} "Hello Zstandard from Rebol!"]""");
        }

        @Test
        @DisplayName("decode chunk by chunk, reading at the end or finishing with the last, and reuse the decoder")
        void decoder() {
            assertThat(answerTo("""
                    z: import 'zstd
                    enc: z/make-encoder
                    z/write :enc "Hello Zstandard"
                    bin1: z/read :enc
                    bin2: z/write/finish :enc " from Rebol!"
                    dec: z/make-decoder
                    z/write :dec bin1
                    z/write :dec bin2
                    one: to string! z/read :dec
                    z/write :dec bin1
                    two: to string! z/write/finish :dec bin2
                    reduce [one two]""")).isEqualTo("""
                    ["Hello Zstandard from Rebol!" "Hello Zstandard from Rebol!"]""");
        }

        @Test
        @DisplayName("compress a file's worth in chunks, flushing each, and decode it back")
        void inChunks() {
            assertThat(answerTo("""
                    z: import 'zstd
                    text: append/dup copy "" "The quick brown fox jumps over the lazy dog. " 4000
                    enc: z/make-encoder/level 6
                    out: copy #{}
                    src: to binary! text
                    while [not tail? src] [
                        chunk: copy/part src 65536
                        src: skip src 65536
                        append out either tail? src [z/write/flush/finish :enc chunk][z/write/flush :enc chunk]
                    ]
                    dec: z/make-decoder
                    z/write :dec out
                    text = to string! z/read :dec""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("refuse a handle of another kind with the extension's message")
        void refuseOtherHandles() {
            assertThat(answerTo("""
                    z: import 'zstd
                    e: try [z/read 42]
                    error? e""")).isEqualTo("#(true)");
        }
    }
}
