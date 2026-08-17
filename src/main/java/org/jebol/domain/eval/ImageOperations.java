package org.jebol.domain.eval;

import org.jebol.domain.value.ImageStorage;
import org.jebol.domain.value.ImageValue;

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
     * out negative gets its image back untouched rather than an error.
     *
     * <p>Two passes of a box blur rather than one Gaussian pass. Repeated
     * box blurring approaches a Gaussian, and doing it in each direction
     * separately is what keeps the cost proportional to the radius instead of
     * its square.
     */
    static void blur(ImageValue image, int radius) {
        if (radius <= 0) {
            return;
        }
        ImageStorage storage = image.storage();
        int wide = storage.wide();
        int high = storage.high();
        for (int pass = 0; pass < 2; pass++) {
            blurAcross(storage, wide, high, radius);
            blurDown(storage, wide, high, radius);
        }
    }

    private static void blurAcross(ImageStorage storage, int wide, int high, int radius) {
        for (int row = 0; row < high; row++) {
            int[][] before = rowRead(storage, wide, row);
            for (int column = 0; column < wide; column++) {
                writeAverage(storage, (row * wide) + column + 1,
                        before, column, wide, radius);
            }
        }
    }

    private static void blurDown(ImageStorage storage, int wide, int high, int radius) {
        for (int column = 0; column < wide; column++) {
            int[][] before = columnRead(storage, wide, high, column);
            for (int row = 0; row < high; row++) {
                writeAverage(storage, (row * wide) + column + 1,
                        before, row, high, radius);
            }
        }
    }

    private static int[][] rowRead(ImageStorage storage, int wide, int row) {
        int[][] read = new int[wide][];
        for (int column = 0; column < wide; column++) {
            read[column] = storage.pixelAt((row * wide) + column + 1);
        }
        return read;
    }

    private static int[][] columnRead(
            ImageStorage storage, int wide, int high, int column) {
        int[][] read = new int[high][];
        for (int row = 0; row < high; row++) {
            read[row] = storage.pixelAt((row * wide) + column + 1);
        }
        return read;
    }

    private static void writeAverage(ImageStorage storage, int pixel,
            int[][] neighbours, int at, int howMany, int radius) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int counted = 0;
        for (int step = -radius; step <= radius; step++) {
            int from = at + step;
            if (from < 0 || from >= howMany) {
                continue;
            }
            red += neighbours[from][0];
            green += neighbours[from][1];
            blue += neighbours[from][2];
            counted++;
        }
        storage.setColourAt(pixel, red / counted, green / counted, blue / counted);
    }

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
        double apart = 0;
        for (int row = 0; row < high; row++) {
            for (int column = 0; column < wide; column++) {
                apart += redmeanDistance(
                        left.pixelAt((row * left.wide()) + column + 1),
                        right.pixelAt((row * right.wide()) + column + 1));
            }
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
