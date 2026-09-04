package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LZW, in the variant {@code u-lzw.c} carries: David Bryant's, with a
 * recycling dictionary and adjusted-binary codes.
 *
 * <p>Two things make it unlike the textbook algorithm and both change the
 * bytes, which is why the bytes are asserted here and not only the round trip.
 *
 * <p>The codes are written in adjusted binary. A dictionary holding 257
 * strings would normally spend nine bits on every code; here the codes below a
 * threshold spend eight and only those above it spend nine, and the threshold
 * moves as the dictionary grows. The width of a code therefore depends on how
 * many strings existed when it was written, and a reader counting differently
 * would read the rest of the stream wrong.
 *
 * <p>The dictionary is never simply cleared when it fills. Entries nothing
 * longer is built on are recycled one at a time, and the encoder starts over
 * only when too few remain or when a decaying average of the compression ratio
 * says it has stopped paying.
 *
 * <p>COMPRESS/LEVEL picks the widest symbol, and the mapping reads backwards
 * until you notice how it is written: levels one to seven give nine to fifteen
 * bits, and everything else -- including level zero, which is spelled as "less
 * than one" -- gives nine or sixteen. So the narrowest and the widest sit at
 * opposite ends with the ordinary range between them.
 */
class LzwFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("the bytes it writes")
    class TheBytes {

        @Test
        @DisplayName("nothing is three bytes: a width, an end code and the flush")
        void nothingIsThreeBytes() {
            assertThat(answerTo("""
                    compress/level "" 'lzw 0""")).isEqualTo("#{00FF01}");
        }

        @Test
        @DisplayName("something with a pattern in it, at the narrowest width")
        void somethingWithAPattern() {
            assertThat(answerTo("""
                    compress/level "test test test" 'lzw 0"""))
                    .isEqualTo("#{007465737420FDFAFBE347F71F}");
        }

        @Test
        @DisplayName("the first byte is the widest symbol less nine")
        void theFirstByteIsTheWidth() {
            assertThat(answerTo("""
                    collect [
                        foreach level [0 1 4 7 8 9][
                            keep first compress/level "test" 'lzw level
                        ]
                    ]""")).isEqualTo("[0 0 3 6 7 7]");
        }

        @Test
        @DisplayName("and the default is the widest, which is what no level means")
        void theDefaultIsTheWidest() {
            assertThat(answerTo("""
                    compress "test" 'lzw""")).isEqualTo("#{0774657374FF01}");
        }
    }

    @Nested
    @DisplayName("reading it back")
    class ReadingItBack {

        @Test
        @DisplayName("what went in comes out, at every level there is")
        void whatWentInComesOut() {
            assertThat(answerTo("""
                    text: {Lorem ipsum dolor sit amet, consectetur adipisici elit,
                    sed eiusmod tempor incidunt ut labore et dolore magna aliqua.}
                    collect [
                        repeat level 10 [
                            keep text = to string! decompress
                                compress/level text 'lzw (level - 1) 'lzw
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)"
                            + " #(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("something long enough to fill the dictionary and recycle it")
        void longEnoughToRecycle() {
            assertThat(answerTo("""
                    long: copy ""
                    repeat n 400 [append long "abcdefgh"]
                    long = to string! decompress compress/level long 'lzw 1 'lzw"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and something with no pattern, which makes it start over")
        void somethingWithNoPattern() {
            assertThat(answerTo("""
                    noise: copy #{}
                    repeat n 300 [append noise (n * 7919 % 251)]
                    noise = decompress compress noise 'lzw 'lzw""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/SIZE stops early, which is how the front is read on its own")
        void sizeStopsEarly() {
            assertThat(answerTo("""
                    packed: compress "test test test" 'lzw
                    decompress/size packed 'lzw 4""")).isEqualTo("#{74657374}");
        }

        @Test
        @DisplayName("it can be read from a position rather than from the head")
        void readFromAPosition() {
            assertThat(answerTo("""
                    data: "test test test"
                    data = to string! decompress
                        next join #{00} compress data 'lzw 'lzw""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and bytes that do not open with a width are refused")
        void bytesWithNoWidth() {
            assertThat(answerTo("""
                    failure: try [decompress #{FF} 'lzw]
                    failure/id""")).isEqualTo("bad-press");
            assertThat(answerTo("""
                    failure: try [decompress #{} 'lzw]
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
                        true? find system/catalog/compressions 'lzw
                        not error? try [compress "x" 'lzw]
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("and the four this build has not got still say so")
        void theOthersStillSayFeatureNa() {
            assertThat(answerTo("""
                    collect [
                        foreach method [br lz4 lzav lzma][
                            raised: try [compress "x" method]
                            keep raised/id
                        ]
                    ]""")).isEqualTo("[feature-na feature-na feature-na feature-na]");
        }
    }
}
