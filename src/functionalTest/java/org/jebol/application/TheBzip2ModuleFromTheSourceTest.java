package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TheBzip2ModuleFromTheSourceTest {

    private static final String IMPORTED = """
            b: import 'bzip2
            hello: {hello hello hello hello}
            c: #{425A683631415926535902F8B0BD000003910040000244A00030CD00548696719B38A3C5DC914E142400BE2C2F40}
            arg-of: func [code] [attempt [get in try code 'arg1]]
            """;
    private static final String NOISE = """
            noise: func [n /local out x] [
                out: make binary! n  x: 12345
                loop n [
                    x: (x * 1103515245 + 12345) and 2147483647
                    append out (to integer! x / 65536) and 255
                ]
                out
            ]
            all-noise: noise 300000
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        String whole = IMPORTED + source;
        interpreter.defineNewWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Nested
    @DisplayName("importing it")
    class Importing {

        @Test
        @DisplayName("answers the module the table then holds, whose version is the library's own string")
        void answersTheModule() {
            assertThat(answerTo("""
                    reduce [module? b same? b system/modules/bzip2 b/version]""")).isEqualTo("""
                    [#(true) #(true) "1.1.0"]""");
        }

        @Test
        @DisplayName("hides init-words and exports the seven commands")
        void hidesInitWords() {
            assertThat(answerTo("""
                    reduce [
                        find words-of b 'init-words
                        copy skip tail words-of b -7
                    ]""")).isEqualTo("""
                    [_ [version compress decompress make-encoder make-decoder write read]]""");
        }

        @Test
        @DisplayName("carries the header its source declares")
        void carriesItsHeader() {
            assertThat(answerTo("""
                    s: spec-of b
                    reduce [s/version s/title s/type s/author s/needs s/license s/url]""")).isEqualTo("""
                    [1.1.0 "Rebol Bzip2 Extension" module Oldes 3.20.5 MIT https://github.com/Oldes/Rebol-Bzip2]""");
        }

        @Test
        @DisplayName("adds bzip2 to the catalog once, however often it is imported")
        void catalogues() {
            assertThat(answerTo("""
                    before: length? system/catalog/compressions
                    import 'bzip2
                    reduce [(length? system/catalog/compressions) - before last system/catalog/compressions]""")).isEqualTo("""
                    [0 bzip2]""");
        }

        @Test
        @DisplayName("leaves the http scheme's Accept-Encoding alone, because this extension declares no edit")
        void leavesAcceptEncodingAlone() {
            assertThat(answerTo("""
                    none? find system/schemes/http/headers/Accept-Encoding {bzip2}""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("the block size each route writes")
    class BlockSizes {

        @ParameterizedTest(name = "compress/level {0} writes {1}")
        @CsvSource({"0, BZh9", "1, BZh1", "6, BZh6", "9, BZh9", "10, BZh9", "-1, BZh1", "99, BZh9"})
        @DisplayName("the compress command reads level 0 as 9 and clamps the rest")
        void theCommandClamps(int level, String head) {
            assertThat(answerTo("to string! copy/part b/compress/level {hello} " + level + " 4"))
                    .isEqualTo('"' + head + '"');
        }

        @ParameterizedTest(name = "make-encoder/level {0} writes {1}")
        @CsvSource({"0, BZh1", "1, BZh1", "9, BZh9", "10, BZh9", "-1, BZh1"})
        @DisplayName("make-encoder has no such rule, so its level 0 is 1")
        void theEncoderClamps(int level, String head) {
            assertThat(answerTo("to string! copy/part b/write/finish b/make-encoder/level " + level + " {hello} 4"))
                    .isEqualTo('"' + head + '"');
        }

        @ParameterizedTest(name = "compress/level {0} with the method writes {1}")
        @CsvSource({"0, BZh9", "1, BZh1", "6, BZh6", "9, BZh9", "10, BZh9"})
        @DisplayName("the native goes through the command's rule")
        void theNativeClamps(int level, String head) {
            assertThat(answerTo("to string! copy/part compress/level {hello} 'bzip2 " + level + " 4"))
                    .isEqualTo('"' + head + '"');
        }

        @Test
        @DisplayName("without a level the commands write 6, and the native writes 1, its unknown level read as -1")
        void theDefaultsDiffer() {
            assertThat(answerTo("""
                    reduce [
                        to string! copy/part b/compress {hello} 4
                        to string! copy/part b/write/finish b/make-encoder {hello} 4
                        to string! copy/part compress {hello} 'bzip2 4
                    ]""")).isEqualTo("""
                    ["BZh6" "BZh6" "BZh1"]""");
        }
    }

    @Nested
    @DisplayName("COMPRESS and DECOMPRESS, the commands")
    class TheCommands {

        @Test
        @DisplayName("compress to the octets the C writes")
        void compressAsTheC() {
            assertThat(answerTo("c = b/compress hello")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("read a string as the UTF-8 R3 stores it, and /part by its clamped length")
        void readStringsAndParts() {
            assertThat(answerTo("""
                    reduce [
                        #{425A683617724538509000000000} = b/compress ""
                        #{425A6836314159265359170A0F700000020074000200082000060020002183419A0C831C5DC914E142405C283DC0} = b/compress "ěšč"
                        #{425A683617724538509000000000} = b/compress/part "abcdefghij" -3
                        #{425A6836314159265359A35B4DF400000001003E0020002183419A025C7177245385090A35B4DF40} = b/compress/part "abcdefghij" 5
                    ]""")).isEqualTo("""
                    [#(true) #(true) #(true) #(true)]""");
        }

        @Test
        @DisplayName("count a string's index in characters but its /part in octets, so /part can split a character")
        void readStringIndexesAndPartsAsTheCDoes() {
            assertThat(answerTo("""
                    round-trip: func [text] [b/decompress b/compress text]
                    reduce [
                        round-trip "é"
                        round-trip "aé"
                        round-trip skip "ěšč" 1
                        round-trip skip "ěšč" 2
                        round-trip skip "éa" 1
                        round-trip append make string! 10 to char! 283
                        round-trip %ě
                        round-trip <ě>
                        round-trip http://a/ě
                        b/decompress b/compress/part "ěšč" 1
                        b/decompress b/compress/part "ěšč" 100
                    ]""")).isEqualTo("""
                    [#{C3A9} #{61C3A9} #{C5A1C48D} #{C48D} #{61} #{C49B} #{C49B} #{C49B} #{687474703A2F2F612FC49B} #{C4} #{C49BC5A1C48D}]""");
        }

        @Test
        @DisplayName("decompress the whole, and ignore what follows the stream's end")
        void decompressWhole() {
            assertThat(answerTo("""
                    reduce [to string! b/decompress c to string! b/decompress join c #{0102}]""")).isEqualTo("""
                    ["hello hello hello hello" "hello hello hello hello"]""");
        }

        @Test
        @DisplayName("keep only /size octets, and /max caps what /size asks for")
        void decompressSized() {
            assertThat(answerTo("""
                    reduce [
                        to string! b/decompress/size c 5
                        b/decompress/size c 0
                        to string! b/decompress/size c 1000
                        to string! b/decompress/max c 0
                        to string! b/decompress/size/max c 12 5
                    ]""")).isEqualTo("""
                    ["hello" #{} "hello hello hello hello" "hello hello hello hello" "hello"]""");
        }

        @Test
        @DisplayName("answer no octets for no octets, which never reaches the decoder")
        void decompressNothing() {
            assertThat(answerTo("""
                    reduce [b/decompress #{} decompress #{} 'bzip2]""")).isEqualTo("""
                    [#{} #{}]""");
        }

        @Test
        @DisplayName("refuse a /max too small to hold the output, a negative /size or /max, and a stream that is damaged or cut")
        void refuseWithTheirOwnMessages() {
            assertThat(answerTo("""
                    reduce [
                        arg-of [b/decompress/max c 5]
                        arg-of [b/decompress/size c -1]
                        arg-of [b/decompress/max c -1]
                        arg-of [b/decompress #{425A683181E8FFFFFF}]
                        arg-of [b/decompress/part c 5]
                    ]""")).isEqualTo("""
                    ["Bzip2 decompression failed." "Invalid output size limit (/size)." "Invalid /max (must be >= 0)." "Bzip2 decompression failed." "Bzip2 decompression failed."]""");
        }
    }

    @Nested
    @DisplayName("COMPRESS and DECOMPRESS, the natives, once it is imported")
    class TheNatives {

        @Test
        @DisplayName("round-trip, keep /size octets, and answer the same errors R3's native raises")
        void roundTrip() {
            assertThat(answerTo("""
                    e1: try [decompress #{425A683181E8FFFFFF} 'bzip2]
                    e2: try [decompress/size c 'bzip2 -1]
                    reduce [
                        to string! decompress c 'bzip2
                        to string! decompress/size c 'bzip2 5
                        e1/id e1/arg1
                        e2/id e2/arg1
                    ]""")).isEqualTo("""
                    ["hello hello hello hello" "hello" bad-press -4 out-of-range -1]""");
        }

        @Test
        @DisplayName("read a string as UTF-8 too, but count their /part in characters where the command counts octets")
        void readStringsAsTheCDoes() {
            assertThat(answerTo("""
                    round-trip: func [text] [decompress compress text 'bzip2 'bzip2]
                    reduce [
                        round-trip "ěšč"
                        round-trip "é"
                        round-trip skip "ěšč" 1
                        decompress compress/part "ěšč" 'bzip2 1 'bzip2
                        decompress compress/part "ěšč" 'bzip2 2 'bzip2
                    ]""")).isEqualTo("""
                    [#{C49BC5A1C48D} #{C3A9} #{C5A1C48D} #{C49B} #{C49BC5A1}]""");
        }
    }

    @Nested
    @DisplayName("a stream's handle")
    class Streams {

        @Test
        @DisplayName("is a handle whose mold names its type and side, and an address")
        void moldsItsType() {
            assertThat(answerTo("""
                    reduce [
                        handle? b/make-encoder
                        mold b/make-encoder
                        mold b/make-decoder
                    ]""")).matches("""
                    \\[#\\(true\\) "#\\(handle! bzip2-encoder bzip2:enc:0x[0-9a-f]+\\)" "#\\(handle! bzip2-decoder bzip2:dec:0x[0-9a-f]+\\)"]""");
        }

        @Test
        @DisplayName("an encoder given none before any data answers none and stays open")
        void answersNoneBeforeData() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    reduce [b/write/finish :e none b/read :e same? :e b/write :e "x"]""")).isEqualTo("""
                    [_ _ #(true)]""");
        }

        @Test
        @DisplayName("read flushes a block, the next read has nothing, and finish completes one stream across them")
        void readsAndFinishes() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    b/write :e "x"
                    first-read: b/read :e
                    second-read: b/read :e
                    finished: b/write/finish :e "y"
                    reduce [
                        first-read = #{425A6836314159265359774BB014000000008000402000211846}
                        second-read
                        finished = #{86282B24CA6B2E7155B4600000001000040400042308D05DC914E142427477362C}
                        b/read :e
                    ]""")).isEqualTo("""
                    [#(true) #{} #(true) #{}]""");
        }

        @Test
        @DisplayName("writes read strings as UTF-8 from their index too")
        void writesReadStringsAsStored() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    b/write :e "ě"
                    b/write :e skip "ěš" 1
                    b/decompress b/write/finish :e {é}""")).isEqualTo("#{C49BC5A1C3A9}");
        }

        @Test
        @DisplayName("an empty write then a read gives the header's first three octets alone, and a finish the rest")
        void emptyWriteGivesTheHeader() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    same-handle: same? :e b/write :e #{}
                    reduce [same-handle b/read :e b/write/finish :e #{}]""")).isEqualTo("""
                    [#(true) #{425A68} #{3617724538509000000000}]""");
        }

        @Test
        @DisplayName("once finished, refuses every write, none included, but still reads")
        void refusesAfterFinishing() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    b/write/finish :e "y"
                    reduce [arg-of [b/write :e "z"] arg-of [b/write :e none] b/read :e]""")).isEqualTo("""
                    ["Bzip2 stream is already finished." "Bzip2 stream is already finished." #{}]""");
        }

        @Test
        @DisplayName("write/flush, where the C never returns, raises the compress error")
        void flushRaisesWhereTheCHangs() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    reduce [arg-of [b/write/flush :e "hello"]]""")).isEqualTo("""
                    ["Bzip2 compression failed."]""");
        }

        @Test
        @DisplayName("a decoder ignores none, decodes a stream, and is finished at its end")
        void decodes() {
            assertThat(answerTo("""
                    d: b/make-decoder
                    reduce [
                        b/read :d
                        b/write :d none
                        same? :d b/write :d c
                        to string! b/read :d
                        b/read :d
                        arg-of [b/write :d c]
                        arg-of [b/write :d none]
                    ]""")).isEqualTo("""
                    [_ _ #(true) "hello hello hello hello" #{} "Bzip2 stream is already finished." "Bzip2 stream is already finished."]""");
        }

        @Test
        @DisplayName("a decoder's write/flush and write/finish hand back what has been decoded, split input or not")
        void decoderFlushAndFinish() {
            assertThat(answerTo("""
                    d: b/make-decoder
                    reduce [b/write/flush :d copy/part c 10 to string! b/write/finish :d skip c 10]""")).isEqualTo("""
                    [#{} "hello hello hello hello"]""");
        }

        @Test
        @DisplayName("refuses what is no handle")
        void refusesOtherTypes() {
            assertThat(answerTo("""
                    e: try [b/read 1]
                    e/id""")).isEqualTo("expect-arg");
        }

        @ParameterizedTest(name = "{0} writes of 3000 octets read back as {1}, {2}, then {3}, and finish with {4}")
        @CsvSource({
                "1, 3447, 0, 12, C34C61883667A6BCE702E9A844A0E767EB51858D",
                "10, 30484, 0, 12, 0564A95672AD5BD1CFE09A424247C5949A3B35FC",
                "30, 66560, 24090, 12, A17F9546EFD16FCDC9087CE6AF3F6222705BB377",
                "33, 66560, 33100, 13, 656EA0D6D6E385BB5CC2E1ED20E01907F9F3BAED",
                "34, 103087, 0, 12, FBE78C719A1EA1C89BA1BFF87B84F4DA5716C73C",
                "40, 121136, 0, 13, B3699ED2C6A814015FDCF6C971B801202AEC6919",
                "100, 302029, 0, 12, 6D5A8AD0A7E79DB4875BE75518CED901B2CD4C6F"
        })
        @DisplayName("a block too big for the room left splits across reads exactly where the series' room ends")
        void splitsReadsWhereTheRoomEnds(int chunks, int firstRead, int secondRead, int finish, String sha1) {
            assertThat(answerTo(NOISE + """
                    e: b/make-encoder/level 1
                    repeat at %d [b/write :e copy/part skip all-noise at - 1 * 3000 3000]
                    got: copy []
                    whole: copy #{}
                    loop 3 [r: b/read :e  append got length? r  append whole r]
                    r: b/write/finish :e #{}
                    append got length? r
                    append whole r
                    reduce [checksum all-noise 'sha1 got checksum whole 'sha1]""".formatted(chunks))).isEqualTo(
                    "[#{5419735E4A16950E0DA7863E554D124A721F0583} [" + firstRead + " " + secondRead + " 0 " + finish
                            + "] #{" + sha1 + "}]");
        }

        @Test
        @DisplayName("a decoder given half a block then the rest has nothing to read until the block is whole")
        void decodesAcrossWrites() {
            assertThat(answerTo(NOISE + """
                    d: b/make-decoder
                    z: b/compress all-noise
                    half: to integer! (length? z) / 2
                    got: copy []
                    b/write :d copy/part z half
                    append got length? b/read :d
                    b/write :d skip z half
                    append got length? b/read :d
                    append got length? b/read :d
                    got""")).isEqualTo("[0 300000 0]");
        }
    }
}
