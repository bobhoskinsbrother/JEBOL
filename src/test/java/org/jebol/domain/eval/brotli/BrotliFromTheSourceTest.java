package org.jebol.domain.eval.brotli;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brotli, ported from the reference library Rebol vendors under
 * {@code src/core/brotli/}.
 *
 * <p>The reading half is RFC 7932 in full: block switching, the four ways a
 * literal can be conditioned on the two bytes before it, and the hundred and
 * twenty thousand bytes of dictionary that a distance past the start of the
 * answer names a word in. It reads every stream a real 3.22.5 writes at every
 * one of its twelve levels, which was checked by handing it a hundred and
 * thirty-two of them.
 *
 * <p>The writing half has all twelve of its settings, and every one of them is
 * byte for byte what a real 3.22.5 writes. Levels two to nine and levels ten
 * and eleven have test files of their own beside this one; what is here is
 * levels zero and one, the reader, the dictionary, and the refusals.
 *
 * <p>One fault this file would have caught and a round trip would not: each of
 * the two files that build a prefix code has a static comparator of its own,
 * and they differ by a line. The one that sorts the literal code does not
 * break a tie between two symbols of equal count; the one that sorts the
 * command code does. Using the wrong one gives a code of exactly the same
 * shape with two symbols swapped -- valid Brotli, decodes perfectly, and not
 * the bytes a real one writes.
 */
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

        /**
         * Six bits: a window size of four megabytes, then "this is the last
         * meta-block" and "it is empty". The other two bits of the byte are
         * padding.
         */
        @Test
        @DisplayName("nothing is one byte, and that byte is 3B")
        void nothingIsOneByte() {
            assertThat(answerTo("""
                    compress/level "" 'br 0""")).isEqualTo("#{3B}");
        }

        /**
         * Fourteen bytes are not worth a prefix code, so they go down as they
         * stand: a header saying so, then the bytes, then an empty last
         * meta-block.
         */
        @Test
        @DisplayName("and fourteen bytes are stored, not compressed")
        void fourteenBytesAreStored() {
            assertThat(answerTo("""
                    compress/level "test test test" 'br 0"""))
                    .isEqualTo("#{8B0680746573742074657374207465737403}");
        }

        /**
         * Level one reads the input twice: once to find the matches, once to
         * build prefix codes from what they actually left behind. On fourteen
         * bytes it finds nothing either way, so both settings store them.
         */
        @Test
        @DisplayName("level one is a different encoder, and on this input agrees")
        void levelOneIsADifferentEncoder() {
            assertThat(answerTo("""
                    (compress/level "test test test" 'br 1)
                        = compress/level "test test test" 'br 0"""))
                    .isEqualTo("#(true)");
        }

        /**
         * On enough input the two part company, and both are what a real
         * 3.22.5 writes at that level.
         */
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

        /**
         * The level nobody asks for is six, and six now writes what a real one
         * writes. Fourteen bytes are too few for level two to bother building a
         * code for, so it stores them where six does not.
         */
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

        /**
         * The two levels that were the last gap. They search for matches by
         * pricing every candidate rather than taking the best one found, so
         * they part company with nine even on fourteen bytes -- and they are
         * now the bytes a real 3.22.5 writes.
         */
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

        /**
         * A real 3.22.5 answers these same bytes. They were taken from
         * {@code ./r3-head} and not from this build, which is the only way the
         * assertion means anything.
         */
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

        /**
         * A stream whose table of code lengths names exactly one length.
         *
         * <p>That code has a single symbol in it, so there is nothing to tell
         * apart and it is read by spending no bits at all. Reading it as an
         * ordinary prefix code instead spends one bit or more, and everything
         * after it is then read at the wrong offset -- which showed up as a
         * symbol longer than any code rather than as wrong output, so the
         * damage was at least loud.
         *
         * <p>It took seventy thousand bytes to find one. Every shorter stream
         * this file had been tested on -- a hundred and thirty two of them,
         * written by a real 3.22.5 at all twelve levels -- reads correctly
         * without the special case, which is why counting streams was no
         * substitute for asking what shapes of stream exist.
         *
         * <p>The stream this decodes is a real 3.22.5's byte for byte: its
         * checksum is asserted here, and that the encoder writes a real one's
         * bytes at level six is asserted separately.
         */
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

    /**
     * The streams here were written by {@code ./r3-head} and pasted in. They
     * are the point of the exercise: a decoder that only ever reads what this
     * build wrote would agree with itself about a format it had misread.
     */
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

    /**
     * The dictionary is a hundred and twenty thousand bytes of data carried in
     * the source, and a mistake in it would show up as a wrong answer on some
     * stream nobody happened to try. These check the bytes themselves.
     */
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

        /**
         * Transform zero is {@code {49, IDENTITY, 49}} and piece 49 is the
         * empty one, so it adds nothing. Transform one hangs a space on the
         * end and transform seven puts "s " in front of that, which is how one
         * dictionary word covers "time", "time " and "s time ".
         */
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
