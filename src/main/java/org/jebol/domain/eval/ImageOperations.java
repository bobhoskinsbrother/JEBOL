package org.jebol.domain.eval;

import org.jebol.domain.value.ImageStorage;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.PairValue;

/**
 * The four things {@code n-image.c} does to a whole image.
 *
 * <p>Whole rather than from a position, which is the one thing all four share
 * and the C flags in a comment of its own: "All pixels are modified even when
 * the input image is not at its head!" So an image standing at its third pixel
 * is still blurred, premultiplied and compared from its first.
 *
 * <p>Three of the four change the image they were given and answer it back, so
 * a caller holding the value sees the change. RESIZE is the exception: it
 * makes a new image because the old one is the wrong size to hold the answer.
 */
final class ImageOperations {

    private ImageOperations() {
    }

    /** What a fully opaque pixel has, and the divisor the C scales by. */
    private static final int OPAQUE = 0xFF;

    /**
     * Scales each colour by the pixel's own alpha, in place.
     *
     * <p>What a renderer wants before it composites: a half-transparent red
     * stored as a full red plus an alpha has to become a half red, or
     * blending it over a background counts the red twice. A fully opaque
     * pixel is skipped rather than multiplied by one, which is the C's own
     * shortcut and gives the same answer.
     */
    static void premultiply(ImageValue image) {
        ImageStorage storage = image.storage();
        for (int pixel = 1; pixel <= storage.length(); pixel++) {
            int[] rgba = storage.pixelAt(pixel);
            int alpha = rgba[3];
            if (alpha == OPAQUE) {
                continue;
            }
            storage.setColourAt(pixel,
                    (rgba[0] * alpha) / OPAQUE,
                    (rgba[1] * alpha) / OPAQUE,
                    (rgba[2] * alpha) / OPAQUE);
        }
    }

    /**
     * Blurs an image in place, by a radius in pixels.
     *
     * <p>A radius of zero or less does nothing at all -- {@code if (radius >
     * 0) BlurImage(...)} -- so a caller passing a computed radius that came
     * out negative gets its image back untouched rather than an error. A
     * radius wider than the picture is brought down to half its shorter side,
     * so no radius is ever too large to ask for: a hundred thousand gives the
     * most blurred the picture can be.
     *
     * <p>{@code u-image-blur.c}, which is Ivan Kuckir's three-box
     * approximation. A box blur replaces each pixel by the plain average of
     * its neighbours in a line, which is cheap and looks wrong; three of them
     * in a row look almost exactly like a Gaussian and cost the same. That is
     * the whole trick, and it is why the cost stays proportional to the radius
     * where a real Gaussian kernel costs its square.
     */
    static void blur(ImageValue image, int radius) {
        ImageStorage storage = image.storage();
        if (radius <= 0 || storage.wide() == 0 || storage.high() == 0) {
            return;
        }
        int wide = storage.wide();
        int high = storage.high();
        int spread = Math.min(radius, Math.min(wide / 2, high / 2));
        byte[] picture = channelsOf(storage, wide, high);
        byte[] working = new byte[picture.length];
        for (int boxWidth : boxWidthsMatching(spread)) {
            boxBlur(picture, working, wide, high, (boxWidth - 1) / 2);
        }
        writeChannelsBack(storage, picture, wide, high);
    }

    /**
     * The three box widths whose combined spread matches the Gaussian that was
     * asked for.
     *
     * <p>They are not all the same, and which of the three is the odd one out
     * depends on the radius: the ideal width is rarely a whole odd number, so
     * some of the boxes take the odd number below it and the rest the one
     * above.
     */
    private static int[] boxWidthsMatching(double spread) {
        int narrower = (int) Math.floor(Math.sqrt((12 * spread * spread / 3) + 1));
        if (narrower % 2 == 0) {
            narrower--;
        }
        int wider = narrower + 2;
        double idealNarrowCount = ((12 * spread * spread)
                - ((double) narrower * narrower * 3)
                - (4.0 * 3 * narrower) - (3.0 * 3))
                / ((-4.0 * narrower) - 4);
        long howManyNarrow = Math.round(idealNarrowCount);
        int[] widths = new int[3];
        for (int which = 0; which < widths.length; which++) {
            widths[which] = which < howManyNarrow ? narrower : wider;
        }
        return widths;
    }

    /**
     * One box blur, which is a pass along the rows and then a pass down the
     * columns. Doing the two separately is what makes a box cost the same as a
     * line.
     */
    private static void boxBlur(byte[] picture, byte[] working,
            int wide, int high, int spread) {

        System.arraycopy(picture, 0, working, 0, wide * high * CHANNELS);
        alongTheRows(working, picture, wide, high, spread);
        downTheColumns(picture, working, wide, high, spread);
        System.arraycopy(working, 0, picture, 0, wide * high * CHANNELS);
    }

    /**
     * A running total slid along each row, one channel at a time.
     *
     * <p>Adding the pixel arriving and taking away the one leaving is what
     * makes the cost independent of how wide the box is. Beyond each end of
     * the row the edge pixel is treated as repeated, because averaging only
     * the neighbours that exist would make the border lighter than the rest.
     */
    private static void alongTheRows(byte[] from, byte[] into,
            int wide, int high, int spread) {

        for (int row = 0; row < high; row++) {
            for (int channel = 0; channel < CHANNELS; channel++) {
                int writing = (row * wide * CHANNELS) + channel;
                int leaving = writing;
                int arriving = writing + (spread * CHANNELS);
                int first = read(from, writing);
                int last = read(from, writing + ((wide - 1) * CHANNELS));
                int total = (spread + 1) * first;
                for (int step = 0; step < spread; step++) {
                    total += read(from, writing + (step * CHANNELS));
                }
                for (int step = 0; step <= spread; step++) {
                    total += read(from, arriving) - first;
                    write(into, writing, total / ((spread * 2) + 1));
                    arriving += CHANNELS;
                    writing += CHANNELS;
                }
                for (int step = spread + 1; step < wide - spread; step++) {
                    total += read(from, arriving) - read(from, leaving);
                    write(into, writing, total / ((spread * 2) + 1));
                    leaving += CHANNELS;
                    arriving += CHANNELS;
                    writing += CHANNELS;
                }
                for (int step = wide - spread; step < wide; step++) {
                    total += last - read(from, leaving);
                    write(into, writing, total / ((spread * 2) + 1));
                    leaving += CHANNELS;
                    writing += CHANNELS;
                }
            }
        }
    }

    /** The same running total slid down each column instead of along each row. */
    private static void downTheColumns(byte[] from, byte[] into,
            int wide, int high, int spread) {

        int aRow = wide * CHANNELS;
        for (int column = 0; column < wide; column++) {
            for (int channel = 0; channel < CHANNELS; channel++) {
                int writing = (column * CHANNELS) + channel;
                int leaving = writing;
                int arriving = writing + (spread * aRow);
                int first = read(from, writing);
                int last = read(from, writing + ((high - 1) * aRow));
                int total = (spread + 1) * first;
                for (int step = 0; step < spread; step++) {
                    total += read(from, writing + (step * aRow));
                }
                for (int step = 0; step <= spread; step++) {
                    total += read(from, arriving) - first;
                    write(into, writing, total / ((spread * 2) + 1));
                    arriving += aRow;
                    writing += aRow;
                }
                for (int step = spread + 1; step < high - spread; step++) {
                    total += read(from, arriving) - read(from, leaving);
                    write(into, writing, total / ((spread * 2) + 1));
                    leaving += aRow;
                    arriving += aRow;
                    writing += aRow;
                }
                for (int step = high - spread; step < high; step++) {
                    total += last - read(from, leaving);
                    write(into, writing, total / ((spread * 2) + 1));
                    leaving += aRow;
                    writing += aRow;
                }
            }
        }
    }

    /**
     * Room past the last pixel, reading as nothing and written to harmlessly.
     *
     * <p>The C runs off the end of every row it blurs at the widest radius it
     * allows: it writes one pixel more per row than the row holds, because the
     * radius is brought down to half the width rather than to half of one less
     * than the width. On a picture with an even width that is one pixel too
     * many, and on the last row it is past the picture altogether.
     *
     * <p>So the room is part of the answer rather than a safety margin. What
     * the C reads there is whatever the allocator left, which on a picture of
     * any size is zeros -- and the blurred pixels near the bottom right are
     * darker for it. This keeps the reads in bounds and reading nothing, which
     * is the same answer and is an answer rather than an accident.
     */
    private static int roomPastTheEnd(int wide) {
        return (wide * CHANNELS) + CHANNELS;
    }

    private static byte[] channelsOf(ImageStorage storage, int wide, int high) {
        byte[] channels = new byte[(wide * high * CHANNELS) + roomPastTheEnd(wide)];
        for (int pixel = 1; pixel <= wide * high; pixel++) {
            int[] rgba = storage.pixelAt(pixel);
            for (int channel = 0; channel < CHANNELS; channel++) {
                channels[((pixel - 1) * CHANNELS) + channel] = (byte) rgba[channel];
            }
        }
        return channels;
    }

    private static void writeChannelsBack(
            ImageStorage storage, byte[] channels, int wide, int high) {

        for (int pixel = 1; pixel <= wide * high; pixel++) {
            int at = (pixel - 1) * CHANNELS;
            storage.setColourAt(pixel,
                    read(channels, at), read(channels, at + 1), read(channels, at + 2));
            storage.setAlphaAt(pixel, read(channels, at + 3));
        }
    }

    private static int read(byte[] channels, int at) {
        return channels[at] & 0xFF;
    }

    private static void write(byte[] channels, int at, int value) {
        channels[at] = (byte) value;
    }

    /** Red, green, blue and alpha, all four blurred the same way. */
    private static final int CHANNELS = 4;

    /**
     * A new image at a new size, sampled from the old one.
     *
     * <p>Nearest neighbour where the C offers a choice of filters and
     * defaults to Lanczos. The filters are named in
     * {@code system/catalog/filters} and choosing between them changes how a
     * shrunken photograph looks; it does not change what RESIZE is, and
     * nothing here can yet ask for one.
     */
    static ImageValue resized(ImageValue image, int wide, int high) {
        ImageStorage from = image.storage();
        ImageStorage into = ImageStorage.of(wide, high);
        for (int row = 0; row < high; row++) {
            for (int column = 0; column < wide; column++) {
                int[] sampled = from.pixelAt(
                        ((row * from.high() / high) * from.wide())
                                + (column * from.wide() / wide) + 1);
                int pixel = (row * wide) + column + 1;
                into.setColourAt(pixel, sampled[0], sampled[1], sampled[2]);
                into.setAlphaAt(pixel, sampled[3]);
            }
        }
        return new ImageValue(into, 1);
    }

    /**
     * How far apart two images are, from nothing to everything.
     *
     * <p>Weighted because the eye is not equally sensitive to the three
     * colours: green carries most of what is seen as brightness and blue
     * least, so an equal-weighted distance calls two images different in a
     * way nobody looking at them would.
     *
     * <p>Only the overlap is compared when the sizes differ, which the
     * declaration says in its own argument comments: "If sizes of the input
     * images are not same... then only the smaller part is compared!"
     */
    static double differenceBetween(ImageValue first, ImageValue second) {
        ImageStorage left = first.storage();
        ImageStorage right = second.storage();
        int wide = Math.min(left.wide(), right.wide());
        int high = Math.min(left.high(), right.high());
        if (wide == 0 || high == 0) {
            return 0;
        }
        return howFarApart(left, right, 0, 0, wide, high);
    }

    /**
     * The same measure over a rectangle of the pair rather than all of it.
     *
     * <p>The corner is counted from nought -- "Zero based top-left corner",
     * says the declaration -- because this is a coordinate into a picture
     * rather than a position in a series.
     *
     * <p>A negative size reaches back from the corner, which is how a caller
     * names a region by its far corner: the corner moves by the negative
     * amount and the size becomes positive. A corner before the picture starts
     * is brought up to nought and takes that much off the size with it.
     */
    static double differenceOverTheRectangle(ImageValue first, ImageValue second,
            int cornerX, int cornerY, int rectangleWide, int rectangleHigh) {

        ImageStorage left = first.storage();
        ImageStorage right = second.storage();
        int widestOfTheTwo = Math.max(left.wide(), right.wide());
        int tallestOfTheTwo = Math.max(left.high(), right.high());

        int wide = rectangleWide;
        int high = rectangleHigh;
        int fromX = cornerX;
        int fromY = cornerY;
        if (wide < 0) {
            fromX += wide;
            wide = -wide;
        }
        if (high < 0) {
            fromY += high;
            high = -high;
        }
        if (fromX < 0) {
            wide += fromX;
            fromX = 0;
        }
        if (fromY < 0) {
            high += fromY;
            fromY = 0;
        }
        if (fromX + wide > widestOfTheTwo) {
            wide = widestOfTheTwo - fromX - wide;
        }
        if (fromY + high > tallestOfTheTwo) {
            high = tallestOfTheTwo - fromY - high;
        }
        if (wide == 0 || high == 0) {
            throw Raised.of(EvaluationFailure.INVALID_DATA,
                    PairValue.of(rectangleWide, rectangleHigh));
        }
        if (fromX >= widestOfTheTwo || fromY >= tallestOfTheTwo) {
            throw Raised.of(EvaluationFailure.INVALID_DATA,
                    PairValue.of(cornerX, cornerY));
        }
        return howFarApart(left, right, fromX, fromY, wide, high);
    }

    /**
     * How far apart a rectangle of two pictures is, walked the way the C walks
     * it: along each row of the rectangle, then over the pixels the rectangle
     * left out on the right before starting the next.
     *
     * <p>A rectangle reaching past the right or bottom edge arrives here with
     * a negative width or height rather than a clipped one, because the C
     * subtracts the size a second time where clipping would subtract only the
     * corner. Nothing is then compared and the answer is nought per cent,
     * which says two pictures are identical when the pixels it was pointed at
     * differ. REBOL's own test asserts that nought twice, so it is behaviour
     * rather than an accident to be tidied away.
     */
    private static double howFarApart(ImageStorage left, ImageStorage right,
            int fromX, int fromY, int wide, int high) {

        long leftCursor = (long) left.wide() * fromY;
        long rightCursor = (long) right.wide() * fromY;
        double apart = 0;
        for (int row = 0; row < high; row++) {
            leftCursor += fromX;
            rightCursor += fromX;
            for (int column = 0; column < wide; column++) {
                apart += redmeanDistance(
                        left.pixelAt((int) leftCursor + 1),
                        right.pixelAt((int) rightCursor + 1));
                leftCursor++;
                rightCursor++;
            }
            leftCursor += left.wide() - fromX - wide;
            rightCursor += right.wide() - fromX - wide;
        }
        return Math.round((apart / ((long) wide * high)) * PICOUNITS)
                / WIDEST_DISTANCE_IN_PICOUNITS;
    }

    /**
     * The mean distance is rounded to a whole number of these before it is
     * divided, which is the whole reason black against white reads as exactly
     * a hundred per cent.
     *
     * <p>The C says so in a comment above the line -- "used rounding to have
     * nice 100% when completely different" -- and without it the answer comes
     * out as 99.9999999999999%, which is true and reads as a mistake.
     */
    private static final double PICOUNITS = 1_000_000_000_000.0;

    private static final double WIDEST_DISTANCE_IN_PICOUNITS = 764_833_315_173_967.0;

    /**
     * How far apart two colours look, by the redmean approximation.
     *
     * <p>Not a plain distance in red, green and blue: equal steps in those
     * numbers do not look equal. Green carries most of what the eye reads as
     * brightness, and how much red and blue matter depends on how red the
     * pair already is -- so the red and blue weights slide with the mean of
     * the two reds while green's stays at four.
     *
     * <p>{@code https://www.compuphase.com/cmetric.htm}, which the C cites,
     * and its shifts are kept rather than turned into division: {@code
     * ((512+rmean)*r*r)>>8} truncates where a divide by 256 would, and the
     * percentages come out a fraction different if it does not.
     *
     * <p>Alpha takes no part. Two images differing only in transparency are
     * nought per cent apart, which is checked against a real 3.22.1 and is
     * not what a reader of "weighted RGB distance" would assume.
     */
    private static double redmeanDistance(int[] left, int[] right) {
        long red = left[0] - right[0];
        long green = left[1] - right[1];
        long blue = left[2] - right[2];
        long meanRed = ((long) left[0] + right[0]) / 2;
        return Math.sqrt((((512 + meanRed) * red * red) >> 8)
                + (4 * green * green)
                + (((767 - meanRed) * blue * blue) >> 8));
    }

}
