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
            storage.setAlphaAt(pixelFromHead, partOr(parts, 3, WHOLLY));
            return;
        }
        int alpha = alphaFrom(written);
        storage.setAlphaAt(pixelFromHead, alpha);
    }

    /**
     * Writes through a path on an image, whichever kind of selector it is.
     *
     * <p>A word names a field of the whole picture and fills or reshapes it. A
     * pair or a number names one pixel, counted from the image's own position,
     * and a position the image does not reach is a bad set rather than a
     * silent nothing -- {@code if (val) return PE_BAD_SET;} is what the C says
     * when the index is out of range and there is something to write.
     */
    static void writeThroughPath(ImageValue image, Value selector, Value written) {
        if (selector instanceof WordValue named) {
            writeTheWholePicture(image, named, written);
            return;
        }
        int pixel = pixelNamedBy(image, selector);
        if (pixel < 1 || pixel > image.lengthFromHere()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "pixel " + pixel + " is outside the image");
        }
        write(image, image.index() + pixel - 1, written);
    }

    /**
     * A named field of the picture, set rather than read.
     *
     * <p>RGB and COLOR are the same setter -- the C says so in a comment, and
     * fills every remaining pixel from a tuple, or from a number read as a
     * grey. The four-letter spellings take a four-part tuple and set the alpha
     * too. A single channel takes a number. SIZE takes a pair and reshapes
     * without moving a byte, the height being however many whole rows the
     * pixels there make.
     */
    private static void writeTheWholePicture(
            ImageValue image, WordValue named, Value written) {

        switch (named.canonical()) {
            case "size" -> reshapeTo(image, written);
            case "rgb", "color" -> fillEveryPixel(image, written, 0, 1, 2);
            case "rgba", "rgbo" -> fillEveryPixel(image, written, 0, 1, 2, 3);
            case "bgra", "bgro" -> fillEveryPixel(image, written, 2, 1, 0, 3);
            case "argb", "orgb" -> fillEveryPixel(image, written, 3, 0, 1, 2);
            case "abgr", "obgr" -> fillEveryPixel(image, written, 3, 2, 1, 0);
            case "luminosity", "gray" -> fillTheSameByteEverywhere(image, written);
            case "alpha" -> fillOneChannel(image, written, 4);
            case "opacity" -> fillTheOpacity(image, written);
            case "red" -> fillOneChannel(image, written, 1);
            case "green" -> fillOneChannel(image, written, 2);
            case "blue" -> fillOneChannel(image, written, 3);
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH, named.spelling());
        }
    }

    private static void reshapeTo(ImageValue image, Value written) {
        if (!(written instanceof PairValue asked) || (int) asked.x() == 0) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "an image is sized by a pair with a width");
        }
        int across = (int) asked.x();
        image.storage().reshape(across,
                Math.min((int) asked.y(), image.storage().length() / across));
    }

    /**
     * Fills the remaining pixels, from a colour or from a run of bytes.
     *
     * <p>RGB and COLOR leave the alpha where it was --
     * {@code Fill_Line(..., only)} with {@code only} true masks it off -- so a
     * four-part tuple written to either of them loses its fourth part. The
     * four-letter spellings set all four.
     *
     * <p>A binary or a byte vector is not one colour but a picture's worth of
     * them, laid over the pixels in the order the field names, and it stops at
     * whichever of the two runs out first.
     */
    private static void fillEveryPixel(
            ImageValue image, Value written, int... order) {

        if (bytesOf(written) instanceof int[] laid) {
            layOver(image, laid, order);
            return;
        }
        int[] parts = written instanceof TupleValue colour
                ? colour.segments()
                : greyParts(byteOrRefuse(written), order.length);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setColourAt(pixel,
                    partOr(parts, 0, 0), partOr(parts, 1, 0), partOr(parts, 2, 0));
            if (order.length > 3) {
                image.storage().setAlphaAt(pixel, partOr(parts, 3, 0xFF));
            }
        }
    }

    private static int[] greyParts(int grey, int channels) {
        return channels > 3
                ? new int[] {grey, grey, grey, grey}
                : new int[] {grey, grey, grey};
    }

    /**
     * LUMINOSITY and GRAY set the three colour channels to one byte each.
     *
     * <p>A run of bytes gives each pixel its own grey and leaves the alpha
     * where it was; a single number is
     * {@code Fill_Line(..., TO_PIXEL_COLOR(n, n, n, n), FALSE)}, which writes
     * that number into all four channels including the alpha.
     */
    private static void fillTheSameByteEverywhere(ImageValue image, Value written) {
        if (bytesOf(written) instanceof int[] greys) {
            int pixels = Math.min(greys.length, image.lengthFromHere());
            for (int step = 0; step < pixels; step++) {
                image.storage().setColourAt(image.index() + step,
                        greys[step], greys[step], greys[step]);
            }
            return;
        }
        int grey = byteOrRefuse(written);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setColourAt(pixel, grey, grey, grey);
            image.storage().setAlphaAt(pixel, grey);
        }
    }

    /** The alpha, written back to front, which is what opacity means here. */
    private static void fillTheOpacity(ImageValue image, Value written) {
        if (bytesOf(written) instanceof int[] laid) {
            int pixels = Math.min(laid.length, image.lengthFromHere());
            for (int step = 0; step < pixels; step++) {
                image.storage().setAlphaAt(image.index() + step, WHOLLY - laid[step]);
            }
            return;
        }
        int octet = byteOrRefuse(written);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setAlphaAt(pixel, WHOLLY - octet);
        }
    }

    /** One byte per pixel over one channel, from a number or a run of bytes. */
    private static void fillOneChannel(ImageValue image, Value written, int channel) {
        if (bytesOf(written) instanceof int[] laid) {
            int pixels = Math.min(laid.length, image.lengthFromHere());
            for (int step = 0; step < pixels; step++) {
                image.storage().setChannelAt(image.index() + step, channel, laid[step]);
            }
            return;
        }
        int octet = byteOrRefuse(written);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setChannelAt(pixel, channel, octet);
        }
    }

    /** Lays a run of bytes over the pixels, so many to each, in a named order. */
    private static void layOver(ImageValue image, int[] laid, int[] order) {
        int pixels = Math.min(laid.length / order.length, image.lengthFromHere());
        for (int step = 0; step < pixels; step++) {
            for (int which = 0; which < order.length; which++) {
                image.storage().setChannelAt(image.index() + step,
                        order[which] + 1, laid[step * order.length + which]);
            }
        }
    }

    /**
     * The bytes a value hands over, or null when it is not a run of bytes.
     *
     * <p>A binary or a vector of single bytes, which the C tests for together:
     * {@code (IS_VECTOR(val) && VAL_VEC_WIDTH(val) == 1) || IS_BINARY(val)}.
     */
    private static int[] bytesOf(Value written) {
        if (written instanceof BinaryValue binary) {
            int[] octets = new int[binary.lengthFromHere()];
            for (int at = 0; at < octets.length; at++) {
                octets[at] = binary.storage().at(binary.index() + at);
            }
            return octets;
        }
        if (written instanceof VectorValue vector && vector.kind().bytes() == 1) {
            java.util.List<Value> numbers = vector.remaining();
            int[] octets = new int[numbers.size()];
            for (int at = 0; at < octets.length; at++) {
                octets[at] = numbers.get(at) instanceof IntegerValue number
                        ? (int) number.magnitude() & 0xFF
                        : 0;
            }
            return octets;
        }
        return null;
    }

    private static int byteOrRefuse(Value written) {
        if (!(written instanceof IntegerValue number)) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a whole picture is filled from a tuple or a number");
        }
        if (number.magnitude() < 0 || number.magnitude() > 255) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a colour byte holds 0 to 255");
        }
        return (int) number.magnitude();
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
            case "alpha" -> channels(image, 3);
            case "opacity" -> opacitiesOf(image);
            case "red" -> channels(image, 0);
            case "green" -> channels(image, 1);
            case "blue" -> channels(image, 2);
            case "color" -> averageColourOf(image);
            case "luminosity" -> oneBytePerPixel(image, ImagePath::luminosityOf);
            case "gray" -> oneBytePerPixel(image, ImagePath::greyOf);
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH, named.spelling());
        };
    }

    /**
     * COLOR, which is every remaining pixel averaged into one.
     *
     * <p>{@code Average_Image_Color} sums each channel over the pixels from the
     * position and divides by how many there were, integer division and no
     * rounding. An image with no pixels left answers transparent black, which
     * the C's own comment is unsure about and does anyway.
     */
    private static Value averageColourOf(ImageValue image) {
        int pixels = image.lengthFromHere();
        if (pixels == 0) {
            return TupleValue.of(0, 0, 0, 0);
        }
        long red = 0;
        long green = 0;
        long blue = 0;
        long alpha = 0;
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int[] channels = image.pixelAt(pixel);
            red += channels[0];
            green += channels[1];
            blue += channels[2];
            alpha += channels[3];
        }
        return TupleValue.of((int) (red / pixels), (int) (green / pixels),
                (int) (blue / pixels), (int) (alpha / pixels));
    }

    /**
     * OPACITY, which is not another name for ALPHA but its opposite.
     *
     * <p>{@code *bin++ = 255 - rgba[C_A]} reading, and
     * {@code rgba[C_A] = 255 - *bin++} writing. A pixel that is fully opaque
     * has an alpha of 255 and an opacity of nothing, which reads backwards
     * until you notice R3 stores the byte as transparency.
     */
    private static final int WHOLLY = 255;

    private static BinaryValue opacitiesOf(ImageValue image) {
        int pixels = image.lengthFromHere();
        int[] octets = new int[pixels];
        for (int pixel = 1; pixel <= pixels; pixel++) {
            octets[pixel - 1] = WHOLLY - image.pixelAt(pixel)[3];
        }
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }

    /**
     * The two ways of squeezing a pixel down to one byte, which are two
     * different sums and not two names for one.
     *
     * <p>LUMINOSITY weights the channels the way the eye reads them and GRAY
     * simply averages them; both ignore the alpha and both truncate. The C
     * knows a third, LUMA, weighted for the older television standard -- but
     * only the encoder can reach it, and a path saying {@code /luma} is an
     * invalid path.
     */
    private static int luminosityOf(int red, int green, int blue) {
        return (int) (0.2126 * red + 0.7152 * green + 0.0722 * blue);
    }

    private static int greyOf(int red, int green, int blue) {
        return (red + green + blue) / 3;
    }

    /** One byte for each remaining pixel, worked out however the caller says. */
    private static BinaryValue oneBytePerPixel(
            ImageValue image, ThreeChannelsToOne squeeze) {

        int pixels = image.lengthFromHere();
        int[] octets = new int[pixels];
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int[] channels = image.pixelAt(pixel);
            octets[pixel - 1] = squeeze.of(channels[0], channels[1], channels[2]);
        }
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }

    @FunctionalInterface
    private interface ThreeChannelsToOne {
        int of(int red, int green, int blue);
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
