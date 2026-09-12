package org.jebol.domain.eval.brotli;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrotliFromTheSourceTest {

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
        @DisplayName("nothing is one byte, and that byte is 3B")
        void nothingIsOneByte() {
            assertThat(answerTo("""
                    compress/level "" 'br 0""")).isEqualTo("#{3B}");
        }

        @Test
        @DisplayName("and fourteen bytes are stored, not compressed")
        void fourteenBytesAreStored() {
            assertThat(answerTo("""
                    compress/level "test test test" 'br 0"""))
                    .isEqualTo("#{8B0680746573742074657374207465737403}");
        }

        @Test
        @DisplayName("level one is a different encoder, and on this input agrees")
        void levelOneIsADifferentEncoder() {
            assertThat(answerTo("""
                    (compress/level "test test test" 'br 1)
                        = compress/level "test test test" 'br 0"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and on a paragraph it is not, and both match a real 3.22.5")
        void levelOneDiffersOnAParagraph() {
            assertThat(answerTo(LOREM + """
                    reduce [
                        (compress/level text 'br 1) = compress/level text 'br 0
                        length? compress/level text 'br 0
                        length? compress/level text 'br 1
                    ]""")).isEqualTo("[#(false) 209 166]");
        }

        @Test
        @DisplayName("the level nobody asks for is six, and six is exact")
        void theLevelNobodyAsksForIsExact() {
            assertThat(answerTo("""
                    reduce [
                        (compress "test test test" 'br)
                            = #{1B0D0000A441CAE6E8C42B516C03}
                        (compress/level "test test test" 'br 2)
                            = compress/level "test test test" 'br 0
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("levels ten and eleven, which differ from nine and from a real one no longer")
        void levelsTenAndElevenAreExact() {
            assertThat(answerTo("""
                    reduce [
                        (compress/level "test test test" 'br 10)
                            = compress/level "test test test" 'br 9
                        (compress/level "test test test" 'br 11)
                            = #{1B0D00F8A541CAE6E8C42B51C036}
                        (compress/level "test test test" 'br 10)
                            = compress/level "test test test" 'br 11
                    ]""")).isEqualTo("[#(false) #(true) #(true)]");
        }

        @Test
        @DisplayName("a paragraph does get a prefix code, and comes out smaller")
        void aParagraphIsCompressed() {
            assertThat(answerTo(LOREM + """
                    reduce [
                        length? text
                        length? compress/level text 'br 0
                    ]""")).isEqualTo("[225 209]");
        }

        @Test
        @DisplayName("a short sentence, byte for byte what a real 3.22.5 writes")
        void aShortSentence() {
            assertThat(answerTo("""
                    (compress/level "the quick brown fox jumps over the lazy dog" 'br 0)
                        = #{0B15807468652071756963 6B2062726F776E20666F78
                            206A756D7073206F766572 20746865206C617A7920646F6703}"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("reading it back")
    class ReadingItBack {

        @Test
        @DisplayName("what went in comes out, at every one of the twelve levels")
        void everyLevelRoundTrips() {
            assertThat(answerTo(LOREM + """
                    collect [
                        repeat level 12 [
                            keep text = to string! decompress
                                compress/level text 'br (level - 1) 'br
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true) #(true)"
                            + " #(true) #(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("and nothing comes back as nothing")
        void nothingComesBackAsNothing() {
            assertThat(answerTo("""
                    decompress compress "" 'br 'br""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("/SIZE stops early, which is how the front is read on its own")
        void sizeStopsEarly() {
            assertThat(answerTo("""
                    packed: compress "test test test" 'br
                    decompress/size packed 'br 4""")).isEqualTo("#{74657374}");
        }

        @Test
        @DisplayName("it can be read from a position rather than from the head")
        void readFromAPosition() {
            assertThat(answerTo("""
                    data: "test test test"
                    data = to string! decompress
                        next join #{00} compress data 'br 'br""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("something long enough to need more than one meta-block")
        void longEnoughForSeveralMetaBlocks() {
            assertThat(answerTo(LOREM + """
                    long: copy ""
                    repeat n 600 [append long text]
                    long = to string! decompress compress long 'br 'br"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and something with no pattern in it, which is stored rather than coded")
        void somethingIncompressible() {
            assertThat(answerTo("""
                    seed: #{0102030405060708}
                    noise: copy #{}
                    loop 200 [seed: checksum seed 'sha256 append noise seed]
                    reduce [
                        noise = decompress compress noise 'br 'br
                        (length? compress noise 'br) - length? noise
                    ]""")).isEqualTo("[#(true) 4]");
        }

        @Test
        @DisplayName("one whose code over code lengths has a single symbol in it")
        void aCodeOverCodeLengthsWithOneSymbol() {
            assertThat(answerTo("""
                    data: make binary! 70000
                    h: checksum "seed" 'sha1
                    while [(length? data) < 70000][
                        append data h
                        append data "the quick brown fox jumps over the lazy dog "
                        h: checksum h 'sha1
                    ]
                    data: copy/part data 70000
                    sha: func [x][trim/all enbase checksum x 'sha1 64]
                    stream: compress/level data 'br 6
                    reduce [
                        sha data
                        length? stream
                        sha stream
                        data = decompress stream 'br
                    ]""")).isEqualTo("""
                    ["CMvWZqlA9EPf/FNp1QIGG9/K/eA=" 23177 \
                    "64A2TP3ihYDsd3SGEbJTZbenEYQ=" #(true)]""");
        }
    }

    @Nested
    @DisplayName("streams a real 3.22.5 wrote")
    class RealStreams {

        @Test
        @DisplayName("one from level eleven, which no encoder here can write")
        void oneFromTheSlowestLevel() {
            assertThat(answerTo("""
                    to string! decompress
                        #{1B0D00F8A541CAE6E8C42B51C036} 'br""")).isEqualTo("\"test test test\"");
        }

        @Test
        @DisplayName("one from level six, the level Rebol uses when nobody says")
        void oneFromTheDefaultLevel() {
            assertThat(answerTo("""
                    to string! decompress
                        #{1B0D0000A441CAE6E8C42B516C03} 'br""")).isEqualTo("\"test test test\"");
        }

        @Test
        @DisplayName("and one from level eleven of a whole sentence")
        void aSentenceFromTheSlowestLevel() {
            assertThat(answerTo("""
                    to string! decompress
                        #{1B2A00889C09364EA87737BC2433A34B9033BC427B4B90B2
                          3998C881435BA0F7DEA7150EE90B4789EA0C1BE0563506} 'br"""))
                    .isEqualTo("\"the quick brown fox jumps over the lazy dog\"");
        }

    }

    @Nested
    @DisplayName("the dictionary every decoder carries")
    class TheDictionary {

        @Test
        @DisplayName("is 122,784 bytes long")
        void isTheRightLength() {
            assertThat(BrotliDictionary.words())
                    .hasSize(BrotliDictionary.wordsLength());
        }

        @Test
        @DisplayName("opens with the shortest words, which are four letters")
        void opensWithFourLetterWords() {
            assertThat(new String(BrotliDictionary.wordAt(0, 24),
                    java.nio.charset.StandardCharsets.US_ASCII))
                    .isEqualTo("timedownlifeleftbackcode");
        }

        @Test
        @DisplayName("and each length starts where the table says it does")
        void eachLengthStartsWhereItShould() {
            assertThat(new String(
                    BrotliDictionary.wordAt(BrotliDictionary.offsetFor(5), 15),
                    java.nio.charset.StandardCharsets.US_ASCII))
                    .isEqualTo("firstvideolight");
        }

        @Test
        @DisplayName("and a transform hangs a prefix and a suffix on a word")
        void transformsAddPrefixesAndSuffixes() {
            assertThat(transformed(0)).isEqualTo("time");
            assertThat(transformed(1)).isEqualTo("time ");
            assertThat(transformed(7)).isEqualTo("s time ");
        }

        private static String transformed(int which) {
            byte[] into = new byte[64];
            int howMany = BrotliDictionary.writeTransformedWord(
                    into, 0, BrotliDictionary.offsetFor(4), 4, which);
            return new String(into, 0, howMany,
                    java.nio.charset.StandardCharsets.US_ASCII);
        }
    }

    @Nested
    @DisplayName("data that is not a Brotli stream")
    class Refusals {

        @Test
        @DisplayName("nothing at all is refused rather than read as nothing")
        void nothingIsRefused() {
            assertThat(answerTo("""
                    failure: try [decompress #{} 'br]
                    failure/id""")).isEqualTo("bad-press");
        }

        @Test
        @DisplayName("and bytes that end part way through a symbol are refused")
        void aTruncatedStreamIsRefused() {
            assertThat(answerTo("""
                    packed: compress "test test test" 'br
                    failure: try [decompress copy/part packed 6 'br]
                    failure/id""")).isEqualTo("bad-press");
        }
    }

    @Nested
    @DisplayName("where it sits among the methods")
    class AmongTheMethods {

        @Test
        @DisplayName("the catalogue lists it under the name Rebol uses")
        void theCatalogueListsIt() {
            assertThat(answerTo("""
                    misnamed: try [compress "x" 'brotli]
                    reduce [
                        true? find system/catalog/compressions 'br
                        not error? try [compress "x" 'br]
                        misnamed/id
                    ]""")).isEqualTo("[#(true) #(true) invalid-arg]");
        }

        @Test
        @DisplayName("and the two this build has not got still say so")
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
