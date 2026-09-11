package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a specification has to look like before an image is built from it.
 *
 * <p>{@code Create_Image} in {@code t-image.c}, which both MAKE from a block
 * and the {@code #(image! ...)} written form go through. It reads the parts in
 * one fixed order -- a size, then the colours, then the alpha, then the
 * position -- and refuses the lot if anything is left over when it stops.
 *
 * <p>The refusals are the part worth having tests for. A size that cannot
 * exist and a position of nought are both caught, and they are caught with
 * different errors: the size is a malformed construct and the position is out
 * of range. JEBOL accepted every one of these, so a mistyped specification
 * built a picture that had quietly ignored half of what it was told.
 *
 * <p>Every expectation here was read off a real 3.22.5 first.
 */
class MakingAnImageFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("the size, which comes first because nothing reads without it")
    class TheSize {

        @Test
        @DisplayName("a negative height cannot be built")
        void aNegativeHeightCannotBeBuilt() {
            assertThat(errorIdFrom("make image! [1x-1]")).isEqualTo("malconstruct");
            assertThat(errorIdFrom("load {#(image! 1x-1)}")).isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("and neither can a negative width")
        void andNeitherCanANegativeWidth() {
            assertThat(errorIdFrom("make image! [-1x1]")).isEqualTo("malconstruct");
            assertThat(errorIdFrom("load {#(image! -1x1)}")).isEqualTo("malconstruct");
        }

        /**
         * Either dimension on its own is enough to refuse the pair, so the
         * check is on both rather than on the area.
         */
        @Test
        @DisplayName("one negative dimension is enough, even beside a nought")
        void oneNegativeDimensionIsEnough() {
            assertThat(errorIdFrom("make image! [0x-1]")).isEqualTo("malconstruct");
            assertThat(errorIdFrom("make image! [-1x0]")).isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("but nought is a size, and makes an empty picture")
        void noughtIsASize() {
            assertThat(answerTo("mold make image! [0x0]"))
                    .isEqualTo("\"make image! [0x0 #{}]\"");
            assertThat(answerTo("mold make image! [0x5]"))
                    .isEqualTo("\"make image! [0x5 #{}]\"");
        }

        /**
         * A bare pair does not come through here at all. It goes to the code
         * that makes a blank image of a size, which brings a negative
         * dimension down to nought instead of refusing it -- so the same size
         * written two ways gives two answers, and that is true of a real
         * 3.22.5 rather than a slip of the port.
         */
        @Test
        @DisplayName("and a bare pair clamps where a block refuses")
        void aBarePairClampsWhereABlockRefuses() {
            assertThat(answerTo("mold make image! -1x-1"))
                    .isEqualTo("\"make image! [0x0 #{}]\"");
        }

        @Test
        @DisplayName("what is not a pair at all is refused")
        void whatIsNotAPairIsRefused() {
            assertThat(errorIdFrom("load {#(image! x)}")).isEqualTo("malconstruct");
            assertThat(errorIdFrom("make image! [3]")).isEqualTo("malconstruct");
        }
    }

    @Nested
    @DisplayName("the position, counted from one like every other position")
    class ThePosition {

        @Test
        @DisplayName("nought is out of range, and so is anything below it")
        void noughtIsOutOfRange() {
            assertThat(errorIdFrom("make image! [1x1 #{FFFFFF} 0]"))
                    .isEqualTo("out-of-range");
            assertThat(errorIdFrom("make image! [1x1 #{FFFFFF} -1]"))
                    .isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("one is the head")
        void oneIsTheHead() {
            assertThat(answerTo("mold make image! [1x1 #{FFFFFF} 1]"))
                    .isEqualTo("\"make image! [1x1 #{FFFFFF}]\"");
        }

        /**
         * Past the end is not refused. The picture is still there and taking
         * the head gives it back; the image simply stands where a position
         * past the end stands on every other series, which is at the tail.
         */
        @Test
        @DisplayName("past the end stands at the tail, with the picture still behind it")
        void pastTheEndStandsAtTheTail() {
            assertThat(answerTo("mold make image! [1x1 #{FFFFFF} 2]"))
                    .isEqualTo("\"make image! [1x1 #{}]\"");
            assertThat(answerTo("mold make image! [1x1 #{FFFFFF} 3]"))
                    .isEqualTo("\"make image! [1x1 #{}]\"");
            assertThat(answerTo("""
                    i: make image! [2x2 #{FFFFFF} 9]
                    reduce [index? i tail? i length? i]"""))
                    .isEqualTo("[5 #(true) 0]");
            assertThat(answerTo("""
                    mold head make image! [2x2 #{FFFFFF} 9]"""))
                    .isEqualTo("""
                            "make image! [2x2 #{FFFFFFFFFFFFFFFFFFFFFFFF}]\"""");
        }
    }

    @Nested
    @DisplayName("what fills the picture")
    class TheFill {

        @Test
        @DisplayName("bytes are read three to a pixel, and too few leave the rest white")
        void bytesAreReadThreeToAPixel() {
            assertThat(answerTo("mold make image! [2x1 #{FFFFFF}]"))
                    .isEqualTo("\"make image! [2x1 #{FFFFFFFFFFFF}]\"");
            assertThat(answerTo("mold make image! [2x1 #{010203}]"))
                    .isEqualTo("\"make image! [2x1 #{010203FFFFFF}]\"");
        }

        @Test
        @DisplayName("and too many are ignored, because the size says how many are wanted")
        void tooManyAreIgnored() {
            assertThat(answerTo("mold make image! [1x1 #{FFFFFFAAAAAA}]"))
                    .isEqualTo("\"make image! [1x1 #{FFFFFF}]\"");
        }

        @Test
        @DisplayName("a second run of bytes is the alpha, padded opaque")
        void aSecondRunOfBytesIsTheAlpha() {
            assertThat(answerTo("mold make image! [2x1 #{FFFFFFEEEEEE} #{30}]"))
                    .isEqualTo("\"make image! [2x1 #{FFFFFFEEEEEE} #{30FF}]\"");
        }

        @Test
        @DisplayName("one colour paints the whole of it, and a number after it is the alpha")
        void oneColourPaintsTheWholeOfIt() {
            assertThat(answerTo("mold make image! [1x1 20.20.20 60]"))
                    .isEqualTo("\"make image! [1x1 #{141414} #{3C}]\"");
        }

        /**
         * A block of colours is read as a run of pixels and then refused
         * anyway. The reader that consumes it never steps past it, so the
         * leftover check fires on the block it has just used -- which makes
         * the branch unreachable, and makes bytes the only way to give an
         * image a list of colours.
         */
        @Test
        @DisplayName("and a block of colours is refused however well formed it is")
        void aBlockOfColoursIsRefused() {
            assertThat(errorIdFrom("make image! [2x1 [1.1.1 2.2.2]]"))
                    .isEqualTo("malconstruct");
            assertThat(errorIdFrom("make image! [2x1 []]")).isEqualTo("malconstruct");
            assertThat(errorIdFrom("load {#(image! 2x1 [1.1.1 2.2.2])}"))
                    .isEqualTo("malconstruct");
        }
    }

    @Nested
    @DisplayName("and anything left over refuses the whole thing")
    class WhatIsLeftOver {

        @Test
        @DisplayName("a word after the size")
        void aWordAfterTheSize() {
            assertThat(errorIdFrom("load {#(image! 1x1 x)}")).isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("a word after the bytes, the alpha or the colour")
        void aWordAfterTheParts() {
            assertThat(errorIdFrom("load {#(image! 1x1 #{FF} x)}"))
                    .isEqualTo("malconstruct");
            assertThat(errorIdFrom("load {#(image! 1x1 #{FFFFFF} #{30} x)}"))
                    .isEqualTo("malconstruct");
            assertThat(errorIdFrom("load {#(image! 1x1 20.20.20.60 x)}"))
                    .isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("and a second number where only one position belongs")
        void aSecondNumberWhereOnlyOnePositionBelongs() {
            assertThat(errorIdFrom("make image! [1x1 20.20.20 60 60]"))
                    .isEqualTo("malconstruct");
            assertThat(errorIdFrom(
                    "make image! [3x2 #{000000000000000000000000000000000000} 1x0]"))
                    .isEqualTo("malconstruct");
        }
    }
}
