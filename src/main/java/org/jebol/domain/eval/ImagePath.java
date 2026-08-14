package org.jebol.domain.eval;

import org.jebol.domain.value.*;

/**
 * Reading and writing an image through a path, from {@code PD_Image}.
 *
 * <p>An image takes more kinds of selector than any other series. A number or a
 * pair names a pixel; a word names either the shape or a channel read out as a
 * binary. The pixel forms are the same idea as a block's position and the word
 * forms are not, which is why the image's handler runs before the general series
 * one rather than after it.
 *
 * <p>Two rules here surprise people, and both are one line of the C.
 *
 * <p>Writing an <em>integer</em> to a pixel sets the alpha and keeps the colour:
 * {@code *dp = (*dp & 0xffffff) | (n << 24)}. Writing a tuple sets the colour,
 * and keeps the alpha unless the tuple carried a fourth part.
 *
 * <p>And a pixel's own bytes can be written by number -- {@code img/1/2: 100} is
 * the green byte -- which the C added for one reported issue and guards tightly:
 * {@code n < 1 || n > 4} is a bad set, and so is a third segment.
 *
 * <p>A pixel always reads back as four parts, alpha included, which is the third
 * surprise and the one that looks like a bug. {@code Set_Tuple_Pixel} writes
 * {@code VAL_TUPLE_LEN(tuple) = 4} before it writes a byte, so a white pixel is
 * {@code 255.255.255.255} rather than {@code 255.255.255}, and a script comparing
 * {@code img/1} against a three-part colour never matches.
 */
final class ImagePath {

    private ImagePath() {
    }

    /** What a path segment answers, or none where the pixel is not there. */
    static Value read(ImageValue image, Value selector) {
        if (selector instanceof WordValue named) {
            return aboutTheImage(image, named);
        }
        int pixel = pixelNamedBy(image, selector);
        if (pixel < 1 || pixel > image.lengthFromHere()) {
            return NoneValue.none();
        }
        int[] channels = image.pixelAt(pixel);
        return TupleValue.of(channels[0], channels[1], channels[2], channels[3]);
    }

    /**
     * Writes one pixel, given a position already worked out from the head.
     *
     * <p>Called from the series write path, which has resolved the offset
     * against the image's own position first.
     */
    static void write(ImageValue image, int pixelFromHead, Value written) {
        ImageStorage storage = image.storage();
        if (pixelFromHead < 1 || pixelFromHead > storage.length()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "pixel " + pixelFromHead + " is outside the image");
        }
        if (written instanceof TupleValue colour) {
            int[] parts = colour.segments();
            storage.setColourAt(pixelFromHead,
                    partOr(parts, 0, 0), partOr(parts, 1, 0), partOr(parts, 2, 0));
            if (parts.length > 3) {
                storage.setAlphaAt(pixelFromHead, parts[3]);
            }
            return;
        }
        int alpha = alphaFrom(written);
        storage.setAlphaAt(pixelFromHead, alpha);
    }

    /**
     * Writes one byte of one pixel: `img/1/2: 100`.
     *
     * <p>The C added this for one reported issue and guards it tightly. The
     * channel is 1 to 4 -- red, green, blue, alpha -- and anything else is a bad
     * set, as is a value that is not a byte:
     *
     * <pre>
     * if (!IS_END(pvs->path+1) || n &lt; 1 || n &gt; 4) return PE_BAD_SET;
     * </pre>
     */
    static void writeOneChannel(
            ImageValue image, Value pixelSegment, int channel, Value written) {
        if (channel < 1 || channel > 4) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a pixel has four bytes, so " + channel + " names none of them");
        }
        if (!(written instanceof IntegerValue octet)
                || octet.magnitude() < 0 || octet.magnitude() > 255) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "one byte of a pixel holds 0 to 255");
        }
        int pixel = pixelNamedBy(image, pixelSegment);
        if (pixel < 1 || pixel > image.lengthFromHere()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "pixel " + pixel + " is outside the image");
        }
        image.storage().setChannelAt(image.index() + pixel - 1, channel,
                (int) octet.magnitude());
    }

    /**
     * Which pixel a selector names, counted from the image's position.
     *
     * <p>A pair is a coordinate and the arithmetic is the C's:
     * {@code n = ((y - 1) * wide + (x - 1)) + 1}. A decimal is truncated and a
     * logic answers the first pixel or the second, which is the same rule every
     * series position follows.
     */
    private static int pixelNamedBy(ImageValue image, Value selector) {
        return switch (selector) {
            case PairValue coordinate -> ((int) coordinate.y() - 1) * image.storage().wide()
                    + ((int) coordinate.x() - 1) + 1;
            case IntegerValue number -> (int) number.magnitude();
            case DecimalValue number -> (int) number.quantity();
            case LogicValue yesOrNo -> yesOrNo.isTruthy() ? 1 : 2;
            default -> 0;
        };
    }

    private static int partOr(int[] parts, int at, int otherwise) {
        return at < parts.length ? parts[at] : otherwise;
    }

    /**
     * A value as an alpha byte, refusing anything that is not one.
     *
     * <p>{@code if (IS_INTEGER(val) && VAL_INT64(val) >= 0 && VAL_INT64(val) <=
     * 255)}, or a char below 256, and {@code else return PE_BAD_ARGUMENT} --
     * which is `invalid-arg` rather than a bad path set, because the path is
     * fine and the value is not.
     */
    private static int alphaFrom(Value written) {
        long octet = switch (written) {
            case IntegerValue number -> number.magnitude();
            case CharacterValue letter -> letter.codepoint();
            default -> -1;
        };
        if (octet < 0 || octet > 255) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a pixel takes a colour tuple or an alpha byte, not "
                            + written.datatype().literalSpelling());
        }
        return (int) octet;
    }

    /**
     * The words an image answers to: its shape, and its channels as binaries.
     *
     * <p>Twenty-one of them in the C, and they fall into three groups. SIZE,
     * WIDTH and HEIGHT are the shape, and they describe the whole image rather
     * than what is left from the position. The colour words answer a binary of
     * the remaining pixels in the order they name -- RGB is three bytes a pixel,
     * RGBA four, and the reversed and alpha-first spellings are the same bytes
     * rearranged. ALPHA answers one byte a pixel.
     *
     * <p>Anything else is {@code PE_BAD_SELECT}, which reads as `invalid-path`.
     */
    private static Value aboutTheImage(ImageValue image, WordValue named) {
        return switch (named.canonical()) {
            case "size" -> image.size();
            case "width" -> IntegerValue.of(image.storage().wide());
            case "height" -> IntegerValue.of(image.storage().high());
            case "rgb" -> channels(image, 0, 1, 2);
            case "bgr" -> channels(image, 2, 1, 0);
            case "rgba", "rgbo" -> channels(image, 0, 1, 2, 3);
            case "bgra", "bgro" -> channels(image, 2, 1, 0, 3);
            case "argb", "orgb" -> channels(image, 3, 0, 1, 2);
            case "abgr", "obgr" -> channels(image, 3, 2, 1, 0);
            case "alpha", "opacity" -> channels(image, 3);
            case "red" -> channels(image, 0);
            case "green" -> channels(image, 1);
            case "blue" -> channels(image, 2);
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH, named.spelling());
        };
    }

    /**
     * The named channels of every remaining pixel, as one binary.
     *
     * <p>{@code Color_To_Bin(QUAD_HEAD(nser), src, len, sym)} where {@code src}
     * is {@code VAL_IMAGE_DATA} -- from the position, not from the head.
     */
    private static BinaryValue channels(ImageValue image, int... order) {
        int pixels = image.lengthFromHere();
        int[] octets = new int[pixels * order.length];
        int written = 0;
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int[] channels = image.pixelAt(pixel);
            for (int which : order) {
                octets[written++] = channels[which];
            }
        }
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }
}
