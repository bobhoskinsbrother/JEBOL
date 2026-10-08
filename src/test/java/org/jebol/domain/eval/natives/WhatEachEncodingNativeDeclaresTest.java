package org.jebol.domain.eval.natives;

import org.jebol.application.Interpreter;
import org.jebol.domain.eval.Encodings;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachEncodingNativeDeclaresTest {

    private String answerTo(String source) {
        return Molder.moldFlat(Interpreter.create().run(source).value());
    }

    private String errorIdOf(String source) {
        return answerTo("select try [" + source + "] 'id");
    }

    private String firstArgumentOf(String source) {
        return answerTo("select try [" + source + "] 'arg1");
    }

    Stream<Arguments> eachNameAndItsRefinements() {
        Encodings encodings = new Encodings();
        return Stream.of(
                Arguments.of(new EnhexNative(encodings), "enhex", Set.of("escape", "except", "uri")),
                Arguments.of(new DehexNative(encodings), "dehex", Set.of("escape", "uri")),
                Arguments.of(new EnbaseNative(encodings), "enbase", Set.of("url", "part", "flat")),
                Arguments.of(new DebaseNative(encodings), "debase", Set.of("url", "part")),
                Arguments.of(new ChecksumNative(encodings), "checksum", Set.of("with", "part")),
                Arguments.of(new CompressNative(encodings), "compress", Set.of("part", "level")),
                Arguments.of(new DecompressNative(encodings), "decompress", Set.of("part", "size")),
                Arguments.of(new EncloakNative(encodings), "encloak", Set.of("with")),
                Arguments.of(new DecloakNative(encodings), "decloak", Set.of("with")),
                Arguments.of(new IconvNative(encodings), "iconv", Set.of("to")),
                Arguments.of(new FilterNative(encodings), "filter", Set.of("skip")),
                Arguments.of(new UnfilterNative(encodings), "unfilter", Set.of("as", "skip")),
                Arguments.of(new SwapEndianNative(encodings), "swap-endian", Set.of("width", "part")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("eachNameAndItsRefinements")
    @DisplayName("each answers to its Rebol name with the refinements Rebol declares")
    void declaresItsNameAndRefinements(DefaultNative definition, String name,
                                       Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    @Nested
    @DisplayName("enhex and dehex")
    class PercentEncoding {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                enhex "a b"                                 | "a%20b"
                enhex "a b/c"                               | "a%20b%2Fc"
                enhex %"a b"                                | %a%2520b
                enhex #{2061}                               | #{25323061}
                enhex/uri "a b"                             | "a+b"
                enhex/escape "a b" #"="                     | "a=20b"
                enhex/escape/uri "a b" #"="                 | "a_b"
                enhex/except "abc" charset "a"              | "a%62%63"
                enhex/escape/except "abc" #"=" charset "a"  | "a=62=63"
                enhex/except "a b" charset " "              | "%61 %62"
                enhex "ř"                                   | "%C5%99"
                enhex <a b>                                 | <a%20b>
                enhex ""                                    | ""
                dehex "a%20b"                               | "a b"
                dehex "a+b"                                 | "a+b"
                dehex/uri "a+b"                             | "a b"
                dehex/escape "a=20b" #"="                   | "a b"
                dehex/escape/uri "a_b" #"="                 | "a b"
                dehex/escape "a%20b" #"="                   | "a%20b"
                dehex #{612532306200}                       | #{61206200}
                dehex "%c5%99"                              | "ř"
                dehex "%zz"                                 | "%zz"
                dehex "%2"                                  | "%2"
                dehex %a%20b                                | %a%20b
                dehex ""                                    | ""
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                enhex #"a"                  | expect-arg | enhex
                enhex/escape "a b" "="      | expect-arg | enhex
                """)
        void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }

    @Nested
    @DisplayName("enbase and debase")
    class BinaryBases {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                enbase "abc" 64                             | "YWJj"
                enbase "abc" 16                             | "616263"
                enbase "abc" 2                              | "011000010110001001100011"
                enbase "abc" 36                             | "3SSIR"
                enbase "abc" 85                             | "@:E^^"
                enbase 255 16                               | "FF"
                enbase 0 16                                 | "00"
                enbase -1 16                                | "FFFFFFFFFFFFFFFF"
                enbase 256 2                                | "0000000100000000"
                enbase/part "abcdef" 64 3                   | "YWJj"
                enbase/part "abcdef" 64 0                   | ""
                enbase/part "abcdef" 64 -1                  | ""
                enbase/part "abcdef" 64 100                 | "YWJjZGVm"
                enbase/part skip "abcdef" 3 64 -2           | "YmM="
                enbase/part 65535 16 1                      | "FF"
                enbase/url #{FBFF} 64                       | "-_8"
                enbase #{FBFF} 64                           | "+/8="
                enbase/flat #{} 64                          | ""
                enbase "" 64                                | ""
                enbase "ř" 16                               | "C599"
                debase "YWJj" 64                            | #{616263}
                debase "616263" 16                          | #{616263}
                debase #{363136323633} 16                   | #{616263}
                debase/url "-_8" 64                         | #{FBFF}
                debase "6" 16                               | #{06}
                debase "" 64                                | #{}
                debase/part "616263" 16 4                   | #{6162}
                debase/part "616263" 16 0                   | #{}
                debase "01100001" 2                         | #{61}
                debase "0110000" 2                          | #{30}
                debase "2j" 36                              | #{000000000000005B}
                debase "@:F" 85                             | #{6162}
                debase "z" 85                               | #{00000000}
                debase "YW Jj" 64                           | #{616263}
                debase "YWJj^/" 64                          | #{616263}
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                enbase/flat head insert/dup copy #{} #{00} 60 64                          | {AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA}
                replace/all enbase head insert/dup copy #{} #{00} 60 64 newline "/"       | {/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/AAAAAAAAAAAAAAAA}
                replace/all enbase head insert/dup copy #{} #{00} 48 64 newline "/"       | {AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/}
                replace/all enbase head insert/dup copy #{} #{00} 49 64 newline "/"       | {AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/AA==}
                replace/all enbase head insert/dup copy #{} #{00} 32 16 newline "/"       | {/0000000000000000000000000000000000000000000000000000000000000000/}
                replace/all enbase head insert/dup copy #{} #{00} 33 16 newline "/"       | {/0000000000000000000000000000000000000000000000000000000000000000/00}
                """)
        void breaksLongEncodingsIntoLinesWhereRebolDoes(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                enbase "abc" 3              | invalid-arg  | 3
                enbase "abc" 0              | invalid-arg  | 0
                enbase "abc" 64.0           | expect-arg   | enbase
                debase "YWJj" 3             | invalid-data | "YWJj"
                debase "+_8" 64             | invalid-data | "+_8"
                debase "!!!" 64             | invalid-data | "!!!"
                debase "YWJ" 64             | invalid-data | "YWJ"
                debase "61 62" 16           | invalid-data | "61 62"
                debase "6G" 16              | invalid-data | "6G"
                debase "YWJj" 64.0          | expect-arg   | debase
                """)
        void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }

    @Nested
    @DisplayName("checksum, and the checksum port")
    class Checksums {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                checksum "abc" 'md5                         | #{900150983CD24FB0D6963F7D28E17F72}
                checksum "abc" 'sha1                        | #{A9993E364706816ABA3E25717850C26C9CD0D89D}
                checksum "abc" 'sha224                      | #{23097D223405D8228642A477BDA255B32AADBCE4BDA0B3F7E36C9DA7}
                checksum "abc" 'sha256                      | #{BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD}
                checksum "abc" 'sha384                      | #{CB00753F45A35E8BB5A03D699AC65007272C32AB0EDED1631A8B605A43FF5BED8086072BA1E7CC2358BAECA134C825A7}
                checksum "abc" 'sha512                      | #{DDAF35A193617ABACC417349AE20413112E6FA4E89A97EA20A9EEEE64B55D39A2192992A274FC1A836BA3C23A3FEEBBD454D4423643CE80E2A9AC94FA54CA49F}
                checksum "abc" 'ripemd160                   | #{8EB208F7E05D987A9B044A8E98C6B087F15A0BFC}
                checksum "abc" 'md4                         | #{A448017AAF21D8525FC10AE87AA6729D}
                checksum "abc" 'xxh32                       | #{32D153FF}
                checksum "abc" 'xxh64                       | #{44BC2CF5AD770999}
                checksum "abc" 'xxh3                        | #{78AF5F94892F3950}
                checksum "abc" 'xxh128                      | #{06B05AB6733A618578AF5F94892F3950}
                checksum #{} 'md5                           | #{D41D8CD98F00B204E9800998ECF8427E}
                checksum "abc" 'crc32                       | 891568578
                checksum "" 'crc32                          | 0
                checksum "abc" 'adler32                     | 38600999
                checksum "abc" 'crc24                       | 12196987
                checksum "abc" 'tcp                         | 40506
                checksum #{FF} 'tcp                         | 65280
                checksum #{FFFF} 'tcp                       | 0
                checksum #{} 'tcp                           | 65535
                checksum head insert/dup copy #{} #{FF} 140000 'tcp | 1
                checksum/with "abc" 'hash 10                | 8
                checksum/with "abc" 'hash 0                 | 0
                checksum/with "abc" 'hash -5                | 0
                checksum/with #{616263} 'hash 1000          | 489
                checksum/with "abc" 'md5 "key"              | #{D2FE98063F876B03193AFB49B4979591}
                checksum/with "abc" 'sha256 #{6B6579}       | #{9C196E32DC0175F86F4B1CB89289D6619DE6BEE699E4C378E68309ED97A1A6AB}
                checksum/part "abcdef" 'crc32 3             | 891568578
                checksum/part "abcdef" 'md5 0               | #{D41D8CD98F00B204E9800998ECF8427E}
                checksum/part "abcdef" 'md5 100             | #{E80B5017098950FC58AAD83C8C14978E}
                checksum/part skip "abcdef" 3 'crc32 -2     | 3265866552
                checksum/part "abcdef" 'crc32 -100          | 0
                checksum/part "abcdef" 'crc32 1.5           | 3904355907
                checksum/part s: "abc" 'crc32 tail s        | 891568578
                checksum/with/part "abcdef" 'md5 "k" 3      | #{75972C9C6569F2F407752DDB02AC79DE}
                checksum/part/with "abcdef" 'md5 3 "k"      | #{75972C9C6569F2F407752DDB02AC79DE}
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                p: open checksum:sha1 write p "abc" read p                   | #{A9993E364706816ABA3E25717850C26C9CD0D89D}
                p: open checksum:md5 write p "ab" write p "c" read p         | #{900150983CD24FB0D6963F7D28E17F72}
                p: open checksum:md5 write/part p "abcdef" 3 read p          | #{900150983CD24FB0D6963F7D28E17F72}
                p: open checksum:md5 write/seek p "abcdef" 3 read p          | #{4ED9407630EB1000C0F6B63842DEFA7D}
                p: open checksum:md5 write/seek/part p "abcdef" 1 2 read p   | #{5360AF35BDE9EBD8F01F492DC059593C}
                p: open checksum:md5 write/part p "abcdef" -2 read p         | #{D41D8CD98F00B204E9800998ECF8427E}
                p: open checksum:xxh32 write p "abc" read p                  | #{32D153FF}
                p: open checksum:ripemd160 write p "a" write p "bc" read p   | #{8EB208F7E05D987A9B044A8E98C6B087F15A0BFC}
                p: open checksum:md5 read p                                  | #{D41D8CD98F00B204E9800998ECF8427E}
                p: open checksum:md5 write p "abc" close p read p            | _
                p: open checksum:sha256 write p #{616263} p/data             | _
                """)
        void aChecksumPortSumsWhatIsWrittenToIt(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                checksum "abc" 'nope                        | invalid-arg   | nope
                checksum "abc" 'hash                        | missing-arg   | _
                checksum/with "abc" 'hash "x"               | bad-refine    | "x"
                checksum/with "abc" 'md5 1                  | bad-refine    | 1
                checksum/with "abc" 'crc32 "k"              | bad-refines   | _
                checksum/part "abcdef" 'crc32 1x2           | invalid-part  | 1x2
                checksum/part "abc" 'crc32 tail "abc"       | invalid-part  | ""
                checksum/part "abc" 'crc32 3000000000       | out-of-range  | 3000000000
                p: open checksum:nope                       | invalid-spec  | %nope
                p: open checksum:md5 write p 1              | invalid-arg   | 1
                """)
        void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }

    @Nested
    @DisplayName("compress and decompress")
    class Compression {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                compress "aaaaaaaa" 'zlib                                 | #{78DA4B4C8400000DAC0309}
                compress/level "aaaaaaaa" 'zlib -1                        | #{78DA4B4C8400000DAC0309}
                compress/level "aaaaaaaa" 'zlib 0                         | #{7801010800F7FF61616161616161610DAC0309}
                compress "aaaaaaaa" 'deflate                              | #{4B4C840000}
                compress "aaaaaaaa" 'gzip                                 | #{1F8B08000000000002FF4B4C840000468084BF08000000}
                compress "aaaaaaaa" 'lzma                                 | #{5D000000010030EA7C00000008000000}
                compress "aaaaaaaa" 'crush                                | #{08000000C20A00}
                compress/part "abcdef" 'lzw 3                             | #{07616263FF01}
                decompress compress "abc" 'zlib 'zlib                     | #{616263}
                decompress compress "abc" 'deflate 'deflate               | #{616263}
                decompress compress "abc" 'gzip 'gzip                     | #{616263}
                decompress #{} 'zlib                                      | #{}
                decompress #{} 'gzip                                      | #{}
                decompress #{0000000000} 'crush                           | #{}
                decompress/size compress "abcdef" 'zlib 'zlib 6           | #{616263646566}
                decompress/size compress "abcdef" 'br 'br 3               | #{616263}
                decompress/size compress "abcdef" 'br 'br 100             | #{616263646566}
                decompress/size compress "abcdef" 'lzma 'lzma 3           | #{616263}
                decompress/size compress "abcdef" 'crush 'crush 3         | #{616263}
                decompress/size compress "abcdef" 'crush 'crush 100       | #{616263646566}
                decompress/size compress "abcdef" 'lzw 'lzw 3             | #{616263}
                decompress/size compress "abcdef" 'lzw 'lzw 100           | #{616263646566}
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                compress "aaaaaaaa" 'nope                                 | invalid-arg  | nope
                compress "aaaaaaaa" 'brotli                               | invalid-arg  | brotli
                decompress #{00} 'nope                                    | invalid-arg  | nope
                decompress "abc" 'zlib                                    | expect-arg   | decompress
                decompress #{0102} 'zlib                                  | bad-press    | 1
                decompress #{0102} 'deflate                               | bad-press    | 1
                decompress #{0102} 'gzip                                  | bad-press    | 1
                decompress #{0102} 'lzw                                   | bad-press    | 1
                decompress #{0102} 'br                                    | bad-press    | 2
                decompress #{} 'br                                        | bad-press    | 2
                decompress #{0102} 'crush                                 | bad-press    | crush
                decompress #{01} 'crush                                   | bad-press    | crush
                decompress #{0102} 'lzma                                  | past-end     | _
                decompress #{} 'lzma                                      | past-end     | _
                decompress/size compress "abcdef" 'zlib 'zlib 5           | bad-press    | 3
                decompress/size compress "abcdef" 'zlib 'zlib 100         | bad-press    | 2
                decompress/size compress "abcdef" 'deflate 'deflate 3     | bad-press    | 3
                decompress/size compress "abcdef" 'gzip 'gzip 3           | bad-press    | 3
                decompress/size compress "abcdef" 'gzip 'gzip 100         | bad-press    | 2
                decompress/size compress "abcdef" 'lzma 'lzma 100         | bad-press    | 6
                decompress/size compress "abcdef" 'deflate 'deflate 0     | out-of-range | 0
                decompress/size compress "abcdef" 'zlib 'zlib -1          | out-of-range | -1
                decompress/part compress "abc" 'zlib 'zlib 4              | bad-press    | 1
                decompress/part compress "abc" 'gzip 'gzip 4              | bad-press    | 1
                """)
        void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }

    @Nested
    @DisplayName("encloak and decloak")
    class Cloaking {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                encloak #{616263} "key"                     | #{0BA811}
                encloak #{616263} #{6B6579}                 | #{0BA811}
                encloak/with #{616263} "key"                | #{0B0C16}
                decloak encloak #{616263} "key" "key"       | #{616263}
                decloak/with encloak/with #{616263} "k" "k" | #{616263}
                encloak #{} "k"                             | #{}
                encloak skip #{616263} 1 "k"                | #{6AA8}
                encloak #{616263} "1"                       | #{0BC1FF}
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                encloak #{616263} 1                         | #{0BC1FF}
                encloak #{616263} 5                         | #{0B4145}
                """)
        @DisplayName("an integer key scrambles as its digits do, where Rebol hashes the bytes of its own cell")
        void anIntegerKeyIsHashedFromItsDigits(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                encloak #{616263} ""                        | invalid-arg | ""
                encloak/with #{616263} ""                   | invalid-arg | ""
                decloak #{616263} #{}                       | invalid-arg | #{}
                encloak protect #{616263} "k"               | protected   | _
                """)
        void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }

    @Nested
    @DisplayName("iconv")
    class Codepages {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                iconv #{616263} 'utf8                       | "abc"
                iconv #{E9} 'latin1                         | "é"
                iconv #{E9} "ISO-8859-1"                    | "é"
                iconv #{E9} 28591                           | "é"
                iconv #{E9} <ISO-8859-1>                    | "é"
                iconv #{C3A9} 'UTF-8                        | "é"
                iconv #{C3A9} 'utf-8                        | "é"
                iconv #{FFFE6100} 'utf16                    | "a"
                iconv #{} 'utf8                             | ""
                iconv/to #{E9} 'latin1 'utf8                | "é"
                iconv/to #{C3A9} 'utf8 'latin1              | #{E9}
                iconv/to #{C3A9} 'utf8 'utf16               | #{FEFF00E9}
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                iconv #{E9} 'nope                           | invalid-arg | nope
                iconv #{E9} 1                               | invalid-arg | 1
                iconv #{E9} 99999                           | invalid-arg | 99999
                iconv #{E9} "nope"                          | invalid-arg | "nope"
                iconv #{E9} <nope>                          | invalid-arg | <nope>
                iconv/to #{E9} 'latin1 'nope                | invalid-arg | nope
                """)
        void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }

    @Nested
    @DisplayName("filter and unfilter")
    class PngFilters {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                filter #{0102030405060708} 4 1                  | #{0101010105010101}
                filter #{0102030405060708} 4 'sub               | #{0101010105010101}
                filter #{0102030405060708} 4 'up                | #{0102030404040404}
                filter #{0102030405060708} 4 'average           | #{0102020305030303}
                filter #{0102030405060708} 4 'paeth             | #{0101010104010101}
                filter #{0102030405060708} 4 5                  | #{0101010104010101}
                filter #{0102030405060708} 4 0                  | #{0000000000000000}
                filter #{0102030405060708} 4 -1                 | #{0000000000000000}
                filter/skip #{0102030405060708} 4 1 2           | #{0102020205060202}
                filter #{0102030405060708} 4.5 1                | #{0101010105010101}
                filter #{0102030405060708} 2.9 1                | #{0101030105010701}
                filter #{01020304050607080910} 4 1              | #{01010101050101010000}
                filter #{0102030405060708FF} 4 0                | #{000000000000000000}
                unfilter #{01010101010101010101} 4              | #{0102030401020304}
                unfilter #{01010101010101010101FFFF} 4          | #{01020304010203040000}
                unfilter #{000102030405060708} 4                | #{0000000000000000}
                unfilter/as #{0101010101010101} 4 'sub          | #{0102030401020304}
                unfilter/as #{0101010101010101} 4 2             | #{0101010102020202}
                unfilter/as #{0101010101010101} 4 9             | #{0102030402030405}
                unfilter/as #{0102030405060708} 4 0             | #{0000000000000000}
                unfilter/as #{01010101010101010101FF} 4 1       | #{0102030401020304000000}
                unfilter/as/skip #{0101010101010101} 4 'sub 2   | #{0101020201010202}
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                filter #{0102030405060708} 4 'nope              | invalid-arg | nope
                filter #{0102030405060708} 4 'none              | invalid-arg | none
                filter #{0102030405060708} 1 1                  | invalid-arg | 1
                filter #{0102030405060708} 0 1                  | invalid-arg | 0
                filter #{0102030405060708} 1.9 1                | invalid-arg | 1.9
                filter #{0102030405060708} 9 1                  | invalid-arg | 9
                filter/skip #{0102030405060708} 4 1 5           | invalid-arg | 5
                filter/skip #{0102030405060708} 4 1 0           | invalid-arg | 0
                unfilter #{0101} 4                              | invalid-arg | 4
                unfilter/as #{0102030405060708} 1 1             | invalid-arg | 1
                unfilter/as #{0102030405060708} 4 'none         | invalid-arg | none
                """)
        void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }

    @Nested
    @DisplayName("swap-endian")
    class SwappingEnds {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                swap-endian #{01020304}                          | #{02010403}
                swap-endian #{010203}                            | #{020103}
                swap-endian #{}                                  | #{}
                swap-endian/width #{0102030405060708} 4          | #{0403020108070605}
                swap-endian/width #{0102030405060708} 8          | #{0807060504030201}
                swap-endian/part #{01020304} 2                   | #{02010304}
                swap-endian/part #{01020304} 3                   | #{02010304}
                swap-endian/part #{01020304} 0                   | #{01020304}
                swap-endian/part #{01020304} -2                  | #{01020304}
                swap-endian/part #{01020304} 1.5                 | #{01020304}
                swap-endian/part #{01020304} 100                 | #{02010403}
                swap-endian/width/part #{0102030405060708} 4 4   | #{0403020105060708}
                swap-endian skip #{01020304} 1                   | #{030204}
                b: #{01020304} swap-endian/part b skip b 2       | #{02010304}
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                swap-endian/width #{0102030405060708} 3          | invalid-arg | 3
                swap-endian/width #{0102030405060708} 1          | invalid-arg | 1
                swap-endian/width #{0102} 16                     | invalid-arg | 16
                swap-endian/width #{0102} 0                      | invalid-arg | 0
                swap-endian/width #{0102} -2                     | invalid-arg | -2
                """)
        void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }
}
