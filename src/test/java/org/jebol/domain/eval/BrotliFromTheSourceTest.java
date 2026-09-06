package org.jebol.domain.eval;

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
 * <p>The writing half is level zero, and only level zero. What it writes is
 * byte for byte what a real one writes at that level -- checked on nineteen
 * inputs from nothing to seven hundred kilobytes, compressible and not -- and
 * what this build writes at every other level is the same thing. That is a
 * real difference from a real 3.22.5 and it is named here rather than left to
 * be discovered: the eleven other levels are eleven other encoders, and
 * porting them is a far larger job than reading any of them.
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
         * This is the difference from a real 3.22.5, stated rather than left
         * to be found. There, no level means level six and the fourteen bytes
         * come out as {@code 1B0D0000A441CAE6E8C42B516C03}; here every level
         * is level zero, so they come out stored. Both are Brotli and either
         * side reads the other.
         */
        @Test
        @DisplayName("and every other level answers those same bytes, which a real one does not")
        void everyLevelAnswersTheLevelZeroBytes() {
            assertThat(answerTo("""
                    reduce [
                        (compress "test test test" 'br)
                            = compress/level "test test test" 'br 0
                        (compress/level "test test test" 'br 11)
                            = compress/level "test test test" 'br 0
                        (compress "test test test" 'br)
                            = #{1B0D0000A441CAE6E8C42B516C03}
                    ]""")).isEqualTo("[#(true) #(true) #(false)]");
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
