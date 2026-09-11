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

    /**
     * One pixel as four channels, from whatever a caller wrote it as.
     *
     * <p>A tuple of three is a colour that is wholly opaque, a tuple of four
     * carries its own alpha, and a whole number on its own is an alpha with no
     * colour -- which is the spelling that lets a caller say "find a
     * transparent pixel" without naming one.
     */
    private static int[] asAPixel(Value written) {
        if (written instanceof TupleValue colour) {
            int[] parts = colour.segments();
            return new int[] {
                part(parts, 0), part(parts, 1), part(parts, 2),
                parts.length > 3 ? parts[3] : WHOLLY
            };
        }
        if (written instanceof IntegerValue alpha) {
            return new int[] {0, 0, 0, (int) alpha.magnitude() & 0xFF};
        }
        return null;
    }

    private static int part(int[] parts, int which) {
        return which < parts.length ? parts[which] : 0;
    }

    /**
     * The pixels a caller named, whether singly or in a block, refused as a
     * whole if any one of them is not a pixel.
     *
     * <p>Refused as a whole because a half-applied APPEND is worse than a
     * refused one: REBOL's own test writes a string at an image and then
     * checks the length has not moved.
     */
    private static List<int[]> everyPixelIn(Value given, String verb) {
        List<int[]> pixels = new ArrayList<>();
        List<Value> named = given instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                ? block.remaining()
                : List.of(given);
        for (Value one : named) {
            int[] pixel = asAPixel(one);
            if (pixel == null) {
                throw Raised.of(EvaluationFailure.CANNOT_USE,
                        WordValue.of(verb),
                        WordValue.of(one.datatype().literalSpelling()));
            }
            pixels.add(pixel);
        }
        return pixels;
    }

    /**
     * Adds pixels at the tail and answers the image at its head, which is what
     * APPEND answers on every series.
     */
    static Value appended(ImageValue image, Value given, long howManyTimes) {
        List<int[]> pixels = everyPixelIn(given, "append");
        ImageStorage storage = image.storage();
        for (long again = 0; again < howManyTimes; again++) {
            for (int[] pixel : pixels) {
                storage.insertAt(storage.length() + 1,
                        pixel[0], pixel[1], pixel[2], pixel[3]);
            }
        }
        return image.head();
    }

    /**
     * Puts pixels in at the position and answers the image just past them,
     * which is what INSERT answers on every series.
     */
    static Value inserted(ImageValue image, Value given, long howManyTimes) {
        List<int[]> pixels = everyPixelIn(given, "insert");
        ImageStorage storage = image.storage();
        int at = image.index();
        for (long again = 0; again < howManyTimes; again++) {
            for (int[] pixel : pixels) {
                storage.insertAt(at, pixel[0], pixel[1], pixel[2], pixel[3]);
                at++;
            }
        }
        return image.atIndex(at);
    }

    /**
     * Writes over the pixels that are there and answers the image just past
     * what was written.
     *
     * <p>The image does not get longer. CHANGE replaces what is at the
     * position on every series, and an image has nowhere to put the overflow:
     * its width is fixed, so a longer one would be a different shape. Pixels
     * past the end are dropped.
     */
    static Value changed(ImageValue image, Value given, long howManyTimes) {
        if (given instanceof ImageValue rectangle) {
            return rectangleWritten(image, rectangle, howManyTimes);
        }
        List<int[]> pixels = everyPixelIn(given, "change");
        ImageStorage storage = image.storage();
        int at = image.index();
        for (long again = 0; again < howManyTimes; again++) {
            for (int[] pixel : pixels) {
                if (at > storage.length()) {
                    return image.atIndex(at);
                }
                storage.setColourAt(at, pixel[0], pixel[1], pixel[2]);
                storage.setAlphaAt(at, pixel[3]);
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
     */
    private static Value rectangleWritten(
            ImageValue image, ImageValue rectangle, long howManyTimes) {

        ImageStorage storage = image.storage();
        if (storage.wide() == 0 || howManyTimes == 0) {
            return image;
        }
        int column = (image.index() - 1) % storage.wide();
        int row = (image.index() - 1) / storage.wide();
        int columns = Math.min(rectangle.storage().wide(), storage.wide() - column);
        int rows = Math.min(rectangle.storage().high(), storage.high() - row);
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
