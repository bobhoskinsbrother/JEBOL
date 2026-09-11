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

        /**
         * One white pixel in the top-left corner of an otherwise black
         * picture, blurred, and every byte checked. A single spot is the
         * sharpest input there is: the answer is the blur's own shape, so a
         * kernel of the wrong width or a pass run in the wrong order shows up
         * in the first row.
         *
         * <p>Every expectation was read off a real 3.22.5 and confirmed
         * against the reference C compiled on its own.
         */
        private static String aSpotBlurred(int wide, int high, String radius) {
            return answerTo("""
                    i: make image! [%dx%d 0.0.0]
                    i/1: 255.255.255
                    blur i %s
                    enbase/flat to binary! i 16""".formatted(wide, high, radius));
        }

        @Test
        @DisplayName("a spot spreads by the shape of three box blurs, to the byte")
        void aSpotSpreadsByTheShapeOfThreeBoxBlurs() {
            assertThat(aSpotBlurred(4, 4, "1")).isEqualTo("""
                    {717171FF383838FF000000FF000000FF\
                    383838FF1C1C1CFF000000FF000000FF\
                    000000FF000000FF000000FF000000FF\
                    000000FF000000FF000000FF000000FF}""");
        }

        @Test
        @DisplayName("and a wider radius spreads it further")
        void aWiderRadiusSpreadsItFurther() {
            assertThat(aSpotBlurred(5, 5, "2")).isEqualTo("""
                    {2C2C2CFF202020FF141414FF090909FF020202FF\
                    202020FF171717FF0F0F0FFF060606FF010101FF\
                    151515FF0F0F0FFF090909FF040404FF000000FF\
                    090909FF060606FF040404FF010101FF000000FF\
                    020202FF010101FF010101FF000000FF000000FF}""");
        }

        /**
         * Half the shorter side is as blurred as a picture gets, so every
         * radius at or above it gives the same answer. Two on a four-wide
         * picture is that half, and five and a hundred thousand both come
         * down to it.
         */
        @Test
        @DisplayName("a radius wider than the picture comes down to half its shorter side")
        void aRadiusWiderThanThePictureComesDown() {
            String atTheLimit = aSpotBlurred(4, 4, "2");
            assertThat(atTheLimit).isEqualTo("""
                    {2C2C2CFF202020FF1B1B1BFF0F0F0FFF\
                    202020FF171717FF131313F40A0A0AF4\
                    151515F40F0F0FCC0C0C0CC1060606C1\
                    090909F4060606CC040404B7020202B7}""");
            assertThat(aSpotBlurred(4, 4, "5")).isEqualTo(atTheLimit);
            assertThat(aSpotBlurred(4, 4, "100000")).isEqualTo(atTheLimit);
        }

        /**
         * Half of one is nought, so a picture with a side shorter than two
         * cannot be blurred at all however large a radius is asked for. It
         * comes back exactly as it was rather than as an error.
         */
        @Test
        @DisplayName("a picture too small to blur comes back untouched")
        void aPictureTooSmallToBlurComesBackUntouched() {
            assertThat(aSpotBlurred(1, 1, "1")).isEqualTo("\"FFFFFFFF\"");
            assertThat(aSpotBlurred(3, 1, "1")).isEqualTo("""
                    "FFFFFFFF000000FF000000FF\"""");
            assertThat(aSpotBlurred(1, 3, "1")).isEqualTo("""
                    "FFFFFFFF000000FF000000FF\"""");
            assertThat(aSpotBlurred(3, 1, "100000")).isEqualTo("""
                    "FFFFFFFF000000FF000000FF\"""");
        }

        @Test
        @DisplayName("and a picture with no pixels is left alone rather than refused")
        void aPictureWithNoPixelsIsLeftAlone() {
            assertThat(answerTo("""
                    i: make image! 0x0
                    blur i 1
                    reduce [image? i  mold i/size  empty? to binary! i]"""))
                    .isEqualTo("[#(true) \"0x0\" #(true)]");
        }

        /**
         * Two by two is the smallest picture there is any blurring to do on,
         * and the one the C handles worst: the box it uses is wider than the
         * picture, so it writes past the end of each row and what comes back
         * is not the average of anything. It is still what a real 3.22.5
         * answers.
         */
        @Test
        @DisplayName("the smallest picture there is anything to do on")
        void theSmallestPictureThereIsAnythingToDoOn() {
            assertThat(aSpotBlurred(2, 2, "1"))
                    .isEqualTo("\"717171FF383838E2383838E21C1C1C8D\"");
        }

        /**
         * Alpha goes through the same pass as the three colours, so a blurred
         * edge fades in transparency as well as in colour. Nothing treats it
         * specially, which is visible above: the two-by-two case blurs its
         * alpha down from 255 along with everything else.
         */
        @Test
        @DisplayName("and alpha is blurred with the colour, not carried past it")
        void alphaIsBlurredWithTheColour() {
            assertThat(answerTo("""
                    i: make image! [3x3 0.0.0]
                    i/5: 10.20.30.255
                    blur i 1
                    enbase/flat to binary! i 16""")).isEqualTo("""
                            {010203FF010203FF010203FF\
                            010203FF010203FF010203FF\
                            010203FF010203FF010203FF}""");
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

    /**
     * Two by two, white along the top row and black along the bottom, against
     * a wholly white one. So every rectangle naming only the top row is
     * nought per cent, every rectangle naming only the bottom row is a
     * hundred, and one naming both is fifty -- which makes each answer say
     * which pixels were compared.
     */
    @Nested
    @DisplayName("and /PART narrows it to a rectangle")
    class TheRectangle {

        private static final String A_HALF_BLACK_PAIR = """
                i1: make image! [2x2 0.0.0]
                i2: make image! [2x2 255.255.255]
                i1/1: 255.255.255
                i1/2: 255.255.255
                """;

        private static String comparing(String refinement) {
            return answerTo(A_HALF_BLACK_PAIR
                    + "round/to image-diff/part i1 i2 " + refinement + " 1%");
        }

        private static String refusing(String refinement) {
            return answerTo(A_HALF_BLACK_PAIR
                    + "e: try [image-diff/part i1 i2 " + refinement + "]"
                    + " either error? e [e/id] ['no-error]");
        }

        /**
         * "Zero based top-left corner", says the declaration, so there is no
         * off-by-one against every other position in the language. This is a
         * coordinate into a picture rather than a position in a series.
         */
        @Test
        @DisplayName("the corner is counted from nought")
        void theCornerIsCountedFromNought() {
            assertThat(comparing("0x0 1x1")).isEqualTo("0%");
            assertThat(comparing("1x0 1x1")).isEqualTo("0%");
            assertThat(comparing("0x1 1x1")).isEqualTo("100%");
            assertThat(comparing("1x1 1x1")).isEqualTo("100%");
        }

        @Test
        @DisplayName("and the size says how far the rectangle reaches")
        void theSizeSaysHowFarItReaches() {
            assertThat(comparing("0x0 2x1")).isEqualTo("0%");
            assertThat(comparing("0x1 2x1")).isEqualTo("100%");
            assertThat(comparing("0x0 1x2")).isEqualTo("50%");
            assertThat(comparing("0x0 2x2")).isEqualTo("50%");
        }

        /**
         * The corner moves by the negative amount and the size becomes
         * positive, so `2x2 -1x-2` names the same pixels as `1x0 1x2`.
         */
        @Test
        @DisplayName("a negative size reaches back from the corner")
        void aNegativeSizeReachesBack() {
            assertThat(comparing("2x2 -1x-2")).isEqualTo("50%");
            assertThat(comparing("1x0 1x2")).isEqualTo("50%");
            assertThat(comparing("1x2 1x-1")).isEqualTo("100%");
        }

        @Test
        @DisplayName("and a negative corner is brought back to nought, taking the size with it")
        void aNegativeCornerIsBroughtBack() {
            assertThat(comparing("-1x0 2x2")).isEqualTo("50%");
        }

        @Test
        @DisplayName("a rectangle with no area is refused")
        void aRectangleWithNoAreaIsRefused() {
            assertThat(refusing("0x0 0x0")).isEqualTo("invalid-data");
            assertThat(refusing("0x0 1x0")).isEqualTo("invalid-data");
            assertThat(refusing("0x0 0x2")).isEqualTo("invalid-data");
        }

        @Test
        @DisplayName("and so is a corner outside the picture")
        void aCornerOutsideThePictureIsRefused() {
            assertThat(refusing("2x0 1x1")).isEqualTo("invalid-data");
            assertThat(refusing("0x2 1x1")).isEqualTo("invalid-data");
            assertThat(refusing("3x0 1x2")).isEqualTo("invalid-data");
            assertThat(refusing("0x2 1x2")).isEqualTo("invalid-data");
        }

        /** One inside the edge is the last corner that works. */
        @Test
        @DisplayName("one column inside the edge still works")
        void oneInsideTheEdgeStillWorks() {
            assertThat(comparing("1x1 1x1")).isEqualTo("100%");
        }

        @Test
        @DisplayName("the pair that was wrong is the one named in the error")
        void theWrongPairIsNamedInTheError() {
            assertThat(answerTo(A_HALF_BLACK_PAIR
                    + "e: try [image-diff/part i1 i2 0x0 0x2] mold e/arg1"))
                    .isEqualTo("\"0x2\"");
            assertThat(answerTo(A_HALF_BLACK_PAIR
                    + "e: try [image-diff/part i1 i2 3x0 1x2] mold e/arg1"))
                    .isEqualTo("\"3x0\"");
        }

        /**
         * The corner is measured against the larger of the two pictures, not
         * the overlap, so a rectangle reaching past the smaller one still
         * compares -- and the pixels the smaller one does not have read as
         * whatever is behind them.
         */
        @Test
        @DisplayName("the corner is measured against the larger of the two")
        void theCornerIsMeasuredAgainstTheLarger() {
            assertThat(answerTo(A_HALF_BLACK_PAIR
                    + "i3: make image! [1x1 0.0.0]\n"
                    + "round/to image-diff/part i1 i3 0x0 1x1 1%")).isEqualTo("100%");
        }

        /**
         * A rectangle reaching past the right or bottom edge is neither
         * clipped nor refused: the C subtracts the size a second time, so a
         * rectangle one column too wide comes out with a negative width and
         * nothing is compared at all. The answer is nought per cent, which
         * says the two pictures are identical when the pixels it was pointed
         * at differ. REBOL's own test asserts it twice.
         */
        @Test
        @DisplayName("and a rectangle reaching past the edge compares nothing at all")
        void aRectanglePastTheEdgeComparesNothing() {
            assertThat(comparing("0x0 1x3")).isEqualTo("0%");
            assertThat(comparing("0x0 3x1")).isEqualTo("0%");
            assertThat(comparing("0x1 1x3")).isEqualTo("0%");
        }
    }

    /**
     * An interpreter built without a host is one of these, so IMAGE refuses
     * here for the reason the C refuses on a platform with no codec. Given a
     * port it works, which is what {@code ImageCodecFromTheSourceTest} covers.
     *
     * <p>The refusal has to come before anything else the call would trip
     * over. {@code Trap0(RE_FEATURE_NA)} is the first thing the C does, so a
     * file that is not there and an argument that is not an image must not
     * report themselves ahead of it -- both did, and both are here.
     */
    @Nested
    @DisplayName("IMAGE refuses where the host supplied no codec")
    class TheCodecShim {

        @Test
        @DisplayName("/LOAD is feature-na before it is a missing file")
        void loadingIsRefused() {
            assertThat(answerTo("""
                    e: try [image/load %picture.png] e/id""")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("and /SAVE before it is a bad argument")
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
