package org.jebol.domain.eval.compression.lz4;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Lz4FromTheSourceTest {

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
            literals: func [n [integer!] /local b k][
                b: make binary! n + 20
                either n < 15 [append b n * 16][
                    append b 240
                    k: n - 15
                    while [k >= 255][append b 255 k: k - 255]
                    append b k
                ]
                append/dup b #{61} n
                b
            ]
            run-of: func [n [integer!]][append/dup copy #{} #{61} n]
            sha: func [x][copy/part enbase checksum x 'sha1 16 8]
            at-each-level: func [data][collect [repeat n 12 [keep sha compress/level data 'lz4 n]]]
            """;

    @Nested
    @DisplayName("what COMPRESS writes")
    class Compressing {

        @Test
        @DisplayName("nothing at all is the one zero octet")
        void compressesNothingToOneZeroOctet() {
            assertThat(answerTo("""
                    compress "" 'lz4""")).isEqualTo("#{00}");
        }

        @Test
        @DisplayName("four octets are a single run of literals")
        void writesAShortInputAsLiterals() {
            assertThat(answerTo("""
                    compress "aaaa" 'lz4""")).isEqualTo("#{4061616161}");
        }

        @Test
        @DisplayName("twelve octets are too few to look for a match in, and thirteen are not")
        void startsLookingForMatchesAtThirteenOctets() {
            assertThat(answerTo("""
                    collect [
                        foreach n [12 13 14 30][
                            keep/only collect [foreach level [1 9 12][keep compress/level run-of n 'lz4 level]]
                        ]
                    ]""")).isEqualTo("[["
                    + "#{C0616161616161616161616161} #{C0616161616161616161616161} "
                    + "#{C0616161616161616161616161}] ["
                    + "#{13610100506161616161} #{13610100506161616161} #{13610100506161616161}] ["
                    + "#{14610100506161616161} #{14610100506161616161} #{14610100506161616161}] ["
                    + "#{1F61010005506161616161} #{1F61010005506161616161} #{1F61010005506161616161}]]");
        }

        @Test
        @DisplayName("the fixtures are the inputs they say they are")
        void fixturesAreWhatTheySay() {
            assertThat(answerTo("""
                    reduce [
                        length? mixture 70000  sha mixture 70000
                        length? mixture 140000  sha mixture 140000
                        length? repeated-at 65535 40  sha repeated-at 65535 40
                        length? repeated-at 65536 40  sha repeated-at 65536 40
                    ]""")).isEqualTo("""
                    [70000 "08CBD666" 140000 "382DCEBA" 65591 "E20D3C01" 65592 "ACC94FAC"]""");
        }

        @Test
        @DisplayName("each of the twelve levels writes R3's bytes for seventy thousand mixed octets")
        void writesRebolsBytesAtEveryLevel() {
            assertThat(answerTo("""
                    at-each-level mixture 70000""")).isEqualTo("""
                    ["A9801168" "A9801168" "2E7C714D" "FE250358" "803141D6" "8EBFEB78" \
                    "2825C9BA" "972F699C" "878BCC74" "C33B05D9" "C0260D2B" "EE64EAB9"]""");
        }

        @Test
        @DisplayName("and for a hundred and forty thousand, which wraps the sixty-four kilobyte chain twice")
        void writesRebolsBytesPastTheChainTable() {
            assertThat(answerTo("""
                    at-each-level mixture 140000""")).isEqualTo("""
                    ["05342A63" "05342A63" "0ED25E6F" "E6D21510" "771C5C4B" "A2924A26" \
                    "3D0F695E" "E5246EFE" "D47113E4" "3FC9C0E1" "F5FE9897" "D989A693"]""");
        }

        @Test
        @DisplayName("a repeat 65535 octets back is reached from level three, and one 65536 back never is")
        void reachesBackNoFurtherThanTheLargestOffset() {
            assertThat(answerTo("""
                    reduce [
                        collect [foreach level [1 3 9 12][keep length? compress/level repeated-at 65535 40 'lz4 level]]
                        collect [foreach level [1 3 9 12][keep length? compress/level repeated-at 65536 40 'lz4 level]]
                        sha compress/level repeated-at 65535 40 'lz4 12
                        sha compress/level repeated-at 65536 40 'lz4 12
                    ]""")).isEqualTo("""
                    [[65850 65813 65813 65813] [65851 65849 65849 65849] "38950395" "278354B3"]""");
        }

        @Test
        @DisplayName("a long run of zeros is one match whatever the level")
        void writesALongRunAsOneMatch() {
            assertThat(answerTo("""
                    reduce [length? compress append/dup #{} #{00} 5000 'lz4
                            unique at-each-level append/dup #{} #{00} 5000]""")).isEqualTo("""
                    [30 ["E5D83EBB"]]""");
        }

        @Test
        @DisplayName("noise is left as literals at every level")
        void leavesNoiseAsLiterals() {
            assertThat(answerTo("""
                    reduce [length? noise 3000 "n" unique at-each-level noise 3000 "n"]""")).isEqualTo("""
                    [3000 ["0F314CB9"]]""");
        }
    }

    @Nested
    @DisplayName("which level is used")
    class TheLevel {

        @Test
        @DisplayName("nobody asking is level one, the fastest")
        void usesLevelOneWhenNobodyAsks() {
            assertThat(answerTo("""
                    data: mixture 70000
                    reduce [
                        (compress data 'lz4) = compress/level data 'lz4 1
                        (compress data 'lz4) = compress/level data 'lz4 3
                    ]""")).isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("levels one and two are the same compressor")
        void sharesOneCompressorBetweenLevelsOneAndTwo() {
            assertThat(answerTo("""
                    data: mixture 70000
                    (compress/level data 'lz4 1) = compress/level data 'lz4 2""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a level below one is one and a level above twelve is twelve, both ways round")
        void clampsTheLevelAtBothEnds() {
            assertThat(answerTo("""
                    data: mixture 70000
                    one: compress/level data 'lz4 1
                    twelve: compress/level data 'lz4 12
                    reduce [
                        one = compress/level data 'lz4 0
                        one = compress/level data 'lz4 -1
                        one = compress/level data 'lz4 -5
                        twelve = compress/level data 'lz4 13
                        twelve = compress/level data 'lz4 100
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("a level is read as its low thirty-two bits")
        void readsTheLowThirtyTwoBitsOfTheLevel() {
            assertThat(answerTo("""
                    data: mixture 70000
                    reduce [
                        (compress/level data 'lz4 4294967299) = compress/level data 'lz4 3
                        (compress/level data 'lz4 4294967296) = compress/level data 'lz4 1
                        (compress/level data 'lz4 -4294967295) = compress/level data 'lz4 1
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("COMPRESS/PART compresses only the part")
        void compressesOnlyThePart() {
            assertThat(answerTo("""
                    compress/part "abcdefabcdef" 'lz4 9""")).isEqualTo("#{90616263646566616263}");
        }
    }

    @Nested
    @DisplayName("what DECOMPRESS reads")
    class Decompressing {

        @Test
        @DisplayName("every level reads back, given the size and without it")
        void readsBackEveryLevel() {
            assertThat(answerTo("""
                    data: mixture 140000
                    reduce [
                        unique collect [repeat n 12 [keep data = decompress/size compress/level data 'lz4 n 'lz4 140000]]
                        unique collect [repeat n 12 [keep data = decompress compress/level data 'lz4 n 'lz4]]
                    ]""")).isEqualTo("[[#(true)] [#(true)]]");
        }

        @Test
        @DisplayName("nothing, and the one zero octet nothing compresses to, are both refused")
        void refusesNothingAndTheEmptyBlock() {
            assertThat(answerTo("""
                    first-raised: try [decompress #{} 'lz4]
                    second-raised: try [decompress #{00} 'lz4]
                    reduce [first-raised/id first-raised/arg1 second-raised/id second-raised/arg1]""")).isEqualTo("""
                    [bad-press 0 bad-press 0]""");
        }

        @Test
        @DisplayName("the size asked for is room, not a cut: a longer answer that fits comes back whole")
        void answersTheWholeOfWhatFitsTheRoom() {
            assertThat(answerTo("""
                    decompress/size compress "abcabc" 'lz4 'lz4 3""")).isEqualTo("#{616263616263}");
        }

        @Test
        @DisplayName("the room is the series Rebol makes for the size, rounded up to its memory pool")
        void roundsTheRoomUpToRebolsPool() {
            assertThat(answerTo("""
                    collect [
                        foreach [made size] [32 30 33 30 32 31 64 31 64 32 96 63 97 63
                                             5120 4095 5120 4096 5120 5000 5121 5119][
                            keep not error? try [decompress/size literals made 'lz4 size]
                        ]
                    ]""")).isEqualTo("[#(true) #(false) #(true) #(false) #(true) #(false) #(false)"
                    + " #(false) #(true) #(true) #(false)]");
        }

        @Test
        @DisplayName("without a size the room is three times the compressed length, so sixty-four octets fit"
                + " eleven and sixty-five do not")
        void givesThreeTimesTheCompressedLengthWithoutASize() {
            assertThat(answerTo("""
                    reduce [
                        length? compress run-of 64 'lz4
                        length? compress run-of 65 'lz4
                        (run-of 64) = decompress compress run-of 64 'lz4 'lz4
                        error? try [decompress compress run-of 65 'lz4 'lz4]
                        (run-of 65) = decompress/size compress run-of 65 'lz4 'lz4 65
                    ]""")).isEqualTo("[11 11 #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("a match with no offset copies zeros, and one reaching before the start is refused")
        void copiesZerosForNoOffsetAndRefusesOneBeforeTheStart() {
            assertThat(answerTo("""
                    raised: try [decompress #{10610200506263646566} 'lz4]
                    reduce [decompress #{10610000506263646566} 'lz4 raised/id raised/arg1]""")).isEqualTo("""
                    [#{61000000006263646566} bad-press 0]""");
        }

        @Test
        @DisplayName("DECOMPRESS/PART reads only the part, which must end where the block does")
        void readsOnlyThePart() {
            assertThat(answerTo("""
                    raised: try [decompress/part #{60616263646566FF} 'lz4 8]
                    reduce [decompress/part #{60616263646566FF} 'lz4 7 raised/id]""")).isEqualTo("""
                    [#{616263646566} bad-press]""");
        }

        @Test
        @DisplayName("a size Rebol cannot make a series of is out of memory, before anything is read")
        void runsOutOfMemoryForASizeTooLargeForASeries() {
            assertThat(answerTo("""
                    raised: try [decompress/size compress "x" 'lz4 'lz4 2147483647]
                    raised/id""")).isEqualTo("no-memory");
        }

        @Test
        @DisplayName("and room of two gigabytes or more is refused, because LZ4 takes its room as an int")
        void refusesRoomThatDoesNotFitAnInt() {
            assertThat(answerTo("""
                    reduce [
                        decompress/size compress "x" 'lz4 'lz4 2147482623
                        error? try [decompress/size compress "x" 'lz4 'lz4 2147482624]
                        error? try [decompress/size compress "x" 'lz4 'lz4 2147483646]
                    ]""")).isEqualTo("[#{78} #(true) #(true)]");
        }
    }
}
