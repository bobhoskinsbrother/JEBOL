package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * An image used as what it is: a run of pixels with a width beside it.
 *
 * <p>{@code t-image.c}. {@code QUAD_SKIP} turns a pixel index into a byte
 * offset and nothing else about navigating one is special, so APPEND, INSERT,
 * CHANGE and FIND mean on an image what they mean on a block. This is the one
 * place that knows what counts as a pixel, so the natives themselves do not
 * have to.
 *
 * <p>The height is never set here. It is how many whole rows the pixels make,
 * and the storage works it out after every change -- so three pixels in an
 * image two wide are a row and a spare, and the next pixel appended completes
 * the row and lifts the height.
 */
final class ImageSeries {

    private ImageSeries() {
    }

    /** What a pixel with no alpha given gets, which is wholly opaque. */
    private static final int WHOLLY = 0xFF;

    /** How many bytes of a binary make one pixel, which is all four channels. */
    private static final int CHANNELS = 4;

    /**
     * One pixel's worth of change: the channels to write, and which of them to
     * write at all.
     *
     * <p>Not every write touches a whole pixel. A whole number on its own is
     * an alpha and reaches nothing else -- {@code Fill_Channel_Line} writes
     * that one byte and steps over the other three -- and /ONLY asks for the
     * opposite, a colour with the alpha left as it was. Carrying the two
     * questions separately is what keeps a caller's transparency from being
     * silently rewritten by a colour that never mentioned it.
     */
    private record APixelWrite(int red, int green, int blue, int alpha,
            boolean writesTheColour, boolean writesTheAlpha) {

        static APixelWrite ofAColourAndAnAlpha(int red, int green, int blue, int alpha) {
            return new APixelWrite(red, green, blue, alpha, true, true);
        }

        static APixelWrite ofAColourAlone(int red, int green, int blue) {
            return new APixelWrite(red, green, blue, 0, true, false);
        }

        static APixelWrite ofAnAlphaAlone(int alpha) {
            return new APixelWrite(0, 0, 0, alpha, false, true);
        }

        void into(ImageStorage storage, int pixel) {
            if (writesTheColour) {
                storage.setColourAt(pixel, red, green, blue);
            }
            if (writesTheAlpha) {
                storage.setAlphaAt(pixel, alpha);
            }
        }
    }

    /**
     * One pixel's worth of change, from whatever a caller wrote it as.
     *
     * <p>A tuple of three is a colour that is wholly opaque, a tuple of four
     * carries its own alpha, and a whole number on its own is an alpha with no
     * colour -- which is the spelling that lets a caller say "find a
     * transparent pixel" without naming one.
     */
    private static APixelWrite asAPixel(Value written, boolean colourOnly) {
        if (written instanceof TupleValue colour) {
            int[] parts = colour.segments();
            if (colourOnly) {
                return APixelWrite.ofAColourAlone(
                        part(parts, 0), part(parts, 1), part(parts, 2));
            }
            return APixelWrite.ofAColourAndAnAlpha(
                    part(parts, 0), part(parts, 1), part(parts, 2),
                    parts.length > 3 ? parts[3] : WHOLLY);
        }
        if (written instanceof IntegerValue alpha) {
            return APixelWrite.ofAnAlphaAlone((int) alpha.magnitude() & 0xFF);
        }
        return null;
    }

    private static int part(int[] parts, int which) {
        return which < parts.length ? parts[which] : 0;
    }

    /**
     * The pixels a caller named, refused as a whole if any one of them is not
     * a pixel.
     *
     * <p>Four ways of naming them. A colour or an alpha on its own, a block
     * holding any number of those, or a run of bytes -- and the bytes are four
     * to a pixel, which is the other way round from building an image.
     * {@code make image! [1x1 #{FFFFFF}]} reads three at a time because the
     * alpha arrives in a run of its own, and a binary written here reads four
     * because there is nowhere else for the alpha to come from.
     *
     * <p>Refused as a whole because a half-applied APPEND is worse than a
     * refused one: REBOL's own test writes a string at an image and then
     * checks the length has not moved.
     */
    private static List<APixelWrite> everyPixelIn(Value given, boolean colourOnly) {
        if (given instanceof BinaryValue bytes) {
            return theBytesReadFourAtATime(bytes, colourOnly);
        }
        List<APixelWrite> pixels = new ArrayList<>();
        List<Value> named = given instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                ? block.remaining()
                : List.of(given);
        for (Value one : named) {
            APixelWrite pixel = asAPixel(one, colourOnly);
            if (pixel == null) {
                throw Raised.of(EvaluationFailure.INVALID_TYPE,
                        DatatypeValue.of(one.datatype()));
            }
            pixels.add(pixel);
        }
        return pixels;
    }

    private static List<APixelWrite> theBytesReadFourAtATime(
            BinaryValue bytes, boolean colourOnly) {

        List<APixelWrite> pixels = new ArrayList<>();
        int howMany = bytes.lengthFromHere() / CHANNELS;
        for (int pixel = 0; pixel < howMany; pixel++) {
            int at = bytes.index() + (pixel * CHANNELS);
            int red = bytes.storage().at(at);
            int green = bytes.storage().at(at + 1);
            int blue = bytes.storage().at(at + 2);
            pixels.add(colourOnly
                    ? APixelWrite.ofAColourAlone(red, green, blue)
                    : APixelWrite.ofAColourAndAnAlpha(
                            red, green, blue, bytes.storage().at(at + 3)));
        }
        return pixels;
    }

    /**
     * Adds pixels at the tail and answers the image at its head, which is what
     * APPEND answers on every series.
     */
    static Value appended(ImageValue image, Value given, long howManyTimes) {
        List<APixelWrite> pixels = everyPixelIn(given, WRITES_THE_ALPHA_TOO);
        ImageStorage storage = image.storage();
        for (long again = 0; again < howManyTimes; again++) {
            for (APixelWrite pixel : pixels) {
                grownWhiteAndThenWritten(storage, storage.length() + 1, pixel);
            }
        }
        return image.head();
    }

    /**
     * Puts pixels in at the position and answers the image just past them,
     * which is what INSERT answers on every series.
     */
    static Value inserted(ImageValue image, Value given, long howManyTimes) {
        List<APixelWrite> pixels = everyPixelIn(given, WRITES_THE_ALPHA_TOO);
        ImageStorage storage = image.storage();
        int at = image.index();
        for (long again = 0; again < howManyTimes; again++) {
            for (APixelWrite pixel : pixels) {
                grownWhiteAndThenWritten(storage, at, pixel);
                at++;
            }
        }
        return image.atIndex(at);
    }

    /**
     * A new pixel is made first and written into second, which is why an alpha
     * on its own leaves a white pixel behind rather than a black one.
     *
     * <p>{@code Expand_Series} then {@code CLEAR_IMAGE} then the fill: the C
     * makes room, whitens it, and only then writes whichever channels were
     * named. So `append img 7` adds an almost-invisible white pixel, and
     * nothing anywhere decided that -- it falls out of the order.
     */
    private static void grownWhiteAndThenWritten(
            ImageStorage storage, int at, APixelWrite pixel) {

        storage.insertAt(at, WHOLLY, WHOLLY, WHOLLY, WHOLLY);
        pixel.into(storage, at);
    }

    /** Growing an image writes every channel, so /ONLY never reaches it. */
    private static final boolean WRITES_THE_ALPHA_TOO = false;

    /**
     * Writes over the pixels that are there and answers the image just past
     * what was written.
     *
     * <p>The image does not get longer. CHANGE replaces what is at the
     * position on every series, and an image has nowhere to put the overflow:
     * its width is fixed, so a longer one would be a different shape. Pixels
     * past the end are dropped.
     */
    static Value changed(ImageValue image, Value given, long howManyTimes,
            boolean colourOnly, Value shapeOfTheRectangle) {

        if (given instanceof ImageValue rectangle) {
            return rectangleWritten(image, rectangle, howManyTimes,
                    shapeOfTheRectangle);
        }
        List<APixelWrite> pixels = everyPixelIn(given, colourOnly);
        ImageStorage storage = image.storage();
        int at = image.index();
        for (long again = 0; again < howManyTimes; again++) {
            for (APixelWrite pixel : pixels) {
                if (at > storage.length()) {
                    return image.atIndex(at);
                }
                pixel.into(storage, at);
                at++;
            }
        }
        return image.atIndex(at);
    }

    /**
     * One image written into another, which is the one thing CHANGE lays down
     * as a block rather than as a run.
     *
     * <p>{@code Copy_Rect_Data}. Every other thing CHANGE accepts is pixels one
     * after another, wrapping from the end of a row to the start of the next.
     * An image is not, because an image has a shape: its top-left corner goes
     * where the position points and each of its rows lands on one row of the
     * target, so what will not fit on a row is dropped rather than spilled onto
     * the next.
     *
     * <p>The step taken afterwards is one pixel, not the size of what was
     * written -- {@code index + dup * part} with {@code part} left at one,
     * because one image is one thing however many pixels it carries. /DUP
     * multiplies that step and nothing else, so the rectangle goes in once
     * however many times it was asked for.
     *
     * <p>/PART says how big the rectangle is rather than how many pixels to
     * write, which is the only reading a shape allows: two across and two down
     * is four pixels, and "four" would not say which four. A count given where
     * a shape belongs writes nothing at all -- the C reads it into the
     * variable holding how many things were given and leaves the rectangle at
     * nought by nought.
     */
    private static Value rectangleWritten(ImageValue image, ImageValue rectangle,
            long howManyTimes, Value shapeOfTheRectangle) {

        ImageStorage storage = image.storage();
        if (storage.wide() == 0 || howManyTimes == 0) {
            return image;
        }
        int column = (image.index() - 1) % storage.wide();
        int row = (image.index() - 1) / storage.wide();
        int wanted = rectangle.storage().wide();
        int tall = rectangle.storage().high();
        if (shapeOfTheRectangle instanceof PairValue shape) {
            wanted = Math.max(0, (int) shape.x());
            tall = Math.max(0, (int) shape.y());
        } else if (!(shapeOfTheRectangle instanceof NoneValue)) {
            wanted = 0;
            tall = 0;
        }
        int columns = Math.min(wanted, storage.wide() - column);
        int rows = Math.min(tall, storage.high() - row);
        for (int down = 0; down < rows; down++) {
            for (int across = 0; across < columns; across++) {
                int[] pixel = rectangle.storage()
                        .pixelAt((down * rectangle.storage().wide()) + across + 1);
                int into = ((row + down) * storage.wide()) + column + across + 1;
                storage.setColourAt(into, pixel[0], pixel[1], pixel[2]);
                storage.setAlphaAt(into, pixel[3]);
            }
        }
        return image.standingAt(image.index() + (int) howManyTimes);
    }

    /**
     * A rectangle taken out of an image as a picture of its own, which is what
     * COPY/PART means when the part is a shape rather than a count.
     *
     * <p>The corner is where the image stands, read as a column and a row the
     * same way CHANGE reads one, and the shape is clipped to what is left of
     * the row and of the picture below it. So a rectangle asked for at the
     * last column comes back one wide, and one asked for larger than the
     * picture comes back as the picture.
     */
    static Value rectangleCopiedFrom(ImageValue image, int wanted, int tall) {
        ImageStorage storage = image.storage();
        int from = Math.min(image.index() - 1, storage.length());
        int column = storage.wide() == 0 ? 0 : from % storage.wide();
        int row = storage.wide() == 0 ? 0 : from / storage.wide();
        int columns = Math.min(Math.max(wanted, 0), storage.wide() - column);
        int rows = Math.min(Math.max(tall, 0), storage.high() - row);
        ImageStorage into = ImageStorage.of(columns, Math.max(rows, 0));
        for (int down = 0; down < rows; down++) {
            for (int across = 0; across < columns; across++) {
                int[] pixel = storage.pixelAt(
                        ((row + down) * storage.wide()) + column + across + 1);
                int at = (down * columns) + across + 1;
                into.setColourAt(at, pixel[0], pixel[1], pixel[2]);
                into.setAlphaAt(at, pixel[3]);
            }
        }
        return new ImageValue(into, 1);
    }

    /**
     * Whether the pixel at a position is the one a caller asked for.
     *
     * <p>A tuple of three matches a colour whatever its alpha, because that is
     * what a caller who wrote no alpha meant. A tuple of four matches the
     * alpha too, and a whole number matches nothing but the alpha.
     *
     * <p>/ONLY drops the alpha from the comparison even where a tuple of four
     * named one, which reads oddly until you notice what it means everywhere
     * else: look at the thing itself and not into it. A pixel's colour is the
     * thing; its alpha is how much of it shows.
     */
    static boolean pixelMatches(ImageValue image, int oneBasedPixel,
            Value wanted, boolean colourOnly) {

        if (oneBasedPixel < 1 || oneBasedPixel > image.storage().length()) {
            return false;
        }
        int[] there = image.storage().pixelAt(oneBasedPixel);
        if (wanted instanceof IntegerValue alpha) {
            return there[3] == ((int) alpha.magnitude() & 0xFF);
        }
        if (!(wanted instanceof TupleValue colour)) {
            return false;
        }
        int[] parts = colour.segments();
        for (int channel = 0; channel < 3; channel++) {
            if (there[channel] != part(parts, channel)) {
                return false;
            }
        }
        return colourOnly || parts.length <= 3 || there[3] == parts[3];
    }

    /**
     * Where the first pixel matching is, counted from one, or nought for
     * nowhere.
     *
     * <p>/MATCH asks a different question -- whether the pixels *at* the
     * position are the ones named -- so it never looks past where it started.
     */
    static int positionOf(ImageValue image, Value wanted,
            boolean onlyAtTheStart, boolean colourOnly) {

        if (onlyAtTheStart) {
            return pixelMatches(image, image.index(), wanted, colourOnly)
                    ? image.index()
                    : 0;
        }
        for (int at = image.index(); at <= image.storage().length(); at++) {
            if (pixelMatches(image, at, wanted, colourOnly)) {
                return at;
            }
        }
        return 0;
    }

    /** Whether a value could name a pixel at all, which is what FIND asks. */
    static boolean couldBeAPixel(Value wanted) {
        return wanted instanceof TupleValue || wanted instanceof IntegerValue;
    }
}
