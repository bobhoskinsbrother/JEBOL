package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An image walked and written as the series of pixels it is.
 *
 * <p>Its positions can be named two ways -- a count of pixels or a column and
 * a row -- and the second was missing entirely: {@code /xy} was accepted and
 * ignored, {@code atz} would not take a pair, and FOREACH said an image was
 * not something it could walk.
 *
 * <p>Its fields could be read and not written, so {@code img/color: 1.2.3}
 * quietly did nothing. The setters are not the getters turned round, either.
 * RGB and COLOR leave the alpha where it was, OPACITY is the alpha counted
 * backwards, GRAY and LUMINOSITY write one byte into three channels, and BGR
 * has no setter at all -- it is missing from the switch in the C, and there is
 * no comment saying whether that was meant.
 */
class ImageAsASeriesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("failure: try [" + source + "] failure/id");
    }

    @Nested
    @DisplayName("a position named as a column and a row")
    class TheColumnAndRow {

        @Test
        @DisplayName("INDEX?/XY counts both from one")
        void indexCountsFromOne() {
            assertThat(answerTo("""
                    picture: make image! 2x3
                    reduce [
                        index?/xy picture
                        index?/xy next picture
                        index?/xy tail picture
                        index?/xy skip tail picture -1
                    ]""")).isEqualTo("[1x1 2x1 1x4 2x3]");
        }

        @Test
        @DisplayName("and INDEXZ?/XY counts both from nothing")
        void indexzCountsFromNothing() {
            assertThat(answerTo("""
                    picture: make image! 2x3
                    reduce [
                        indexz?/xy picture
                        indexz?/xy next picture
                        indexz?/xy tail picture
                    ]""")).isEqualTo("[0x0 1x0 0x3]");
        }

        @Test
        @DisplayName("the tail is the first column of a row that is not there")
        void theTailIsAColumnOfNowhere() {
            assertThat(answerTo("""
                    reduce [
                        index?/xy tail make image! 2x3
                        index?/xy tail make image! 3x2
                    ]""")).isEqualTo("[1x4 1x3]");
        }

        @Test
        @DisplayName("AT takes a pair counting from one, ATZ one counting from nothing")
        void atAndAtzTakePairs() {
            assertThat(answerTo("""
                    picture: make image! 2x3
                    reduce [
                        index? at picture 1x2
                        index? at picture 2x2
                        index? atz picture 0x1
                        index? atz picture 1x1
                    ]""")).isEqualTo("[3 4 3 4]");
        }

        @Test
        @DisplayName("and a column past the width runs on into the rows after it")
        void aColumnPastTheWidthRunsOn() {
            assertThat(answerTo("""
                    picture: make image! 2x3
                    reduce [index? at picture 20x2 index? atz picture 2x2]"""))
                    .isEqualTo("[7 7]");
        }

        @Test
        @DisplayName("/XY means nothing to any other series, and is ignored there")
        void xyMeansNothingElsewhere() {
            assertThat(answerTo("""
                    reduce [index?/xy next "abc" index?/xy next [1 2 3]]"""))
                    .isEqualTo("[2 2]");
        }
    }

    @Nested
    @DisplayName("walking one")
    class WalkingIt {

        @Test
        @DisplayName("FOREACH hands over a pixel at a time, as a tuple")
        void foreachHandsOverPixels() {
            assertThat(answerTo("""
                    collect [foreach pixel make image! 2x1 [keep pixel]]"""))
                    .isEqualTo("[255.255.255.255 255.255.255.255]");
        }

        @Test
        @DisplayName("from the position rather than the head")
        void fromThePosition() {
            assertThat(answerTo("""
                    picture: make image! 3x1
                    picture/1: 1.2.3
                    collect [foreach pixel next picture [keep pixel]]"""))
                    .isEqualTo("[255.255.255.255 255.255.255.255]");
        }

        @Test
        @DisplayName("and a picture with no pixels hands over nothing")
        void anEmptyPictureHandsOverNothing() {
            assertThat(answerTo("""
                    collect [foreach pixel make image! 0x0 [keep pixel]]"""))
                    .isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("writing one pixel through a path")
    class WritingOnePixel {

        @Test
        @DisplayName("a pair names it as a column and a row")
        void aPairNamesIt() {
            assertThat(answerTo("""
                    picture: make image! 2x2
                    picture/(1x1): 1.2.3
                    picture/(1x2): 4.5.6
                    mold picture""")).isEqualTo(
                            "\"make image! [2x2 #{010203FFFFFF040506FFFFFF}]\"");
        }

        @Test
        @DisplayName("a three-part tuple makes the pixel opaque again")
        void aThreePartTupleMakesItOpaque() {
            assertThat(answerTo("""
                    picture: make image! 1x1
                    picture/alpha: 100
                    picture/1: 1.2.3
                    picture/1""")).isEqualTo("1.2.3.255");
        }

        @Test
        @DisplayName("where filling the whole picture with COLOR leaves the alpha alone")
        void whereColourLeavesIt() {
            assertThat(answerTo("""
                    picture: make image! 1x1
                    picture/alpha: 100
                    picture/color: 1.2.3
                    picture/1""")).isEqualTo("1.2.3.100");
        }
    }

    @Nested
    @DisplayName("the fields of the whole picture")
    class TheWholePicture {

        @Test
        @DisplayName("COLOR reads every pixel averaged into one")
        void colourAveragesThemAll() {
            assertThat(answerTo("""
                    picture: make image! 2x1
                    first-was: picture/color
                    picture/1: 0.0.0
                    then-was: picture/color
                    picture/2: 50.130.60.200
                    reduce [first-was then-was picture/color]"""))
                    .isEqualTo("[255.255.255.255 127.127.127.255 25.65.30.227]");
        }

        @Test
        @DisplayName("and a picture with nothing left in it is transparent black")
        void anEmptyPictureIsTransparentBlack() {
            assertThat(answerTo("""
                    nothing-left: tail make image! 2x1
                    nothing-left/color""")).isEqualTo("0.0.0.0");
        }

        @Test
        @DisplayName("COLOR written fills every pixel and leaves the alpha alone")
        void colourWrittenLeavesTheAlpha() {
            assertThat(answerTo("""
                    picture: make image! 2x1
                    picture/alpha: #{64C8}
                    picture/color: 25.65.30.227
                    picture/rgba""")).isEqualTo("#{19411E64 19411EC8}".replace(" ", ""));
        }

        @Test
        @DisplayName("OPACITY is the alpha counted backwards, both ways round")
        void opacityIsTheAlphaBackwards() {
            assertThat(answerTo("""
                    picture: make image! 2x1
                    picture/alpha: #{64C8}
                    read-back: picture/opacity
                    picture/opacity: #{9B37}
                    reduce [read-back picture/alpha]"""))
                    .isEqualTo("[#{9B37} #{64C8}]");
        }

        @Test
        @DisplayName("GRAY written from bytes gives each pixel its own grey")
        void grayWrittenFromBytes() {
            assertThat(answerTo("""
                    picture: make image! 2x1
                    picture/alpha: #{64C8}
                    picture/gray: #{0102}
                    reduce [picture/rgba picture/gray]"""))
                    .isEqualTo("[#{01010164020202C8} #{0102}]");
        }

        @Test
        @DisplayName("the two one-byte readings are two different sums")
        void theTwoOneByteReadings() {
            assertThat(answerTo("""
                    picture: make image! 1x1
                    picture/1: 100.150.200
                    reduce [picture/luminosity picture/gray]"""))
                    .isEqualTo("[#{8E} #{96}]");
        }

        @Test
        @DisplayName("and LUMA is not one of them, only the encoder reaching that")
        void lumaIsNotAField() {
            assertThat(errorIdFrom("""
                    picture: make image! 1x1
                    picture/luma""")).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("BGR can be read and not written, which the C does not explain")
        void bgrCanBeReadAndNotWritten() {
            assertThat(answerTo("""
                    picture: make image! 1x1
                    picture/1: 1.2.3
                    picture/bgr""")).isEqualTo("#{030201}");
            assertThat(errorIdFrom("""
                    picture: make image! 1x1
                    picture/bgr: #{030201}""")).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("a channel takes one byte for all of them or one byte each")
        void aChannelTakesOneOrMany() {
            assertThat(answerTo("""
                    picture: make image! 2x1
                    picture/red: 9
                    all-nine: picture/red
                    picture/red: #{0104}
                    reduce [all-nine picture/red]"""))
                    .isEqualTo("[#{0909} #{0104}]");
        }

        @Test
        @DisplayName("and a byte that is not one is out of range")
        void aByteThatIsNotOne() {
            assertThat(errorIdFrom("""
                    picture: make image! 1x1
                    picture/red: 300""")).isEqualTo("out-of-range");
        }
    }
}
