package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The image datatype, read out of {@code t-image.c}.
 *
 * <p>An image is a series and that is the whole trick of it. The element is four
 * bytes -- {@code QUAD_SKIP(s, n)} is {@code data + n * 4} -- the tail is
 * {@code w * h}, and the width and height sit beside the data. So a position is
 * a pixel index and every navigation action comes free from being a series:
 * {@code at img 3} is the third pixel of the same image, not a smaller one.
 *
 * <p>The bytes are red, green, blue, alpha here on every platform, and Rebol's
 * own order is not: {@code include/reb-c.h} picks ARGB on a big-endian host, RGBA
 * on Android and BGRA elsewhere. What a script sees is fixed either way, so the
 * fixed order is the language and the varying one is storage. Decision 20 says
 * why that matters more here than there.
 *
 * <p>Three rules in the C that no name would suggest:
 *
 * <ul>
 *   <li>A fresh image is opaque <em>white</em>. {@code CLEAR_IMAGE} is
 *   {@code memset(p, 0xFF, ...)}, and the comment beside it says so.
 *   <li>Molding writes the alpha binary only when some pixel needs it, decided
 *   by walking every pixel rather than by reading a flag: {@code if (~*p++ &
 *   0xff000000)}.
 *   <li>A pixel written as an integer sets the <em>alpha</em> and leaves the
 *   colour: {@code *dp = (*dp & 0xffffff) | (n << 24)}.
 * </ul>
 *
 * <p>Specified in {@code spec/values.allium} as {@code ImageStorage} and
 * {@code ImageValue}.
 */
class ImageFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("MAKE, in its four forms")
    class MakingOne {

        @Test
        @DisplayName("a pair is a size, and the image it makes is opaque white")
        void aPairIsASize() {
            // `series = Make_Image(w, h, TRUE)` and then `CLEAR_IMAGE`, which is
            // a memset of 0xFF: white, and opaque because the alpha byte gets
            // 0xFF too.
            assertThat(answerTo("img: make image! 2x2 img/size")).isEqualTo("2x2");
            assertThat(answerTo("img: make image! 2x2 length? img")).isEqualTo("4");
            assertThat(answerTo("img: make image! 2x2 img/1")).isEqualTo("255.255.255.255");
        }

        @Test
        @DisplayName("and a negative side is clamped to nothing rather than refused")
        void aNegativeSideIsClamped() {
            // `w = MAX(w, 0); h = MAX(h, 0);`
            assertThat(answerTo("img: make image! -2x2 img/size")).isEqualTo("0x2");
            assertThat(answerTo("length? make image! -2x-2")).isEqualTo("0");
        }

        @Test
        @DisplayName("a side past 65535 is refused, and the error names the limit")
        void anOversizedImageIsRefused() {
            // `if (w > 0xFFFF || h > 0xFFFF) { if (error) Trap1(RE_SIZE_LIMIT,
            // Get_Type(REB_IMAGE)); ...` -- MAKE passes TRUE for error, so this
            // raises rather than answering none.
            assertThat(errorIdFrom("make image! 65536x1")).isEqualTo("size-limit");
            assertThat(errorIdFrom("make image! 1x65536")).isEqualTo("size-limit");
            assertThat(answerTo("img: make image! 65535x1 img/size")).isEqualTo("65535x1");
        }

        @Test
        @DisplayName("a block is a size and then its contents: a binary of RGB triples")
        void aBlockWithABinary() {
            // `Bin_To_RGB(ip, size, VAL_BIN_DATA(block), VAL_LEN(block) / 3)` --
            // three bytes a pixel, and the alpha the fresh image already has is
            // left alone.
            assertThat(answerTo("img: make image! [2x1 #{FF0000 00FF00}] img/1"))
                    .isEqualTo("255.0.0.255");
            assertThat(answerTo("img: make image! [2x1 #{FF0000 00FF00}] img/2"))
                    .isEqualTo("0.255.0.255");
        }

        @Test
        @DisplayName("and a second binary is the alpha channel, one byte a pixel")
        void aBlockWithAnAlphaBinary() {
            // `Bin_To_Channel(ip, size, VAL_BIN_DATA(block), VAL_LEN(block),
            // SYM_ALPHA)`.
            assertThat(answerTo("img: make image! [2x1 #{FF0000 00FF00} #{8000}] img/1"))
                    .isEqualTo("255.0.0.128");
            assertThat(answerTo("img: make image! [2x1 #{FF0000 00FF00} #{8000}] img/2"))
                    .isEqualTo("0.255.0.0");
        }

        @Test
        @DisplayName("and an integer after those two is where the image starts")
        void aBlockWithAnIndex() {
            // `VAL_INDEX(val) = (Int32s(block, 1) - 1)` -- one-based, so a 3
            // leaves the image standing at its third pixel.
            assertThat(answerTo(
                    "img: make image! [3x1 #{FF0000 00FF00 0000FF} #{808080} 3] index? img"))
                    .isEqualTo("3");
            assertThat(answerTo(
                    "img: make image! [3x1 #{FF0000 00FF00 0000FF} #{808080} 3] length? img"))
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("a tuple fills every pixel with that colour")
        void aBlockWithATuple() {
            // `Fill_Rect((REBCNT *)ip, TO_PIXEL_TUPLE(block), w, w, h,
            // VAL_TUPLE_LEN(block) == 3)` -- the last argument says the tuple
            // carried no alpha, so the alpha already there is kept.
            assertThat(answerTo("img: make image! [2x2 255.0.0] img/1"))
                    .isEqualTo("255.0.0.255");
            assertThat(answerTo("img: make image! [2x2 255.0.0] img/4"))
                    .isEqualTo("255.0.0.255");
        }

        @Test
        @DisplayName("and an integer after the tuple fills the alpha")
        void aBlockWithATupleAndAlpha() {
            // `Fill_Alpha_Rect((REBCNT *)ip, (REBYTE)VAL_INT32(block), w, w, h)`.
            assertThat(answerTo("img: make image! [2x2 255.0.0 128] img/1"))
                    .isEqualTo("255.0.0.128");
        }

        @Test
        @DisplayName("a block of tuples is the pixels one by one")
        void aBlockOfTuples() {
            // `Tuples_To_RGBA(ip, size, VAL_BLK_DATA(block), VAL_LEN(block))`.
            assertThat(answerTo("img: make image! [2x1 [255.0.0 0.255.0]] img/2"))
                    .isEqualTo("0.255.0.255");
        }

        @Test
        @DisplayName("an image is copied")
        void anImageIsCopied() {
            // `if (IS_IMAGE(arg)) goto makeCopy;` -- a copy and not an alias, so
            // writing to one leaves the other alone.
            assertThat(answerTo(
                    "a: make image! [1x1 #{FF0000}] b: make image! a b/1: 0.0.255 a/1"))
                    .isEqualTo("255.0.0.255");
        }

        @Test
        @DisplayName("and anything else is malconstructed")
        void anythingElseIsRefused() {
            // `Trap1(RE_MALCONSTRUCT, arg)` is the fall-through of every branch.
            assertThat(errorIdFrom("make image! \"2x2\"")).isEqualTo("malconstruct");
            assertThat(errorIdFrom("make image! 5")).isEqualTo("malconstruct");
        }
    }

    @Nested
    @DisplayName("molding")
    class Molding {

        @Test
        @DisplayName("a size and a binary of RGB triples")
        void theOrdinaryForm() {
            // `Emit(mold, "IxI #{", ...)` inside `make image! [...]`, and
            // `Form_RGB` writes six hex digits a pixel.
            assertThat(answerTo("mold make image! [1x1 #{FF0000}]"))
                    .isEqualTo("\"make image! [1x1 #{FF0000}]\"");
        }

        @Test
        @DisplayName("with the alpha binary only when a pixel needs one")
        void theAlphaBinaryIsConditional() {
            // `if (Image_Has_Alpha(value, FALSE))`, which walks every pixel
            // looking for an alpha that is not 0xFF rather than reading a flag.
            // So a fresh image molds without one and a transparent pixel brings
            // it back.
            assertThat(answerTo("mold make image! 1x1"))
                    .isEqualTo("\"make image! [1x1 #{FFFFFF}]\"");
            assertThat(answerTo("mold make image! [1x1 #{FF0000} #{80}]"))
                    .isEqualTo("\"make image! [1x1 #{FF0000} #{80}]\"");
        }

        @Test
        @DisplayName("and MOLD/ALL would write the construction form, which nothing here does yet")
        void theConstructionForm() {
            // `Pre_Mold` emits `#(image! ` under /ALL and `make image! [` without
            // it, and /ALL also molds from the head whatever the position. JEBOL
            // declares /ALL on MOLD and implements it for no datatype -- the
            // MOLD/ALL cluster in 5c -- so this is the plain form for now, and
            // this test is where the image's half of that lands when it arrives.
            assertThat(answerTo("mold/all make image! [1x1 #{FF0000}]"))
                    .isEqualTo("\"make image! [1x1 #{FF0000}]\"");
        }

        @Test
        @DisplayName("an empty image molds as an empty binary")
        void theEmptyImage() {
            // `if (size == 0) { Append_Byte(mold->series, '}'); return; }`
            assertThat(answerTo("mold make image! 0x0"))
                    .isEqualTo("\"make image! [0x0 #{}]\"");
        }

        @Test
        @DisplayName("and molding starts where the image stands, not at its head")
        void moldingFollowsThePosition() {
            // `size = VAL_IMAGE_LEN(value)` is the count from the index, which is
            // why /ALL sets the index to zero first and the plain form does not.
            assertThat(answerTo("mold skip make image! [2x1 #{FF0000 00FF00}] 1"))
                    .isEqualTo("\"make image! [2x1 #{00FF00}]\"");
        }
    }

    @Nested
    @DisplayName("a pixel through a path")
    class Pixels {

        @Test
        @DisplayName("an integer picks the nth pixel, counted from where the image stands")
        void anIntegerPicksAPixel() {
            // `index += n; if (n > 0) index--;`
            assertThat(answerTo("img: make image! [2x1 #{FF0000 00FF00}] img/2"))
                    .isEqualTo("0.255.0.255");
            assertThat(answerTo(
                    "img: skip make image! [2x1 #{FF0000 00FF00}] 1 img/1"))
                    .isEqualTo("0.255.0.255");
        }

        @Test
        @DisplayName("a pair picks by coordinate, one-based in both directions")
        void aPairPicksByCoordinate() {
            // `n = ((VAL_PAIR_Y_INT(sel)-1) * VAL_IMAGE_WIDE(data) +
            // (VAL_PAIR_X_INT(sel)-1)) + 1`
            assertThat(answerTo(
                    "img: make image! [2x2 #{FF0000 00FF00 0000FF 000000}] img/1x2"))
                    .isEqualTo("0.0.255.255");
        }

        @Test
        @DisplayName("a position outside the image answers none, and refuses a write")
        void outsideTheImage() {
            // `if (n == 0 || index < 0 || index >= series->tail) { if (val)
            // return PE_BAD_SET; return PE_NONE; }` -- so reading past the end
            // is an answer and writing past it is an error.
            assertThat(answerTo("img: make image! 1x1 none? img/5")).isEqualTo(TRUE);
            assertThat(answerTo("img: make image! 1x1 none? img/0")).isEqualTo(TRUE);
            assertThat(errorIdFrom("img: make image! 1x1 img/5: 255.0.0"))
                    .isEqualTo("bad-path-set");
        }

        @Test
        @DisplayName("writing a tuple sets the colour")
        void writingATuple() {
            assertThat(answerTo("img: make image! 1x1 img/1: 1.2.3 img/1"))
                    .isEqualTo("1.2.3.255");
        }

        @Test
        @DisplayName("and writing an integer sets the alpha alone")
        void writingAnIntegerSetsTheAlpha() {
            // `*dp = (*dp & 0xffffff) | (n << 24)` -- the colour is kept and only
            // the alpha changes, which is not what the spelling suggests.
            assertThat(answerTo("img: make image! [1x1 #{FF0000}] img/1: 128 img/1"))
                    .isEqualTo("255.0.0.128");
        }

        @Test
        @DisplayName("and an integer outside a byte is refused")
        void anIntegerOutsideAByte() {
            // `if (IS_INTEGER(val) && VAL_INT64(val) >= 0 && VAL_INT64(val) <=
            // 255)` and `else return PE_BAD_ARGUMENT`.
            assertThat(errorIdFrom("img: make image! 1x1 img/1: 300"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and one byte of a pixel can be written by number")
        void writingOneByteOfAPixel() {
            // The special case the C spells out: `img/1/2: 100` writes the green
            // byte. 1 to 4 are red, green, blue and alpha, and anything else is
            // a bad set.
            assertThat(answerTo("img: make image! [1x1 #{FF0000}] img/1/2: 100 img/1"))
                    .isEqualTo("255.100.0.255");
            assertThat(errorIdFrom("img: make image! 1x1 img/1/5: 100"))
                    .isEqualTo("bad-path-set");
        }
    }

    @Nested
    @DisplayName("the words an image answers to")
    class WordSelectors {

        @Test
        @DisplayName("size, width and height")
        void theShapeWords() {
            assertThat(answerTo("img: make image! 3x2 img/size")).isEqualTo("3x2");
            assertThat(answerTo("img: make image! 3x2 img/width")).isEqualTo("3");
            assertThat(answerTo("img: make image! 3x2 img/height")).isEqualTo("2");
        }

        @Test
        @DisplayName("rgb answers three bytes a pixel and rgba four")
        void theChannelWords() {
            // `Color_To_Bin(QUAD_HEAD(nser), src, len, sym)` for the three-byte
            // forms and four bytes for the alpha ones, from the position rather
            // than from the head.
            assertThat(answerTo("img: make image! [1x1 #{FF8000}] img/rgb"))
                    .isEqualTo("#{FF8000}");
            assertThat(answerTo("img: make image! [1x1 #{FF8000} #{40}] img/rgba"))
                    .isEqualTo("#{FF800040}");
        }

        @Test
        @DisplayName("and alpha answers one byte a pixel")
        void theAlphaWord() {
            assertThat(answerTo("img: make image! [2x1 #{FF0000 00FF00} #{8040}] img/alpha"))
                    .isEqualTo("#{8040}");
        }

        @Test
        @DisplayName("a word it does not know is a bad selector")
        void anUnknownWord() {
            // `return PE_BAD_SELECT` for anything not in the list.
            assertThat(errorIdFrom("img: make image! 1x1 img/nonsense"))
                    .isEqualTo("invalid-path");
        }
    }

    @Nested
    @DisplayName("an image is a series")
    class BeingASeries {

        @Test
        @DisplayName("counted in pixels, from where it stands")
        void countedInPixels() {
            assertThat(answerTo("length? make image! 3x2")).isEqualTo("6");
            assertThat(answerTo("length? skip make image! 3x2 2")).isEqualTo("4");
            assertThat(answerTo("index? skip make image! 3x2 2")).isEqualTo("3");
        }

        @Test
        @DisplayName("and skipping keeps the width, so the image does not change shape")
        void skippingKeepsTheShape() {
            // The width lives beside the data rather than in the position, which
            // is why this is worth pinning: `at img 3` is the same 3x2 image.
            assertThat(answerTo("img: skip make image! 3x2 2 img/size")).isEqualTo("3x2");
        }

        @Test
        @DisplayName("head, tail and the questions about them")
        void theNavigationArms() {
            assertThat(answerTo("index? head skip make image! 3x2 2")).isEqualTo("1");
            assertThat(answerTo("index? tail make image! 3x2")).isEqualTo("7");
            assertThat(answerTo("tail? tail make image! 3x2")).isEqualTo(TRUE);
            assertThat(answerTo("head? make image! 3x2")).isEqualTo(TRUE);
            assertThat(answerTo("empty? make image! 0x0")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("AT takes a pair as a coordinate as well as a number")
        void atTakesAPair() {
            // `diff = ((VAL_PAIR_Y_INT(arg) - 1) * VAL_IMAGE_WIDE(value) +
            // VAL_PAIR_X_INT(arg))` -- the one place a navigation action reads
            // the width.
            assertThat(answerTo("index? at make image! 3x2 2x2")).isEqualTo("5");
        }

        @Test
        @DisplayName("and a position past the end is clamped rather than refused")
        void positionsAreClamped() {
            // `if (index > tail) index = tail; else if (index < 0) index = 0;`
            assertThat(answerTo("index? skip make image! 2x1 99")).isEqualTo("3");
            assertThat(answerTo("index? skip make image! 2x1 -99")).isEqualTo("1");
        }
    }
}
