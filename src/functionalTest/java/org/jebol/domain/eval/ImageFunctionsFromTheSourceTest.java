package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        void halvesAHalfTransparentRed() {
            assertThat(answerTo("""
                    i: make image! 4x4
                    i/1: 255.0.0.128
                    premultiply i
                    mold i/1""")).isEqualTo("\"128.0.0.128\"");
        }

        @Test
        @DisplayName("a fully opaque pixel is left exactly as it was")
        void leavesAnOpaquePixelUntouched() {
            assertThat(answerTo("""
                    i: make image! 2x2
                    i/1: 200.100.50.255
                    premultiply i
                    (mold i/1) = "200.100.50.255\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a wholly transparent pixel loses its colour altogether")
        void blackensATransparentPixel() {
            assertThat(answerTo("""
                    i: make image! 2x2
                    i/1: 255.255.255.0
                    premultiply i
                    (mold i/1) = "0.0.0.0\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it answers the image it was given, not a copy")
        void answersTheSameValue() {
            assertThat(answerTo("i: make image! 2x2  same? i premultiply i"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("BLUR")
    class TheBlur {

        @Test
        @DisplayName("answers the image it was given")
        void answersTheSameValue() {
            assertThat(answerTo("i: make image! 4x4  same? i blur i 2")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a radius of nothing or less leaves the image alone")
        void doesNothingForARadiusOfNone() {
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
        void spreadsColourForARealRadius() {
            assertThat(answerTo("""
                    i: make image! 4x4
                    repeat n 16 [poke i n 0.0.0.255]
                    poke i 1 255.255.255.255
                    blur i 1
                    (mold i/2) <> "0.0.0.255\"""")).isEqualTo(TRUE);
        }

        private static String blurASpot(int wide, int high, String radius) {
            return answerTo("""
                    i: make image! [%dx%d 0.0.0]
                    i/1: 255.255.255
                    blur i %s
                    enbase/flat to binary! i 16""".formatted(wide, high, radius));
        }

        @Test
        @DisplayName("a spot spreads by the shape of three box blurs, to the byte")
        void spreadsASpotByTheShapeOfThreeBoxBlurs() {
            assertThat(blurASpot(4, 4, "1")).isEqualTo("""
                    {717171FF383838FF000000FF000000FF\
                    383838FF1C1C1CFF000000FF000000FF\
                    000000FF000000FF000000FF000000FF\
                    000000FF000000FF000000FF000000FF}""");
        }

        @Test
        @DisplayName("and a wider radius spreads it further")
        void spreadsFurtherForAWiderRadius() {
            assertThat(blurASpot(5, 5, "2")).isEqualTo("""
                    {2C2C2CFF202020FF141414FF090909FF020202FF\
                    202020FF171717FF0F0F0FFF060606FF010101FF\
                    151515FF0F0F0FFF090909FF040404FF000000FF\
                    090909FF060606FF040404FF010101FF000000FF\
                    020202FF010101FF010101FF000000FF000000FF}""");
        }

        @Test
        @DisplayName("a radius wider than the picture comes down to half its shorter side")
        void bringsDownARadiusWiderThanThePicture() {
            String atTheLimit = blurASpot(4, 4, "2");
            assertThat(atTheLimit).isEqualTo("""
                    {2C2C2CFF202020FF1B1B1BFF0F0F0FFF\
                    202020FF171717FF131313F40A0A0AF4\
                    151515F40F0F0FCC0C0C0CC1060606C1\
                    090909F4060606CC040404B7020202B7}""");
            assertThat(blurASpot(4, 4, "5")).isEqualTo(atTheLimit);
            assertThat(blurASpot(4, 4, "100000")).isEqualTo(atTheLimit);
        }

        @Test
        @DisplayName("a picture too small to blur comes back untouched")
        void leavesAPictureTooSmallToBlurUntouched() {
            assertThat(blurASpot(1, 1, "1")).isEqualTo("\"FFFFFFFF\"");
            assertThat(blurASpot(3, 1, "1")).isEqualTo("""
                    "FFFFFFFF000000FF000000FF\"""");
            assertThat(blurASpot(1, 3, "1")).isEqualTo("""
                    "FFFFFFFF000000FF000000FF\"""");
            assertThat(blurASpot(3, 1, "100000")).isEqualTo("""
                    "FFFFFFFF000000FF000000FF\"""");
        }

        @Test
        @DisplayName("and a picture with no pixels is left alone rather than refused")
        void leavesAPictureWithNoPixelsAlone() {
            assertThat(answerTo("""
                    i: make image! 0x0
                    blur i 1
                    reduce [image? i  mold i/size  empty? to binary! i]"""))
                    .isEqualTo("[#(true) \"0x0\" #(true)]");
        }

        @Test
        @DisplayName("the smallest picture there is anything to do on")
        void blursTheSmallestPictureThereIsAnythingToDoOn() {
            assertThat(blurASpot(2, 2, "1"))
                    .isEqualTo("\"717171FF383838E2383838E21C1C1C8D\"");
        }

        @Test
        @DisplayName("and alpha is blurred with the colour, not carried past it")
        void blursAlphaWithTheColour() {
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
        void takesAPairAsTheSize() {
            assertThat(answerTo("r: resize (make image! 4x4) 2x2  mold r/size"))
                    .isEqualTo("\"2x2\"");
        }

        @Test
        @DisplayName("a whole number is a width, and the height follows to keep the shape")
        void takesAWholeNumberAsAWidth() {
            assertThat(answerTo("r: resize (make image! 8x4) 4  mold r/size"))
                    .isEqualTo("\"4x2\"");
        }

        @Test
        @DisplayName("a percentage is a proportion of what it was")
        void takesAPercentageAsAProportion() {
            assertThat(answerTo("r: resize (make image! 4x4) 50%  mold r/size"))
                    .isEqualTo("\"2x2\"");
        }

        @Test
        @DisplayName("and it is a new image, so the original is left as it was")
        void leavesTheOriginalUntouched() {
            assertThat(answerTo("""
                    i: make image! 4x4
                    r: resize i 2x2
                    all [not same? i r  (mold i/size) = "4x4"]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a size of nothing is refused rather than making an empty image")
        void refusesAZeroSize() {
            assertThat(answerTo("""
                    e: try [resize (make image! 4x4) 0x0] e/id""")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("but one side at nought is taken from the shape instead")
        void takesOneSideAtNoughtFromTheShape() {
            assertThat(answerTo("r: resize (make image! 10x4) 5x0  mold r/size"))
                    .isEqualTo("\"5x2\"");
            assertThat(answerTo("r: resize (make image! 10x4) 0x5  mold r/size"))
                    .isEqualTo("\"12x5\"");
            assertThat(answerTo("r: resize (make image! 10x4) 0x2  mold r/size"))
                    .isEqualTo("\"5x2\"");
        }

        @Test
        @DisplayName("a width that leaves no height at all is refused")
        void refusesAWidthThatLeavesNoHeight() {
            assertThat(answerTo("""
                    e: try [resize (make image! 10x1) 3] e/id""")).isEqualTo("no-create");
            assertThat(answerTo("""
                    e: try [resize (make image! 10x4) 1] e/id""")).isEqualTo("no-create");
            assertThat(answerTo("r: resize (make image! 10x4) 3  mold r/size"))
                    .isEqualTo("\"3x1\"");
        }

        @Test
        @DisplayName("and a filter it has never heard of is refused by name")
        void refusesAFilterItHasNeverHeardOf() {
            assertThat(answerTo("""
                    e: try [resize/filter (make image! 4x4) 2x2 'nonsense]
                    reduce [e/id  word? e/arg1  e/arg1]"""))
                    .isEqualTo("[invalid-arg #(true) nonsense]");
            assertThat(answerTo("""
                    e: try [resize/filter (make image! 4x4) 2x2 99] e/id"""))
                    .isEqualTo("invalid-arg");
            assertThat(answerTo("""
                    r: resize/filter (make image! 4x4) 2x2 'box  mold r/size"""))
                    .isEqualTo("\"2x2\"");
        }

        @Test
        @DisplayName("the catalogue names the fifteen it can be asked for")
        void knowsTheFifteenFiltersInTheCatalogue() {
            assertThat(answerTo("length? system/catalog/filters")).isEqualTo("15");
            assertThat(answerTo("""
                    reduce [
                        not none? find system/catalog/filters 'Point
                        not none? find system/catalog/filters 'Lanczos
                        not none? find system/catalog/filters 'Sinc
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("IMAGE-DIFF, by the measure the C actually uses")
    class TheDifference {

        @Test
        @DisplayName("two images the same are nought per cent apart")
        void isNoughtForTheSame() {
            assertThat(answerTo("image-diff (make image! 2x2) (make image! 2x2)"))
                    .isEqualTo("0%");
        }

        @Test
        @DisplayName("but comparing no pixels at all is not a number")
        void isNotANumberForNoPixelsAtAll() {
            assertThat(answerTo("image-diff (make image! 0x0) (make image! 0x0)"))
                    .isEqualTo("1.#NaN");
        }

        @Test
        @DisplayName("black against white is exactly a hundred, thanks to the rounding")
        void isAHundredForBlackAgainstWhite() {
            assertThat(answerTo("""
                    a: make image! 2x2
                    c: make image! 2x2
                    repeat n 4 [poke c n 0.0.0.255]
                    image-diff a c""")).isEqualTo("100%");
        }

        @Test
        @DisplayName("alpha takes no part, so transparency alone is no difference at all")
        void leavesAlphaUncounted() {
            assertThat(answerTo("""
                    a: make image! 2x2
                    c: make image! 2x2
                    repeat n 4 [poke c n 255.255.255.0]
                    image-diff a c""")).isEqualTo("0%");
        }

        @Test
        @DisplayName("and the weighting is the redmean one, to the last digit")
        void weightsByRedmean() {
            assertThat(answerTo("""
                    a: make image! 2x2
                    c: make image! 2x2
                    repeat n 4 [poke c n 255.0.0.255]
                    image-diff a c""")).isEqualTo("81.6674525046854%");
        }

        @Test
        @DisplayName("where the sizes differ only the overlap counts")
        void countsOnlyTheOverlap() {
            assertThat(answerTo("""
                    image-diff (make image! 4x4) (make image! 2x2)""")).isEqualTo("0%");
        }
    }

    @Nested
    @DisplayName("and /PART narrows it to a rectangle")
    class TheRectangle {

        private static final String A_HALF_BLACK_PAIR = """
                i1: make image! [2x2 0.0.0]
                i2: make image! [2x2 255.255.255]
                i1/1: 255.255.255
                i1/2: 255.255.255
                """;

        private static String compare(String refinement) {
            return answerTo(A_HALF_BLACK_PAIR
                    + "round/to image-diff/part i1 i2 " + refinement + " 1%");
        }

        private static String refuse(String refinement) {
            return answerTo(A_HALF_BLACK_PAIR
                    + "e: try [image-diff/part i1 i2 " + refinement + "]"
                    + " either error? e [e/id] ['no-error]");
        }

        @Test
        @DisplayName("the corner is counted from nought")
        void countsTheCornerFromNought() {
            assertThat(compare("0x0 1x1")).isEqualTo("0%");
            assertThat(compare("1x0 1x1")).isEqualTo("0%");
            assertThat(compare("0x1 1x1")).isEqualTo("100%");
            assertThat(compare("1x1 1x1")).isEqualTo("100%");
        }

        @Test
        @DisplayName("and the size says how far the rectangle reaches")
        void reachesAsFarAsTheSizeSays() {
            assertThat(compare("0x0 2x1")).isEqualTo("0%");
            assertThat(compare("0x1 2x1")).isEqualTo("100%");
            assertThat(compare("0x0 1x2")).isEqualTo("50%");
            assertThat(compare("0x0 2x2")).isEqualTo("50%");
        }

        @Test
        @DisplayName("a negative size reaches back from the corner")
        void reachesBackForANegativeSize() {
            assertThat(compare("2x2 -1x-2")).isEqualTo("50%");
            assertThat(compare("1x0 1x2")).isEqualTo("50%");
            assertThat(compare("1x2 1x-1")).isEqualTo("100%");
        }

        @Test
        @DisplayName("and a negative corner is brought back to nought, taking the size with it")
        void bringsBackANegativeCorner() {
            assertThat(compare("-1x0 2x2")).isEqualTo("50%");
        }

        @Test
        @DisplayName("a rectangle with no area is refused")
        void refusesARectangleWithNoArea() {
            assertThat(refuse("0x0 0x0")).isEqualTo("invalid-data");
            assertThat(refuse("0x0 1x0")).isEqualTo("invalid-data");
            assertThat(refuse("0x0 0x2")).isEqualTo("invalid-data");
        }

        @Test
        @DisplayName("and so is a corner outside the picture")
        void refusesACornerOutsideThePicture() {
            assertThat(refuse("2x0 1x1")).isEqualTo("invalid-data");
            assertThat(refuse("0x2 1x1")).isEqualTo("invalid-data");
            assertThat(refuse("3x0 1x2")).isEqualTo("invalid-data");
            assertThat(refuse("0x2 1x2")).isEqualTo("invalid-data");
        }

        @Test
        @DisplayName("one column inside the edge still works")
        void acceptsACornerOneInsideTheEdge() {
            assertThat(compare("1x1 1x1")).isEqualTo("100%");
        }

        @Test
        @DisplayName("the pair that was wrong is the one named in the error")
        void namesTheWrongPairInTheError() {
            assertThat(answerTo(A_HALF_BLACK_PAIR
                    + "e: try [image-diff/part i1 i2 0x0 0x2] mold e/arg1"))
                    .isEqualTo("\"0x2\"");
            assertThat(answerTo(A_HALF_BLACK_PAIR
                    + "e: try [image-diff/part i1 i2 3x0 1x2] mold e/arg1"))
                    .isEqualTo("\"3x0\"");
        }

        @Test
        @DisplayName("the corner is measured against the larger of the two")
        void measuresTheCornerAgainstTheLarger() {
            assertThat(answerTo(A_HALF_BLACK_PAIR
                    + "i3: make image! [1x1 0.0.0]\n"
                    + "round/to image-diff/part i1 i3 0x0 1x1 1%")).isEqualTo("100%");
        }

        @Test
        @DisplayName("and a rectangle reaching past the edge compares nothing at all")
        void comparesNothingPastTheEdge() {
            assertThat(compare("0x0 1x3")).isEqualTo("0%");
            assertThat(compare("0x0 3x1")).isEqualTo("0%");
            assertThat(compare("0x1 1x3")).isEqualTo("0%");
        }
    }

    @Nested
    @DisplayName("IMAGE refuses where the host supplied no codec")
    class TheCodecShim {

        @Test
        @DisplayName("/LOAD is feature-na before it is a missing file")
        void refusesLoading() {
            assertThat(answerTo("""
                    e: try [image/load %picture.png] e/id""")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("and /SAVE before it is a bad argument")
        void refusesSaving() {
            assertThat(answerTo("""
                    e: try [image/save none none] e/id""")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("asked for nothing it answers nothing")
        void answersUnsetWithoutARefinement() {
            assertThat(answerTo("unset? image")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("all of them work on the whole image, never from where it stands")
    class ThePositionIsIgnored {

        @Test
        @DisplayName("PREMULTIPLY changes the pixels before the position too")
        void ignoresThePositionForPremultiply() {
            assertThat(answerTo("""
                    i: make image! 2x2
                    i/1: 255.0.0.128
                    premultiply next i
                    (mold i/1) = "128.0.0.128\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and IMAGE-DIFF compares from the first pixel of each")
        void ignoresThePositionForImageDiff() {
            assertThat(answerTo("""
                    a: make image! 2x2
                    c: make image! 2x2
                    (image-diff next a c) = (image-diff a c)""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the spare pixel of a partly-filled row is past all of them")
        void putsTheSparePixelOfAPartlyFilledRowPastAllOfThem() {
            assertThat(answerTo("""
                    i: make image! [2x1 200.100.50]
                    append i 200.100.50
                    poke i 3 200.100.50.128
                    premultiply i
                    reduce [mold i/1  mold i/3]"""))
                    .isEqualTo("""
                            ["200.100.50.255" "200.100.50.128"]""");
            assertThat(answerTo("""
                    i: make image! [2x1 200.100.50]
                    poke i 1 200.100.50.128
                    premultiply i
                    mold i/1""")).isEqualTo("\"100.50.25.128\"");
        }
    }
}
