package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUSH, which is Rebol's own compressor rather than a standard one.
 *
 * <p>{@code u-crush.c}, ported from Ilya Muravyov's public-domain original:
 * LZ77 with a bit code of its own, a length in one of six brackets and a
 * distance in one of sixteen slots, packed least significant bit first. The
 * first four bytes are the uncompressed length, little endian, which is how
 * DECOMPRESS knows how much to make before it starts.
 *
 * <p>The bytes are asserted and not just the round trip, because a compressor
 * that reads its own output back is not thereby the same compressor. Rebol
 * builds this one with the constants Red uses rather than the ones the
 * original shipped with -- a smaller window and smaller hash tables -- and a
 * port that took the originals would round-trip perfectly and share not one
 * byte with a real 3.22.5.
 */
class CrushFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("the bytes it writes")
    class TheBytes {

        @Test
        @DisplayName("nothing is its own length and no more")
        void nothingIsFourBytes() {
            assertThat(answerTo("""
                    compress/level "" 'crush 0""")).isEqualTo("#{00000000}");
        }

        @Test
        @DisplayName("something with a pattern in it, at the lowest level")
        void somethingWithAPattern() {
            assertThat(answerTo("""
                    compress/level "test test test" 'crush 0"""))
                    .isEqualTo("#{0E000000E894994307A40201}");
        }

        @Test
        @DisplayName("and text with no pattern comes out longer than it went in")
        void noPatternComesOutLonger() {
            assertThat(answerTo("""
                    reduce [
                        compress "1234" 'crush
                        compress "12345678" 'crush
                        compress "123456789ABCD" 'crush
                    ]""")).isEqualTo("[#{0400000062C8984103}"
                            + " #{0800000062C89841A3868D1B38}"
                            + " #{0D00000062C89841A3868D1B38720411328408}]");
        }

        @Test
        @DisplayName("the first four bytes are the length, little endian")
        void theFirstFourBytesAreTheLength() {
            assertThat(answerTo("""
                    copy/part compress "123456789ABCDEFGH" 'crush 4"""))
                    .isEqualTo("#{11000000}");
        }
    }

    @Nested
    @DisplayName("reading it back")
    class ReadingItBack {

        @Test
        @DisplayName("what went in comes out, at every level")
        void whatWentInComesOut() {
            assertThat(answerTo("""
                    text: {Lorem ipsum dolor sit amet, consectetur adipisici elit,
                    sed eiusmod tempor incidunt ut labore et dolore magna aliqua.}
                    collect [
                        repeat level 3 [
                            keep text = to string! decompress
                                compress/level text 'crush (level - 1) 'crush
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("and nothing comes back as nothing")
        void nothingComesBackAsNothing() {
            assertThat(answerTo("""
                    decompress compress "" 'crush 'crush""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("/SIZE stops early, which is how the front is read on its own")
        void sizeStopsEarly() {
            assertThat(answerTo("""
                    packed: compress "test test test" 'crush
                    decompress/size packed 'crush 4""")).isEqualTo("#{74657374}");
        }

        @Test
        @DisplayName("it can be read from a position rather than from the head")
        void readFromAPosition() {
            assertThat(answerTo("""
                    data: "test test test"
                    data = to string! decompress
                        next join #{00} compress data 'crush 'crush"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and bytes too short to hold a length are refused")
        void tooShortToHoldALength() {
            assertThat(answerTo("""
                    failure: try [decompress #{0102} 'crush]
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
                        true? find system/catalog/compressions 'crush
                        not error? try [compress "x" 'crush]
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("and the five this build has not got still say so")
        void theOthersStillSayFeatureNa() {
            assertThat(answerTo("""
                    collect [
                        foreach method [br lz4 lzav lzma lzw][
                            raised: try [compress "x" method]
                            keep raised/id
                        ]
                    ]""")).isEqualTo("[feature-na feature-na feature-na"
                            + " feature-na feature-na]");
        }
    }
}
