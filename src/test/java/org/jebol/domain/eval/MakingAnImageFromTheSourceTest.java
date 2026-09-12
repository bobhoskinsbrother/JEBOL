package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

        @Test
        @DisplayName("a side too wide is a size limit on its own and malformed in a block")
        void aSideTooWideIsRefusedTwoDifferentWays() {
            assertThat(errorIdFrom("make image! 70000x1")).isEqualTo("size-limit");
            assertThat(errorIdFrom("make image! [70000x1]")).isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("and the widest side there is still builds")
        void theWidestSideThereIsStillBuilds() {
            assertThat(answerTo("r: make image! 65535x1  mold r/size"))
                    .isEqualTo("\"65535x1\"");
            assertThat(errorIdFrom("make image! 65536x1")).isEqualTo("size-limit");
        }

        @Test
        @DisplayName("and it is a script failure rather than an internal one")
        void itIsAScriptFailureRatherThanAnInternalOne() {
            assertThat(answerTo("""
                    e: try [make image! 70000x1] mold e/type""")).isEqualTo("\"Script\"");
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

        @Test
        @DisplayName("a number with no colours before it is not a position at all")
        void aNumberWithNoColoursBeforeItIsNotAPosition() {
            assertThat(errorIdFrom("make image! [2x2 0]")).isEqualTo("malconstruct");
            assertThat(errorIdFrom("make image! [2x2 3]")).isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("and the written form has no position slot either")
        void theWrittenFormHasNoPositionSlot() {
            assertThat(errorIdFrom("load {#(image! 2x2 3)}")).isEqualTo("malconstruct");
            assertThat(errorIdFrom("load {#(image! 2x2 0)}")).isEqualTo("malconstruct");
            assertThat(errorIdFrom("load {#(image! 2x2 1)}")).isEqualTo("malconstruct");
        }

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
