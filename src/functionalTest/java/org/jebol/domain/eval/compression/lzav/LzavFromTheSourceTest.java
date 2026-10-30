package org.jebol.domain.eval.compression.lzav;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LzavFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        String whole = FIXTURES + source;
        interpreter.defineNewWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    private static final String FIXTURES = """
            noise: func [n [integer!] seed /local b h][
                b: make binary! n
                h: checksum seed 'sha1
                while [(length? b) < n][append b h h: checksum h 'sha1]
                copy/part b n
            ]
            mixture: func [n [integer!] /local b h][
                b: make binary! n
                h: checksum "seed" 'sha1
                while [(length? b) < n][
                    append b h
                    append b "the quick brown fox jumps over the lazy dog "
                    h: checksum h 'sha1
                ]
                copy/part b n
            ]
            repeated-at: func [distance [integer!] length [integer!] /local b][
                b: noise distance "far"
                append b copy/part b length
                append b noise 16 "tail"
            ]
            sha: func [x][copy/part enbase checksum x 'sha1 16 8]
            described: func [data /local packed][
                packed: compress data 'lzav
                reduce [length? data sha data length? packed sha packed
                        data = decompress/size packed 'lzav length? data]
            ]
            refusal: func [code [block!] /local raised][
                raised: try code
                reduce [raised/id raised/arg1]
            ]
            """;

    @Nested
    @DisplayName("what COMPRESS writes")
    class Compressing {

        @Test
        @DisplayName("nothing at all is refused, and reports nothing")
        void refusesNothing() {
            assertThat(answerTo("""
                    refusal [compress "" 'lzav]""")).isEqualTo("[bad-press 0]");
        }

        @Test
        @DisplayName("fewer than nine octets are padded with zeros to nine")
        void padsAShortInputToNineOctets() {
            assertThat(answerTo("""
                    reduce [compress "aaaa" 'lzav compress #{6162636465666768} 'lzav
                            compress #{616263646566676869} 'lzav]""")).isEqualTo(
                    "[#{3504616161610000000000} #{3508616263646566676800} #{3509616263646566676869}]");
        }

        @Test
        @DisplayName("fifteen octets are still written whole, and sixteen are the first that are compressed")
        void compressesFromSixteenOctets() {
            assertThat(answerTo("""
                    collect [foreach n [1 8 9 15 16 17][keep/only described mixture n]]""")).isEqualTo("""
                    [[1 "E67CB59B" 11 "0FA4306D" #(true)] [8 "80EC8081" 11 "7FD3F849" #(true)] \
                    [9 "1F104D73" 11 "209DE07B" #(true)] [15 "63F95E4E" 17 "EB0DE3D5" #(true)] \
                    [16 "824580FB" 19 "AD16281F" #(true)] [17 "F9F51DC0" 20 "2C90F8FB" #(true)]]""");
        }

        @Test
        @DisplayName("sixteen and seventeen octets, byte for byte")
        void writesRebolsBytesForSixteenAndSeventeen() {
            assertThat(answerTo("""
                    reduce [compress mixture 16 'lzav compress mixture 17 'lzav]""")).isEqualTo(
                    "[#{35000092713D4709377111CF31F2A71986C411} #{35000192713D4709377111CF31F2A71986C411BD}]");
        }

        @Test
        @DisplayName("either side of 513 octets, where the hash table doubles")
        void writesRebolsBytesEitherSideOfTheFirstTableSize() {
            assertThat(answerTo("""
                    collect [foreach n [512 513][keep/only described mixture n]]""")).isEqualTo("""
                    [[512 "E163FDC1" 250 "44D6F603" #(true)] [513 "0B8A4AA3" 250 "ED3A14E4" #(true)]]""");
        }

        @Test
        @DisplayName("either side of 256 kilobytes, where the shortest reference grows from five octets to six")
        void growsTheShortestReferenceAtTwoHundredAndFiftySixKilobytes() {
            assertThat(answerTo("""
                    reduce [
                        first compress mixture 262143 'lzav
                        first compress mixture 262144 'lzav
                        described mixture 262143
                        described mixture 262144
                    ]""")).isEqualTo("""
                    [53 54 [262143 "C3B379F4" 100377 "8D3BDD8A" #(true)] \
                    [262144 "B943D9C8" 100384 "3DB4886E" #(true)]]""");
        }

        @Test
        @DisplayName("either side of each offset width: 1023 and 1024 back, 32767 and 32768 back")
        void widensTheOffsetAtEachThreshold() {
            assertThat(answerTo("""
                    collect [foreach d [1023 1024 32767 32768][keep/only described repeated-at d 40]]""")).isEqualTo("""
                    [[1079 "CD649C17" 1048 "BCECB64D" #(true)] [1080 "611EE14E" 1049 "092291D6" #(true)] \
                    [32823 "AF1BFFA8" 32828 "000D36CD" #(true)] [32824 "A9BDA053" 32829 "E3B7426B" #(true)]]""");
        }

        @Test
        @DisplayName("a repeat three million octets back, beyond the two megabyte window")
        void reachesBeyondTheWindowWhenLiteralsCarryTheOffset() {
            assertThat(answerTo("""
                    described repeated-at 3000000 100""")).isEqualTo("""
                    [3000116 "2B7A45A3" 3000029 "D40FAD46" #(true)]""");
        }

        @Test
        @DisplayName("a hundred thousand zeros")
        void writesALongRunOfZeros() {
            assertThat(answerTo("""
                    described append/dup #{} #{00} 100000""")).isEqualTo("""
                    [100000 "B98C6A15" 25 "8B2EF41C" #(true)]""");
        }

        @Test
        @DisplayName("LZAV has no levels, and a level asked for changes nothing")
        void ignoresTheLevel() {
            assertThat(answerTo("""
                    data: mixture 70000
                    reduce [
                        (compress data 'lzav) = compress/level data 'lzav 1
                        (compress data 'lzav) = compress/level data 'lzav 12
                        (compress data 'lzav) = compress/level data 'lzav -1
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("COMPRESS/PART compresses only the part")
        void compressesOnlyThePart() {
            assertThat(answerTo("""
                    compress/part skip "xyabcdefabcdef" 2 'lzav 9""")).isEqualTo("#{3509616263646566616263}");
        }
    }

    @Nested
    @DisplayName("what DECOMPRESS reads")
    class Decompressing {

        @Test
        @DisplayName("without a size it refuses, because LZAV never says how long the answer is")
        void refusesWithoutASize() {
            assertThat(answerTo("""
                    refusal [decompress compress "abc" 'lzav 'lzav]""")).isEqualTo("[bad-press 0]");
        }

        @Test
        @DisplayName("the size must be the answer's own: fewer is out of room and more is a length mismatch")
        void needsTheExactSize() {
            assertThat(answerTo("""
                    packed: compress "abc" 'lzav
                    reduce [
                        decompress/size packed 'lzav 3
                        refusal [decompress/size packed 'lzav 2]
                        refusal [decompress/size packed 'lzav 5]
                    ]""")).isEqualTo("[#{616263} [bad-press -3] [bad-press -5]]");
        }

        @Test
        @DisplayName("a size below one is out of range, and one too large for a series is out of memory")
        void refusesASizeOutOfRange() {
            assertThat(answerTo("""
                    packed: compress "x" 'lzav
                    reduce [
                        refusal [decompress/size packed 'lzav 0]
                        refusal [decompress/size packed 'lzav -1]
                        first refusal [decompress/size packed 'lzav 2147483647]
                        refusal [decompress/size packed 'lzav 2147483646]
                    ]""")).isEqualTo("[[out-of-range 0] [out-of-range -1] no-memory [bad-press -5]]");
        }

        @Test
        @DisplayName("each refusal carries LZAV's own code")
        void reportsLzavsOwnCodes() {
            assertThat(answerTo("""
                    reduce [
                        refusal [decompress/size #{} 'lzav 4]
                        refusal [decompress/size #{2500000000000000} 'lzav 4]
                        refusal [decompress/size #{3511000000000000000000} 'lzav 4]
                        refusal [decompress/size #{00} 'lzav 4]
                        refusal [decompress/size #{60} 'lzav 4]
                    ]""")).isEqualTo("[[bad-press -1] [bad-press -2] [bad-press -4] [bad-press -6] [bad-press -6]]");
        }

        @Test
        @DisplayName("the older second format still reads")
        void readsTheSecondFormat() {
            assertThat(answerTo("""
                    old: #{2509616263646566676869}
                    reduce [
                        decompress/size old 'lzav 9
                        refusal [decompress/size old 'lzav 8]
                        refusal [decompress/size old 'lzav 10]
                    ]""")).isEqualTo("[#{616263646566676869} [bad-press -3] [bad-press -5]]");
        }

        @Test
        @DisplayName("DECOMPRESS/PART reads only the part")
        void readsOnlyThePart() {
            assertThat(answerTo("""
                    packed: compress "abcdefabcdefabcdefabcdef" 'lzav
                    reduce [
                        refusal [decompress/part packed 'lzav 5]
                        refusal [decompress/part/size packed 'lzav 5 24]
                        to string! decompress/part/size packed 'lzav length? packed 24
                    ]""")).isEqualTo("""
                    [[bad-press 0] [bad-press -2] "abcdefabcdefabcdefabcdef"]""");
        }
    }
}
