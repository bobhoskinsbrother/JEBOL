package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LzwFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("the bytes it writes")
    class TheBytes {

        @Test
        @DisplayName("nothing is three bytes: a width, an end code and the flush")
        void compressNothingToThreeBytes() {
            assertThat(answerTo("""
                    compress/level "" 'lzw 0""")).isEqualTo("#{00FF01}");
        }

        @Test
        @DisplayName("something with a pattern in it, at the narrowest width")
        void compressSomethingWithAPattern() {
            assertThat(answerTo("""
                    compress/level "test test test" 'lzw 0"""))
                    .isEqualTo("#{007465737420FDFAFBE347F71F}");
        }

        @Test
        @DisplayName("the first byte is the widest symbol less nine")
        void startWithTheWidth() {
            assertThat(answerTo("""
                    collect [
                        foreach level [0 1 4 7 8 9][
                            keep first compress/level "test" 'lzw level
                        ]
                    ]""")).isEqualTo("[0 0 3 6 7 7]");
        }

        @Test
        @DisplayName("and the default is the widest, which is what no level means")
        void defaultToTheWidest() {
            assertThat(answerTo("""
                    compress "test" 'lzw""")).isEqualTo("#{0774657374FF01}");
        }
    }

    @Nested
    @DisplayName("reading it back")
    class ReadingItBack {

        @Test
        @DisplayName("what went in comes out, at every level there is")
        void bringsOutWhatGoesIn() {
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
        void readsSomethingWithNoPattern() {
            assertThat(answerTo("""
                    noise: copy #{}
                    repeat n 300 [append noise (n * 7919 % 251)]
                    noise = decompress compress noise 'lzw 'lzw""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/SIZE stops early, which is how the front is read on its own")
        void stopsEarlyForSize() {
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
        void refusesBytesWithNoWidth() {
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
        void appearsInTheCatalogue() {
            assertThat(answerTo("""
                    reduce [
                        true? find system/catalog/compressions 'lzw
                        not error? try [compress "x" 'lzw]
                    ]""")).isEqualTo("[#(true) #(true)]");
        }
    }
}
