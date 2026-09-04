package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TO IMAGE! of a binary, which is four bytes a pixel laid out at a width the
 * interpreter picks rather than the caller.
 *
 * <p>MAKE IMAGE! wants a size and reads a block; TO takes bytes and has no
 * size to go on, so {@code t-image.c} chooses one: as many pixels across as
 * there are up to a hundred, a hundred to a row up to ten thousand, five
 * hundred to a row beyond that. The height follows, and the last row may be
 * short -- the pixels nobody supplied stay the opaque white a new image is
 * filled with.
 *
 * <p>Fewer than four bytes is not an empty picture but an error, and something
 * that is not a binary at all is refused by type rather than by argument,
 * because {@code Trap_Type(arg)} is the last line of the branch and
 * {@code Trap_Make} is the line above it.
 */
class ImageFromABinaryFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("failure: try [" + source + "] failure/id");
    }

    @Nested
    @DisplayName("four bytes to a pixel, in the order red green blue alpha")
    class ThePixels {

        @Test
        @DisplayName("one pixel keeps all four bytes in their places")
        void onePixelKeepsAllFour() {
            assertThat(answerTo("""
                    mold to image! #{11223344}"""))
                    .isEqualTo("\"make image! [1x1 #{112233} #{44}]\"");
        }

        @Test
        @DisplayName("two pixels go side by side")
        void twoPixelsGoSideBySide() {
            assertThat(answerTo("""
                    mold to image! #{0000000011111111}"""))
                    .isEqualTo("\"make image! [2x1 #{000000111111} #{0011}]\"");
        }

        @Test
        @DisplayName("and bytes past the last whole pixel are dropped")
        void bytesPastTheLastWholePixelAreDropped() {
            assertThat(answerTo("""
                    reduce [
                        mold to image! #{00000000}
                        mold to image! #{0000000011}
                        mold to image! #{000000001111}
                        mold to image! #{00000000111111}
                    ]""")).isEqualTo("""
                    ["make image! [1x1 #{000000} #{00}]" \
                    "make image! [1x1 #{000000} #{00}]" \
                    "make image! [1x1 #{000000} #{00}]" \
                    "make image! [1x1 #{000000} #{00}]"]""");
        }
    }

    @Nested
    @DisplayName("the width, which changes twice as the picture grows")
    class TheWidth {

        private String sizesFor(String counts) {
            return answerTo("""
                    sizes: copy []
                    foreach many [%s][
                        picture: to image! head insert/dup copy #{} #{01020304} many
                        append sizes picture/size
                    ]
                    sizes""".formatted(counts));
        }

        @Test
        @DisplayName("under a hundred pixels the picture is one row of them")
        void underAHundredIsOneRow() {
            assertThat(sizesFor("1 2 99 100")).isEqualTo("[1x1 2x1 99x1 100x1]");
        }

        @Test
        @DisplayName("from a hundred and one it is a hundred to a row")
        void fromAHundredAndOne() {
            assertThat(sizesFor("101 199 200 9999")).isEqualTo("[100x2 100x2 100x2 100x100]");
        }

        @Test
        @DisplayName("and from ten thousand it is five hundred to a row")
        void fromTenThousand() {
            assertThat(sizesFor("10000 10001 250000"))
                    .isEqualTo("[500x20 500x21 500x500]");
        }

        @Test
        @DisplayName("a short last row is filled out with opaque white")
        void aShortLastRowIsWhite() {
            assertThat(answerTo("""
                    picture: to image! head insert/dup copy #{} #{01020304} 101
                    reduce [picture/101 picture/102 picture/200]"""))
                    .isEqualTo("[1.2.3.4 255.255.255.255 255.255.255.255]");
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class TheRefusals {

        @Test
        @DisplayName("not one whole pixel is bad-make-arg, not an empty picture")
        void notOneWholePixel() {
            assertThat(errorIdFrom("to image! #{}")).isEqualTo("bad-make-arg");
            assertThat(errorIdFrom("to image! #{00}")).isEqualTo("bad-make-arg");
            assertThat(errorIdFrom("to image! #{0000}")).isEqualTo("bad-make-arg");
            assertThat(errorIdFrom("to image! #{000000}")).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("and something that is not bytes at all is refused by type")
        void somethingThatIsNotBytes() {
            assertThat(errorIdFrom("""
                    to image! {abcd}""")).isEqualTo("invalid-type");
            assertThat(errorIdFrom("to image! 1")).isEqualTo("invalid-type");
            assertThat(errorIdFrom("to image! [1 2]")).isEqualTo("invalid-type");
        }

        @Test
        @DisplayName("an image converts to itself, and to a copy rather than the same one")
        void anImageConvertsToACopy() {
            assertThat(answerTo("""
                    original: to image! #{11223344}
                    copied: to image! original
                    copied/1: 9.9.9
                    reduce [mold original/1 mold copied/1]"""))
                    .isEqualTo("[\"17.34.51.68\" \"9.9.9.68\"]");
        }
    }
}
