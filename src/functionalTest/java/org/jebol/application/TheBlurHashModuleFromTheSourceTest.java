package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheBlurHashModuleFromTheSourceTest {

    private static final String FOUR_COLOURS = """
            b: import 'blurhash
            four: make image! [2x2 #{FF0000 00FF00 0000FF FFFFFF}]
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("importing it")
    class Importing {

        @Test
        @DisplayName("answers the module, which the table then holds")
        void answersTheModule() {
            assertThat(answerTo("""
                    before: type? select system/modules 'blurhash
                    b: import 'blurhash
                    reduce [before module? b same? b system/modules/blurhash]""")).isEqualTo("""
                    [#(block!) #(true) #(true)]""");
        }

        @Test
        @DisplayName("gives the module its two commands and exports neither, so lib's ENCODE and DECODE stay")
        void exportsNothing() {
            assertThat(answerTo("""
                    b: import 'blurhash
                    reduce [words-of b same? :lib/encode :b/encode same? :lib/decode :b/decode same? :encode :lib/encode]""")).isEqualTo("""
                    [[lib-base lib-file lib-local words encode decode] #(false) #(false) #(true)]""");
        }

        @Test
        @DisplayName("carries the extension's own header and specs")
        void carriesTheSpecs() {
            assertThat(answerTo("""
                    b: import 'blurhash
                    reduce [select spec-of b 'title spec-of :b/encode]""")).isEqualTo("""
                    ["Rebol BlurHash Extension" ["Encode an image using BlurHash algorithm" img [image!] "Source image"]]""");
        }
    }

    @Nested
    @DisplayName("ENCODE")
    class Encode {

        @Test
        @DisplayName("writes the hash the extension's C writes for the same pixels in R3's memory")
        void writesTheCsHash() {
            assertThat(answerTo(FOUR_COLOURS + """
                    reduce [b/encode four]""")).isEqualTo("""
                    ["K~Lqe9|ldLhefZdU2E2NfZ"]""");
        }

        @Test
        @DisplayName("reads red as blue, because R3 keeps a pixel's bytes blue first and the C reads them red first")
        void readsRedAsBlue() {
            assertThat(answerTo("""
                    b: import 'blurhash
                    reduce [b/encode make image! [1x1 #{FF0000}] b/encode make image! [1x1 #{0000FF}]]""")).isEqualTo("""
                    ["K~0036fZfZfZfZfZfZfZfZ" "K~TI:j|c|c|c|c|c|c|c|c"]""");
        }

        @Test
        @DisplayName("encodes an empty image, as the C does")
        void encodesAnEmptyImage() {
            assertThat(answerTo("""
                    b: import 'blurhash
                    reduce [b/encode make image! 0x0]""")).isEqualTo("""
                    ["K0TSUA~q~q~q~q~q~q~q~q"]""");
        }

        @Test
        @DisplayName("reads the whole image from its head, wherever it stands")
        void readsFromTheHead() {
            assertThat(answerTo(FOUR_COLOURS + """
                    equal? b/encode four b/encode skip four 3""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("DECODE")
    class Decode {

        @Test
        @DisplayName("paints a new image of the size asked for, with the pixels the C paints")
        void paintsANewImage() {
            assertThat(answerTo("""
                    b: import 'blurhash
                    d: b/decode {K~6n~Zc7bqYoc8f*YDg1fk} 3x2
                    reduce [d/size d/rgb d/alpha]""")).isEqualTo("""
                    [3x2 #{00FF0000FF0000F303A77241B33C48B8004C} #{FFFFFFFFFFFF}]""");
        }

        @Test
        @DisplayName("writes blue where the C writes red, so a hash R3 wrote reads back the right way round")
        void writesRedIntoBlue() {
            assertThat(answerTo("""
                    b: import 'blurhash
                    standard: b/decode {K~TI:j|c|c|c|c|c|c|c|c} 1x1
                    round-trip: b/decode b/encode make image! [1x1 #{FF0000}] 1x1
                    reduce [standard/rgb round-trip/rgb]""")).isEqualTo("""
                    [#{0000FF} #{FF0000}]""");
        }

        @Test
        @DisplayName("paints into an image it is given and answers that image at its head")
        void paintsIntoAGivenImage() {
            assertThat(answerTo(FOUR_COLOURS + """
                    into: make image! 2x1
                    answered: b/decode b/encode four next into
                    reduce [same? answered into index? answered into/rgb]""")).isEqualTo("""
                    [#(true) 1 #{FF0A0A0AFF0A}]""");
        }

        @Test
        @DisplayName("rounds a pair to the nearest whole size")
        void roundsAPair() {
            assertThat(answerTo(FOUR_COLOURS + """
                    d: b/decode b/encode four 1.4x1.5
                    reduce [d/size d/rgb]""")).isEqualTo("""
                    [1x2 #{FF0A0A0A0AFF}]""");
        }

        @Test
        @DisplayName("lifts the contrast by its punch, and treats a punch below one as one")
        void appliesThePunch() {
            assertThat(answerTo(FOUR_COLOURS + """
                    h: b/encode four
                    three: b/decode/contrast h 1x1 3
                    none-at-all: b/decode/contrast h 1x1 0
                    plain: b/decode h 1x1
                    reduce [three/rgb none-at-all/rgb plain/rgb]""")).isEqualTo("""
                    [#{FF0000} #{FF0A0A} #{FF0A0A}]""");
        }

        @Test
        @DisplayName("reads text, a binary, and any string, always from the head")
        void readsFromTheHead() {
            assertThat(answerTo(FOUR_COLOURS + """
                    h: b/encode four
                    from-binary: b/decode to binary! h 1x1
                    from-tag: b/decode to tag! h 1x1
                    from-next: b/decode next h 1x1
                    reduce [from-binary/rgb from-tag/rgb from-next/rgb]""")).isEqualTo("""
                    [#{FF0A0A} #{FF0A0A} #{FF0A0A}]""");
        }

        @Test
        @DisplayName("answers none for a hash that does not read, leaving a given image as it was")
        void answersNoneForABadHash() {
            assertThat(answerTo("""
                    b: import 'blurhash
                    into: make image! [1x1 #{102030}]
                    reduce [b/decode {bad} 2x2 b/decode {LEHV6nWB2yk8pyo0adR*.7kCMdnj!} into into/rgb]""")).isEqualTo("""
                    [_ _ #{102030}]""");
        }

        @Test
        @DisplayName("answers none for text with a character past 255, which R3 stores two bytes wide")
        void answersNoneForWideText() {
            assertThat(answerTo(FOUR_COLOURS + """
                    b/decode append b/encode four {€} 1x1""")).isEqualTo("_");
        }

        @Test
        @DisplayName("stops reading a binary at its first zero byte")
        void stopsAtAZeroByte() {
            assertThat(answerTo(FOUR_COLOURS + """
                    h: to binary! b/encode four
                    reduce [image? b/decode append copy h #{00FF} 1x1 b/decode head insert copy h #{00} 1x1]""")).isEqualTo("""
                    [#(true) _]""");
        }

        @Test
        @DisplayName("paints an empty image when asked for no size at all")
        void paintsAnEmptyImage() {
            assertThat(answerTo(FOUR_COLOURS + """
                    d: b/decode b/encode four 0x0
                    d/size""")).isEqualTo("0x0");
        }

        @Test
        @DisplayName("refuses a side past 65535, or below zero, as size-limit where R3 crashes")
        void refusesASideTooLong() {
            assertThat(answerTo(FOUR_COLOURS + """
                    h: b/encode four
                    collect [foreach size [70000x1 1x65536 -1x1] [e: try [b/decode h size] keep e/id keep e/arg1]]""")).isEqualTo("""
                    [size-limit image! size-limit image! size-limit image!]""");
        }
    }
}
