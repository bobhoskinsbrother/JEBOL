package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

        @Test
        @DisplayName("the fourth part is an alpha, and both conversions hand it back")
        void theAlphaIsHandedBack() {
            assertThat(answerTo("rgb-to-hsv 134.116.10.100"))
                    .isEqualTo("36.235.134.100");
            assertThat(answerTo("hsv-to-rgb 134.116.10.100"))
                    .isEqualTo("5.9.10.100");
        }

        @Test
        @DisplayName("and so is everything past the fourth")
        void andSoIsEverythingPastTheFourth() {
            assertThat(answerTo("rgb-to-hsv 1.2.3.4.5")).isEqualTo("148.170.3.4.5");
            assertThat(answerTo("hsv-to-rgb 1.2.3.4.5")).isEqualTo("3.2.2.4.5");
            assertThat(answerTo("rgb-to-hsv to tuple! [1 2 3 4 5 6 7 8 9 10 11 12]"))
                    .isEqualTo("148.170.3.4.5.6.7.8.9.10.11.12");
        }

        @Test
        @DisplayName("a tuple shorter than three keeps its length, so the rest reads as nought")
        void aShortTupleKeepsItsLength() {
            assertThat(answerTo("rgb-to-hsv to tuple! [200]")).isEqualTo("0.0.0");
            assertThat(answerTo("rgb-to-hsv to tuple! [200 100]")).isEqualTo("21.255.0");
            assertThat(answerTo("hsv-to-rgb to tuple! [200]")).isEqualTo("0.0.0");
            assertThat(answerTo("hsv-to-rgb to tuple! [200 100]")).isEqualTo("0.0.0");
        }

        @Test
        @DisplayName("and a grey with an alpha keeps both")
        void aGreyWithAnAlphaKeepsBoth() {
            assertThat(answerTo("rgb-to-hsv 9.9.9.77")).isEqualTo("0.0.9.77");
            assertThat(answerTo("hsv-to-rgb 9.0.9.77")).isEqualTo("9.9.9.77");
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
