package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The five functions {@code n-image.c} performs on a whole image.
 *
 * <p>Whole rather than from a position, which all five share and the C flags
 * in a comment of its own: "All pixels are modified even when the input image
 * is not at its head!" An image is a series and has a position; these ignore
 * it where every other series function obeys it.
 *
 * <p>Three change the image and answer it back, so a caller holding the value
 * sees the change. RESIZE is the exception, making a new image because the old
 * one is the wrong size to hold the answer. IMAGE is the shim onto an
 * operating system's own encoder, which this build has not got.
 *
 * <p>IMAGE-DIFF was the one worth reading the C for rather than guessing. It
 * is the redmean approximation from compuphase, which the C cites: green
 * weighted at four throughout, and the red and blue weights sliding with how
 * red the pair already is. Alpha takes no part at all, and the mean is rounded
 * to a whole number of picounits before dividing -- without which black
 * against white is a true 99.9999999999999% that reads as a mistake. Every
 * number below was checked against a real 3.22.1.
 *
 * <p>Specified in {@code spec/natives.allium} under the image functions.
 */
class ImageFunctionsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("PREMULTIPLY scales colour by alpha")
    class ThePremultiply {

        @Test
        @DisplayName("a half-transparent red becomes a half red")
        void aHalfTransparentRed() {
            assertThat(answerTo("""
                    i: make image! 4x4
                    i/1: 255.0.0.128
                    premultiply i
                    mold i/1""")).isEqualTo("\"128.0.0.128\"");
        }

        @Test
        @DisplayName("a fully opaque pixel is left exactly as it was")
        void anOpaquePixelIsUntouched() {
            assertThat(answerTo("""
                    i: make image! 2x2
                    i/1: 200.100.50.255
                    premultiply i
                    (mold i/1) = "200.100.50.255\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a wholly transparent pixel loses its colour altogether")
        void aTransparentPixelGoesBlack() {
            assertThat(answerTo("""
                    i: make image! 2x2
                    i/1: 255.255.255.0
                    premultiply i
                    (mold i/1) = "0.0.0.0\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it answers the image it was given, not a copy")
        void itAnswersTheSameValue() {
            assertThat(answerTo("i: make image! 2x2  same? i premultiply i"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("BLUR")
    class TheBlur {

        @Test
        @DisplayName("answers the image it was given")
        void itAnswersTheSameValue() {
            assertThat(answerTo("i: make image! 4x4  same? i blur i 2")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a radius of nothing or less leaves the image alone")
        void aRadiusOfNoneDoesNothing() {
            assertThat(answerTo("""
                    i: make image! 2x2
                    i/1: 255.0.0.255
                    blur i 0
                    (mold i/1) = "255.0.0.255\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    i: make image! 2x2
                    i/1: 255.0.0.255
                    blur i -3
                    (mold i/1) = "255.0.0.255\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a real radius spreads one bright pixel into its neighbour")
        void aRealRadiusSpreadsColour() {
            assertThat(answerTo("""
                    i: make image! 4x4
                    repeat n 16 [poke i n 0.0.0.255]
                    poke i 1 255.255.255.255
                    blur i 1
                    (mold i/2) <> "0.0.0.255\"""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("RESIZE makes a new image")
    class TheResize {

        @Test
        @DisplayName("a pair is the size to make")
        void aPairIsTheSize() {
            assertThat(answerTo("r: resize (make image! 4x4) 2x2  mold r/size"))
                    .isEqualTo("\"2x2\"");
        }

        @Test
        @DisplayName("a whole number is a width, and the height follows to keep the shape")
        void aWholeNumberIsAWidth() {
            assertThat(answerTo("r: resize (make image! 8x4) 4  mold r/size"))
                    .isEqualTo("\"4x2\"");
        }

        @Test
        @DisplayName("a percentage is a proportion of what it was")
        void aPercentageIsAProportion() {
            assertThat(answerTo("r: resize (make image! 4x4) 50%  mold r/size"))
                    .isEqualTo("\"2x2\"");
        }

        @Test
        @DisplayName("and it is a new image, so the original is left as it was")
        void theOriginalIsUntouched() {
            assertThat(answerTo("""
                    i: make image! 4x4
                    r: resize i 2x2
                    all [not same? i r  (mold i/size) = "4x4"]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a size of nothing is refused rather than making an empty image")
        void aZeroSizeIsRefused() {
            assertThat(answerTo("""
                    e: try [resize (make image! 4x4) 0x0] error? e""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("IMAGE-DIFF, by the measure the C actually uses")
    class TheDifference {

        @Test
        @DisplayName("two images the same are nought per cent apart")
        void theSameIsNought() {
            assertThat(answerTo("image-diff (make image! 2x2) (make image! 2x2)"))
                    .isEqualTo("0%");
        }

        @Test
        @DisplayName("black against white is exactly a hundred, thanks to the rounding")
        void blackAgainstWhiteIsAHundred() {
            // Exactly, because the mean is rounded to a whole number of
            // picounits before dividing -- "used rounding to have nice 100%
            // when completely different". Without it: 99.9999999999999%.
            assertThat(answerTo("""
                    a: make image! 2x2
                    c: make image! 2x2
                    repeat n 4 [poke c n 0.0.0.255]
                    image-diff a c""")).isEqualTo("100%");
        }

        @Test
        @DisplayName("alpha takes no part, so transparency alone is no difference at all")
        void alphaIsNotCounted() {
            assertThat(answerTo("""
                    a: make image! 2x2
                    c: make image! 2x2
                    repeat n 4 [poke c n 255.255.255.0]
                    image-diff a c""")).isEqualTo("0%");
        }

        @Test
        @DisplayName("and the weighting is the redmean one, to the last digit")
        void theWeightingIsRedmean() {
            // White against pure red. An equal-weighted distance would call
            // this 66.7%; the redmean measure says this, and so does a real
            // 3.22.1.
            assertThat(answerTo("""
                    a: make image! 2x2
                    c: make image! 2x2
                    repeat n 4 [poke c n 255.0.0.255]
                    image-diff a c""")).isEqualTo("81.6674525046854%");
        }

        @Test
        @DisplayName("where the sizes differ only the overlap counts")
        void onlyTheOverlapCounts() {
            assertThat(answerTo("""
                    image-diff (make image! 4x4) (make image! 2x2)""")).isEqualTo("0%");
        }
    }

    @Nested
    @DisplayName("IMAGE reaches an encoder this build has not got")
    class TheCodecShim {

        @Test
        @DisplayName("/LOAD is feature-na, as the C is where the OS codec is absent")
        void loadingIsRefused() {
            assertThat(answerTo("""
                    e: try [image/load %picture.png] e/id""")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("and so is /SAVE")
        void savingIsRefused() {
            assertThat(answerTo("""
                    e: try [image/save none none] e/id""")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("asked for nothing it answers nothing")
        void noRefinementAnswersUnset() {
            assertThat(answerTo("unset? image")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("all of them work on the whole image, never from where it stands")
    class ThePositionIsIgnored {

        @Test
        @DisplayName("PREMULTIPLY changes the pixels before the position too")
        void premultiplyIgnoresThePosition() {
            assertThat(answerTo("""
                    i: make image! 2x2
                    i/1: 255.0.0.128
                    premultiply next i
                    (mold i/1) = "128.0.0.128\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and IMAGE-DIFF compares from the first pixel of each")
        void imageDiffIgnoresThePosition() {
            assertThat(answerTo("""
                    a: make image! 2x2
                    c: make image! 2x2
                    (image-diff next a c) = (image-diff a c)""")).isEqualTo(TRUE);
        }
    }
}
