package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AS-COLOR and the colour functions of {@code n-image.c}.
 *
 * <p>A colour is a tuple and an image is a series of them, so most of these have
 * two arms over one formula: given a tuple they answer or change that tuple, and
 * given an image they do the same to every pixel from the position on. The
 * formulas are the C's arithmetic exactly, because a colour that is one off is a
 * colour that fails a comparison and nothing else.
 *
 * <p>"(modified)" in each doc string means two different things, and where the
 * bytes live is what decides which. An image is a series: {@code VAL_IMAGE_DATA}
 * points into shared storage, so tinting one changes the image every other value
 * of it can see. A tuple is inline in the REBVAL, so what the native writes
 * through {@code D_ARG(1)} is the copy on the data stack, and {@code return
 * R_ARG1} hands that copy back -- the caller's own word keeps the colour it had.
 * So `c: tint c ...` is how a script keeps the result of tinting a colour, and
 * `tint img ...` needs no such thing.
 *
 * <p>And HSV lives in the same three bytes as RGB, so a hue is a byte rather than
 * a degree: {@code h / (255.0 / 6)}, with the C's own comment saying "255 because
 * we have just one byte! Else it should be 360!".
 */
class ColourFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("AS-COLOR, which reads its three parts three ways")
    class MakingAColour {

        @Test
        @DisplayName("three integers are three bytes")
        void threeIntegers() {
            assertThat(answerTo("as-color 255 128 0")).isEqualTo("255.128.0");
        }

        @Test
        @DisplayName("a decimal is rounded rather than truncated")
        void aDecimalRounds() {
            assertThat(answerTo("as-color 1.4 1.5 1.6")).isEqualTo("1.2.2");
        }

        @Test
        @DisplayName("and a percent is a fraction of 255")
        void aPercentScales() {
            assertThat(answerTo("as-color 100% 50% 0%")).isEqualTo("255.128.0");
        }

        @Test
        @DisplayName("out of range is clamped at both ends")
        void clampedBothWays() {
            assertThat(answerTo("as-color 300 -20 255")).isEqualTo("255.0.255");
        }

        @Test
        @DisplayName("and nothing else is a colour part")
        void anythingElseIsRefused() {
            assertThat(errorIdFrom("as-color \"255\" 0 0")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("as-color 255 0 none")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("GRAYSCALE and LUMINOSITY, which differ in their weights")
    class TurningGrey {

        @Test
        @DisplayName("GRAYSCALE averages the three parts")
        void grayscaleAverages() {
            assertThat(answerTo("grayscale 30.60.90")).isEqualTo("60");
            assertThat(answerTo("grayscale 255.255.254")).isEqualTo("254");
        }

        @Test
        @DisplayName("LUMINOSITY weights them as BT.709")
        void luminosityWeights() {
            assertThat(answerTo("luminosity 255.0.0")).isEqualTo("54");
            assertThat(answerTo("luminosity 0.255.0")).isEqualTo("182");
            assertThat(answerTo("luminosity 0.0.255")).isEqualTo("18");
        }

        @Test
        @DisplayName("and /LUMA weights them as BT.601 instead")
        void lumaWeights() {
            assertThat(answerTo("luminosity/luma 255.0.0")).isEqualTo("76");
            assertThat(answerTo("luminosity/luma 0.255.0")).isEqualTo("149");
            assertThat(answerTo("luminosity/luma 0.0.255")).isEqualTo("29");
        }

        @Test
        @DisplayName("an image is turned grey in place, and answered")
        void anImageIsChangedInPlace() {
            assertThat(answerTo(
                    "img: make image! [1x1 #{1E3C5A}] grayscale img img/1"))
                    .isEqualTo("60.60.60.255");
            assertThat(answerTo(
                    "img: make image! [1x1 #{FF0000}] same? img grayscale img"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and only from where it stands")
        void onlyFromThePosition() {
            assertThat(answerTo(
                    "img: make image! [2x1 #{1E3C5A FF0000}] grayscale skip img 1 img/1"))
                    .isEqualTo("30.60.90.255");
        }
    }

    @Nested
    @DisplayName("RGB and HSV, in the same three bytes")
    class Converting {

        @Test
        @DisplayName("a colour with no saturation is a grey, and converts to one")
        void theAchromaticCase() {
            assertThat(answerTo("hsv-to-rgb 40.0.200")).isEqualTo("200.200.200");
        }

        @Test
        @DisplayName("and RGB to HSV answers zeros for a grey")
        void greyHasNoHue() {
            assertThat(answerTo("rgb-to-hsv 200.200.200")).isEqualTo("0.0.200");
            assertThat(answerTo("rgb-to-hsv 0.0.0")).isEqualTo("0.0.0");
        }

        @Test
        @DisplayName("the value is the largest part and the saturation follows the spread")
        void valueAndSaturation() {
            assertThat(answerTo("third rgb-to-hsv 255.0.0")).isEqualTo("255");
            assertThat(answerTo("second rgb-to-hsv 255.0.0")).isEqualTo("255");
            assertThat(answerTo("second rgb-to-hsv 255.128.128")).isEqualTo("127");
        }

        @Test
        @DisplayName("and the two are inverses for a colour that survives the rounding")
        void theRoundTrip() {
            assertThat(answerTo("hsv-to-rgb rgb-to-hsv 255.0.0")).isEqualTo("255.0.0");
            assertThat(answerTo("hsv-to-rgb rgb-to-hsv 0.255.0")).isEqualTo("0.255.0");
            assertThat(answerTo("hsv-to-rgb rgb-to-hsv 0.0.255")).isEqualTo("0.0.255");
        }

        @Test
        @DisplayName("and the answer carries the change, while the caller's word does not")
        void theChangeIsInTheAnswer() {
            assertThat(answerTo("c: 255.0.0 rgb-to-hsv c")).isEqualTo("0.255.255");
            assertThat(answerTo("c: 255.0.0 rgb-to-hsv c c")).isEqualTo("255.0.0");
        }
    }

    @Nested
    @DisplayName("COLOR-DISTANCE, which is weighted rather than plain")
    class Distance {

        @Test
        @DisplayName("a colour is no distance from itself")
        void zeroFromItself() {
            assertThat(answerTo("color-distance 12.34.56 12.34.56")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("and the weights make green count more than blue")
        void greenCountsMost() {
            assertThat(answerTo(
                    "(color-distance 0.0.0 0.255.0) > (color-distance 0.0.0 0.0.255)"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and it answers a decimal")
        void itAnswersADecimal() {
            assertThat(answerTo("decimal? color-distance 0.0.0 255.255.255"))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("TINT, which mixes towards a colour")
    class Tinting {

        @Test
        @DisplayName("no amount leaves the target where it was")
        void noneOfIt() {
            assertThat(answerTo("tint 10.20.30 200.200.200 0")).isEqualTo("10.20.30");
        }

        @Test
        @DisplayName("all of it takes the mixture's colour")
        void allOfIt() {
            assertThat(answerTo("tint 10.20.30 200.100.50 1")).isEqualTo("200.100.50");
        }

        @Test
        @DisplayName("and the amount is clipped to nothing and everything")
        void theAmountIsClipped() {
            assertThat(answerTo("tint 10.20.30 200.100.50 5")).isEqualTo("200.100.50");
            assertThat(answerTo("tint 10.20.30 200.100.50 -5")).isEqualTo("10.20.30");
        }

        @Test
        @DisplayName("halfway is halfway, rounded")
        void halfway() {
            assertThat(answerTo("tint 0.0.0 255.255.255 0.5")).isEqualTo("128.128.128");
        }

        @Test
        @DisplayName("and an image is tinted pixel by pixel, in place")
        void anImageIsTinted() {
            assertThat(answerTo(
                    "img: make image! [1x1 #{000000}] tint img 255.255.255 1 img/1"))
                    .isEqualTo("255.255.255.255");
        }
    }
}
