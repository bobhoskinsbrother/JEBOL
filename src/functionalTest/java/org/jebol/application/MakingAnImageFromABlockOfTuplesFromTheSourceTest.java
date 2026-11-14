package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MakingAnImageFromABlockOfTuplesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String pixelsOf(String expression) {
        return answerTo("i: " + expression + " reduce [i/size i/rgb i/alpha]");
    }

    private static String refusalOf(String expression) {
        return answerTo("e: try [" + expression + "] reduce [e/id e/arg1]");
    }

    @Nested
    @DisplayName("MAKE reads the block as one tuple a pixel")
    class Making {

        @Test
        @DisplayName("two tuples are two pixels, opaque")
        void makesTwoPixels() {
            assertThat(pixelsOf("make image! [2x1 [1.2.3 4.5.6]]"))
                    .isEqualTo("[2x1 #{010203040506} #{FFFF}]");
        }

        @Test
        @DisplayName("row by row, left to right")
        void fillsRowByRow() {
            assertThat(pixelsOf("make image! [1x2 [1.2.3 4.5.6]]"))
                    .isEqualTo("[1x2 #{010203040506} #{FFFF}]");
        }

        @Test
        @DisplayName("a fourth byte is transparency, stored as 255 less the byte")
        void readsTheFourthByteAsTransparency() {
            assertThat(pixelsOf("make image! [2x1 [1.2.3.0 4.5.6.255]]"))
                    .isEqualTo("[2x1 #{010203040506} #{FF00}]");
            assertThat(pixelsOf("make image! [1x1 [1.2.3.7]]"))
                    .isEqualTo("[1x1 #{010203} #{F8}]");
        }

        @Test
        @DisplayName("too few tuples leave the rest white and opaque")
        void leavesTheRestWhite() {
            assertThat(pixelsOf("make image! [2x1 [1.2.3]]"))
                    .isEqualTo("[2x1 #{010203FFFFFF} #{FFFF}]");
        }

        @Test
        @DisplayName("too many tuples are ignored past the last pixel")
        void ignoresTheSurplus() {
            assertThat(pixelsOf("make image! [2x1 [1.2.3 4.5.6 7.8.9]]"))
                    .isEqualTo("[2x1 #{010203040506} #{FFFF}]");
        }

        @Test
        @DisplayName("an empty block is the image a bare size makes")
        void readsAnEmptyBlock() {
            assertThat(pixelsOf("make image! [2x1 []]"))
                    .isEqualTo("[2x1 #{FFFFFFFFFFFF} #{FFFF}]");
        }

        @Test
        @DisplayName("an image of no pixels takes an empty block")
        void makesAnImageOfNoPixels() {
            assertThat(pixelsOf("make image! [0x0 []]")).isEqualTo("[0x0 #{} #{}]");
        }

        @Test
        @DisplayName("the block is read from where it stands")
        void readsFromWhereTheBlockStands() {
            assertThat(pixelsOf("make image! compose/only [2x1 (next [1.2.3 4.5.6 7.8.9])]"))
                    .isEqualTo("[2x1 #{040506070809} #{FFFF}]");
        }

        @Test
        @DisplayName("the image stands at its head")
        void standsAtItsHead() {
            assertThat(answerTo("index? make image! [2x1 [1.2.3 4.5.6]]")).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("MAKE refuses what is not a block of tuples")
    class Refusing {

        @ParameterizedTest(name = "{0} names {1}")
        @CsvSource(delimiter = '|', value = {
                "make image! [2x1 [1.2.3 x]] | x",
                "make image! [2x1 [1.2.3 7]] | 7",
                "make image! [2x1 [none]] | none",
                "make image! [2x1 [1.2.3 4.5.6 a/b]] | a/b"})
        @DisplayName("a value that is not a tuple is invalid-arg naming that value")
        void namesTheValue(String expression, String named) {
            assertThat(refusalOf(expression)).isEqualTo("[invalid-arg " + named + "]");
        }

        @Test
        @DisplayName("anything after the block is still left over")
        void refusesWhatIsLeftOver() {
            assertThat(refusalOf("make image! [2x1 [1.2.3 4.5.6] 0]"))
                    .isEqualTo("[malconstruct [2x1 [1.2.3 4.5.6] 0]]");
        }
    }

    @Nested
    @DisplayName("a fill and a pixel keep the fourth byte as alpha")
    class OneTupleAtATime {

        @Test
        @DisplayName("a fill of one four-byte colour")
        void fillsWithAlpha() {
            assertThat(pixelsOf("make image! [2x1 1.2.3.4]"))
                    .isEqualTo("[2x1 #{010203010203} #{0404}]");
        }

        @Test
        @DisplayName("a pixel set by its position")
        void setsAPixelWithAlpha() {
            assertThat(pixelsOf("make image! 1x1 i/1: 1.2.3.4 i"))
                    .isEqualTo("[1x1 #{010203} #{04}]");
        }

        @Test
        @DisplayName("a pixel changed by one tuple")
        void changesWithAlpha() {
            assertThat(pixelsOf("head change make image! [2x1 0.0.0] 1.2.3.4"))
                    .isEqualTo("[2x1 #{010203000000} #{04FF}]");
        }
    }

    @Nested
    @DisplayName("APPEND, INSERT and CHANGE of a block read the fourth byte as transparency")
    class WritingABlock {

        @Test
        @DisplayName("APPEND gives alpha 248 for a fourth byte of 7")
        void appends() {
            assertThat(pixelsOf("append make image! [1x1 0.0.0] [1.2.3 4.5.6.7]"))
                    .isEqualTo("[1x3 #{000000010203040506} #{FFFFF8}]");
        }

        @Test
        @DisplayName("CHANGE does the same")
        void changes() {
            assertThat(pixelsOf("head change make image! [2x1 0.0.0] [1.2.3 4.5.6.7]"))
                    .isEqualTo("[2x1 #{010203040506} #{FFF8}]");
        }

        @Test
        @DisplayName("INSERT does the same")
        void inserts() {
            assertThat(pixelsOf("head insert make image! [2x1 0.0.0] [1.2.3.4]"))
                    .isEqualTo("[2x1 #{010203000000000000} #{FBFFFF}]");
        }

        @Test
        @DisplayName("CHANGE/ONLY writes the alpha anyway")
        void writesTheAlphaUnderOnly() {
            assertThat(pixelsOf("head change/only make image! [2x1 0.0.0] [1.2.3.4 5.6.7]"))
                    .isEqualTo("[2x1 #{010203050607} #{FBFF}]");
        }

        @Test
        @DisplayName("CHANGE/DUP repeats the block")
        void repeatsUnderDup() {
            assertThat(pixelsOf("head change/dup make image! [2x1 0.0.0] [1.2.3.4] 2"))
                    .isEqualTo("[2x1 #{010203010203} #{FBFB}]");
        }

        @ParameterizedTest(name = "{0} names {1}")
        @CsvSource(delimiter = '|', value = {
                "append make image! 1x1 [1.2.3 x] | x",
                "append make image! 1x1 [1.2.3 7] | 7",
                "change make image! 1x1 [x] | x",
                "insert make image! 1x1 [1.2.3 none] | none"})
        @DisplayName("a block holding anything but tuples is invalid-arg naming that value")
        void namesTheValue(String expression, String named) {
            assertThat(refusalOf(expression)).isEqualTo("[invalid-arg " + named + "]");
        }

        @Test
        @DisplayName("and the image is left as it was")
        void leavesTheImageAlone() {
            assertThat(pixelsOf("make image! [1x1 1.2.3] try [append i [4.5.6 x]] i"))
                    .isEqualTo("[1x1 #{010203} #{FF}]");
        }
    }
}
