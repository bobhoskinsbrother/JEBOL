package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LZMA, ported from the SDK Rebol vendors in {@code u-lzma.c}.
 *
 * <p>An arithmetic coder over adaptive bit models. Each step is a literal
 * byte, a repeat of one of the four most recent distances, or a fresh distance
 * and length; a twelve-value state remembers what the last few steps were so
 * the models can be conditioned on it.
 *
 * <p>Rebol frames it its own way, and the framing is what makes a Rebol stream
 * unreadable to 7-Zip and the other way round: five property bytes, the
 * stream, then the uncompressed length in four bytes little endian. A file
 * written by 7-Zip has an eight-byte length in the header instead and nothing
 * at the end.
 *
 * <p>The bytes are asserted and not only the round trip, because a compressor
 * that reads its own output back is not thereby the same compressor. Every
 * expected value here came out of {@code ./r3-head}, and a sweep of ten inputs
 * from nothing to eighty-six kilobytes across all eleven levels answers byte
 * for byte what a real 3.22.5 answers -- which is the claim worth making,
 * because the level chooses between a greedy parse and a priced one and only
 * the priced one has to agree about which of several equal-length matches to
 * take.
 *
 * <p>Two faults that this file would have caught and a round trip would not,
 * both found by the sweep rather than by reading: a reversed bit tree walked
 * the wrong way from its third bit, so the decoder adapted a different model
 * from the one the encoder had used; and a repeat of exactly two bytes decodes
 * to the length symbol zero, which was being read as "this repeat wrote its
 * own single byte", losing the last two bytes of anything that ended on one.
 */
class LzmaFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String LOREM = """
            text: {Lorem ipsum dolor sit amet, consectetur adipisici elit,
            sed eiusmod tempor incidunt ut labore et dolore magna aliqua.
            Ut enim ad minim veniam, quis nostrud exercitation ullamco
            laboris nisi ut aliquid ex ea commodi consequat.}
            """;

    @Nested
    @DisplayName("the bytes it writes")
    class TheBytes {

        @Test
        @DisplayName("nothing is a header, five bytes of flush and a zero length")
        void nothingAtLevelZero() {
            assertThat(answerTo("""
                    compress/level "" 'lzma 0"""))
                    .isEqualTo("#{5D00400000000000000000000000}");
        }

        @Test
        @DisplayName("something with a pattern in it, at the lowest level")
        void somethingWithAPattern() {
            assertThat(answerTo("""
                    compress/level "test test test" 'lzma 0"""))
                    .isEqualTo("#{5D00400000003A194ACE1CFB1CD99000000E000000}");
        }

        @Test
        @DisplayName("and the same bytes at the level nobody asked for, bar the header")
        void theSameBytesAtTheDefaultLevel() {
            assertThat(answerTo("""
                    compress "test test test" 'lzma"""))
                    .isEqualTo("#{5D00000001003A194ACE1CFB1CD99000000E000000}");
        }

        @Test
        @DisplayName("the last four bytes are the uncompressed length, little endian")
        void theLastFourBytesAreTheLength() {
            assertThat(answerTo("""
                    packed: compress "test test test" 'lzma
                    skip packed (length? packed) - 4""")).isEqualTo("#{0E000000}");
        }

        @Test
        @DisplayName("the first byte packs the three context widths, and is always 5D")
        void theFirstByteIsTheContextWidths() {
            assertThat(answerTo("""
                    to integer! first compress "x" 'lzma""")).isEqualTo("93");
        }
    }

    /**
     * The level chooses the dictionary and how the parse is made, and only the
     * dictionary shows in the header. Below five the parse is greedy and from
     * five it is priced, which is why the body changes between four and five
     * and not between five and six.
     */
    @Nested
    @DisplayName("what the level decides")
    class WhatTheLevelDecides {

        private static String headerAtLevel(String level) {
            return answerTo(
                    "copy/part (compress/level {test test test} 'lzma "
                            + level + ") 5");
        }

        @Test
        @DisplayName("the dictionary doubles twice per level up to five")
        void theDictionaryGrowsWithTheLevel() {
            assertThat(headerAtLevel("0")).isEqualTo("#{5D00400000}");
            assertThat(headerAtLevel("1")).isEqualTo("#{5D00000100}");
            assertThat(headerAtLevel("2")).isEqualTo("#{5D00000400}");
            assertThat(headerAtLevel("3")).isEqualTo("#{5D00001000}");
            assertThat(headerAtLevel("4")).isEqualTo("#{5D00004000}");
            assertThat(headerAtLevel("5")).isEqualTo("#{5D00000001}");
        }

        @Test
        @DisplayName("and then stands still for six and seven, and again for eight and nine")
        void theDictionaryStopsDoubling() {
            assertThat(headerAtLevel("6")).isEqualTo("#{5D00000002}");
            assertThat(headerAtLevel("7")).isEqualTo("#{5D00000002}");
            assertThat(headerAtLevel("8")).isEqualTo("#{5D00000004}");
            assertThat(headerAtLevel("9")).isEqualTo("#{5D00000004}");
        }

        @Test
        @DisplayName("minus one is the level nobody asked for, which is five")
        void minusOneIsTheDefault() {
            assertThat(headerAtLevel("-1")).isEqualTo("#{5D00000001}");
            assertThat(answerTo("""
                    (compress/level {test test test} 'lzma -1)
                        = compress {test test test} 'lzma""")).isEqualTo("#(true)");
        }

        /**
         * {@code MIN(9, level)} over an unsigned level, so a negative that is
         * not minus one arrives as a number near four thousand million and
         * comes out as nine. Checked against r3-head rather than reasoned
         * about.
         */
        @Test
        @DisplayName("every other negative is the slowest level, and so is anything above nine")
        void everyOtherNegativeIsTheSlowest() {
            assertThat(headerAtLevel("-5")).isEqualTo("#{5D00000004}");
            assertThat(headerAtLevel("99")).isEqualTo("#{5D00000004}");
        }

        @Test
        @DisplayName("the body changes where the parse changes and not where the dictionary does")
        void theBodyChangesWhereTheParseDoes() {
            assertThat(answerTo(LOREM + """
                    collect [
                        repeat level 10 [
                            keep checksum
                                (skip compress/level text 'lzma (level - 1) 5)
                                'crc32
                        ]
                    ]""")).isEqualTo("[1551896313 1551896313 1551896313 1551896313"
                            + " 1551896313 3785810532 3785810532 3785810532"
                            + " 3785810532 3785810532]");
        }
    }

    @Nested
    @DisplayName("reading it back")
    class ReadingItBack {

        @Test
        @DisplayName("what went in comes out, at every level")
        void everyLevelRoundTrips() {
            assertThat(answerTo(LOREM + """
                    collect [
                        repeat level 10 [
                            keep text = to string! decompress
                                compress/level text 'lzma (level - 1) 'lzma
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)"
                            + " #(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("and nothing comes back as nothing")
        void nothingComesBackAsNothing() {
            assertThat(answerTo("""
                    decompress compress "" 'lzma 'lzma""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("/SIZE stops early, which is how the front is read on its own")
        void sizeStopsEarly() {
            assertThat(answerTo("""
                    packed: compress "test test test" 'lzma
                    decompress/size packed 'lzma 4""")).isEqualTo("#{74657374}");
        }

        @Test
        @DisplayName("it can be read from a position rather than from the head")
        void readFromAPosition() {
            assertThat(answerTo("""
                    data: "test test test"
                    data = to string! decompress
                        next join #{00} compress data 'lzma 'lzma"""))
                    .isEqualTo("#(true)");
        }

        /**
         * A run of exactly two bytes at the very end is the case a shorter
         * round trip never reaches: it decodes to the length symbol zero,
         * which reads as "no length was written" unless the two are told
         * apart. Two hundred and five characters of Lorem is the shortest
         * prefix that ends on one.
         */
        @Test
        @DisplayName("a stream that ends on a two-byte repeat keeps its last two bytes")
        void aStreamEndingOnATwoByteRepeat() {
            assertThat(answerTo(LOREM + """
                    front: copy/part text 205
                    front = to string! decompress compress front 'lzma 'lzma"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("every prefix of a paragraph round trips, at every level")
        void everyPrefixRoundTrips() {
            assertThat(answerTo(LOREM + """
                    bad: copy []
                    repeat n length? text [
                        front: copy/part text n
                        repeat level 10 [
                            unless front = to string! decompress
                                    compress/level front 'lzma (level - 1) 'lzma [
                                append bad reduce [n level]
                            ]
                        ]
                    ]
                    bad""")).isEqualTo("[]");
        }

        @Test
        @DisplayName("a real 3.22.5 stream reads here")
        void aRealStreamReadsHere() {
            assertThat(answerTo("""
                    to string! decompress
                        #{5D00000001003A194ACE1CFB1CD99000000E000000} 'lzma"""))
                    .isEqualTo("\"test test test\"");
        }

        @Test
        @DisplayName("and one this build wrote is the same bytes a real one wrote")
        void andOursIsTheSameBytes() {
            assertThat(answerTo("""
                    (compress "test test test" 'lzma)
                        = #{5D00000001003A194ACE1CFB1CD99000000E000000}"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("data that is not an LZMA stream")
    class Refusals {

        @Test
        @DisplayName("fewer than nine bytes is past-end, not bad-press")
        void tooShortIsPastEnd() {
            assertThat(answerTo("""
                    failure: try [decompress #{00} 'lzma]
                    failure/id""")).isEqualTo("past-end");
        }

        @Test
        @DisplayName("eight bytes is still past-end, and nine is not")
        void eightIsPastEndAndNineIsNot() {
            assertThat(answerTo("""
                    eight: try [decompress #{0000000000000000} 'lzma]
                    nine: try [decompress #{000000000000000000} 'lzma]
                    reduce [eight/id nine/id]""")).isEqualTo("[past-end bad-press]");
        }

        @Test
        @DisplayName("a first byte that is not zero is not a stream")
        void aStreamOpensWithZero() {
            assertThat(answerTo("""
                    failure: try [decompress #{5D004000000100000000000004000000} 'lzma]
                    failure/id""")).isEqualTo("bad-press");
        }

        @Test
        @DisplayName("and a property byte above 224 names no context widths at all")
        void aPropertyByteOutOfRange() {
            assertThat(answerTo("""
                    failure: try [decompress #{FF00400000000000000000000000} 'lzma]
                    failure/id""")).isEqualTo("bad-press");
        }
    }

    @Nested
    @DisplayName("where it sits among the methods")
    class AmongTheMethods {

        @Test
        @DisplayName("the catalogue lists it, and COMPRESS answers to it")
        void theCatalogueListsIt() {
            assertThat(answerTo("""
                    reduce [
                        true? find system/catalog/compressions 'lzma
                        not error? try [compress "x" 'lzma]
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("and the two this build still has not got say feature-na")
        void theOthersStillSayFeatureNa() {
            assertThat(answerTo("""
                    collect [
                        foreach method [lz4 lzav][
                            raised: try [compress "x" method]
                            keep raised/id
                        ]
                    ]""")).isEqualTo("[feature-na feature-na]");
        }
    }
}
