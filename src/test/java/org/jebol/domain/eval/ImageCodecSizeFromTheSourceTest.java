package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code codecs/png/size?} and its four siblings, which read a picture's
 * dimensions out of the first few bytes without decoding the picture.
 *
 * <p>They are Rebol's own code, in {@code codec-image-ext.reb}, and JEBOL had
 * the file all along. It never did anything, because every one of its blocks
 * opens with {@code if find codecs 'png} and the load order put it before
 * {@code codec-image.reb} -- the file that registers png, jpeg, gif, bmp, dds
 * and tiff. So each guard was false, each {@code put} was skipped, and nothing
 * said a word.
 *
 * <p>That is the failure mode worth remembering: a borrowed file that loads
 * cleanly and does nothing looks exactly like a borrowed file that works.
 * {@code borrowedLoadFailures} was empty and the codecs simply had no
 * {@code size?}.
 *
 * <p>The bytes below are headers built by hand rather than real pictures, so
 * the test needs no files and no filesystem grant; each was checked against a
 * running 3.22.5 first, including the two answers that are plainly wrong and
 * are what Rebol says anyway.
 */
class ImageCodecSizeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("every image codec has one")
    class TheyAllHaveOne {

        @Test
        @DisplayName("png, jpeg, gif, bmp and dds each carry a size? of their own")
        void eachCarriesOne() {
            assertThat(answerTo("""
                    collect [
                        foreach name [png jpeg gif bmp dds][
                            keep true? function? select select system/codecs name 'size?
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("and png also gained the chunk reader from the same file")
        void pngAlsoGainedChunks() {
            assertThat(answerTo("""
                    true? function? select system/codecs/png 'chunks""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what each one reads")
    class WhatEachReads {

        @Test
        @DisplayName("PNG takes the two big-endian counts out of the IHDR chunk")
        void pngReadsTheIhdrChunk() {
            assertThat(answerTo("""
                    reduce [
                        codecs/png/size? #{89504E470D0A1A0A0000000D4948445200000018000000180802000000}
                        codecs/png/size? #{89504E470D0A1A0A0000000D49484452000001000000008008020000}
                    ]""")).isEqualTo("[24x24 256x128]");
        }

        @Test
        @DisplayName("GIF takes little-endian counts, and knows both of its version strings")
        void gifReadsLittleEndian() {
            assertThat(answerTo("""
                    reduce [
                        codecs/gif/size? #{47494638396140010F00}
                        codecs/gif/size? #{47494638376140010F00}
                    ]""")).isEqualTo("[320x15 320x15]");
        }

        @Test
        @DisplayName("JPEG walks the markers to the one that carries the size")
        void jpegWalksTheMarkers() {
            assertThat(answerTo("""
                    codecs/jpeg/size? #{FFD8FFC00011080100004002}""")).isEqualTo("64x256");
        }

        @Test
        @DisplayName("BMP skips eighteen bytes first, and reads them as signed")
        void bmpSkipsEighteenBytes() {
            assertThat(answerTo("""
                    codecs/bmp/size? #{424D0000000000000000000000000000000000004001000020000000}"""))
                    .isEqualTo("2.097152e7x2097152");
        }

        @Test
        @DisplayName("and DDS skips twelve and then reverses the pair")
        void ddsSkipsTwelveAndReverses() {
            assertThat(answerTo("""
                    codecs/dds/size? #{444453207C00000000000000000000000F0000004001000000000000}"""))
                    .isEqualTo("15x0");
        }
    }

    @Nested
    @DisplayName("bytes that are not that format at all")
    class TheWrongBytes {

        @Test
        @DisplayName("each answers none rather than raising")
        void eachAnswersNone() {
            assertThat(answerTo("""
                    collect [
                        foreach name [png jpeg gif bmp dds][
                            reader: select select system/codecs name 'size?
                            keep none? reader #{0102030405060708}
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("a PNG header with no chunk after it is none, not a guess")
        void aHeaderWithNoChunk() {
            assertThat(answerTo("""
                    reduce [
                        none? codecs/png/size? #{89504E470D0A1A0A}
                        none? codecs/png/size? #{}
                    ]""")).isEqualTo("[#(true) #(true)]");
        }
    }
}
