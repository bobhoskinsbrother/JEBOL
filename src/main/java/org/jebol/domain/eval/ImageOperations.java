package org.jebol.domain.eval;

import org.jebol.domain.value.*;

public final class ImageOperations {

    private ImageOperations() {
    }

    private static final int OPAQUE = 0xFF;

    private static int pixelCount(ImageStorage storage) {
        return storage.wide() * storage.high();
    }

    public static void premultiply(ImageValue image) {
        ImageStorage storage = image.storage();
        for (int pixel = 1;
                pixel <= pixelCount(storage);
                pixel++) {
            int[] rgba = storage.readPixelAt(pixel);
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

    public static void blur(ImageValue image, int radius) {
        ImageStorage storage = image.storage();
        if (radius <= 0 || storage.wide() == 0 || storage.high() == 0) {
            return;
        }
        int wide = storage.wide();
        int high = storage.high();
        int spread = limitToHalfShorterSide(radius, wide, high);
        byte[] picture = flattenChannels(storage, wide, high);
        byte[] working = new byte[picture.length];
        for (int boxWidth : findBoxWidthsMatching(spread)) {
            blurWithBoxes(picture, working, wide, high, (boxWidth - 1) / 2);
        }
        writeChannelsBack(storage, picture, wide, high);
    }

    private static int limitToHalfShorterSide(int radius, int wide, int high) {
        return Math.min(radius, Math.min(wide / 2, high / 2));
    }

    private static int[] findBoxWidthsMatching(double spread) {
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

    private static void blurWithBoxes(byte[] picture, byte[] working,
                                      int wide, int high, int spread) {

        System.arraycopy(picture, 0, working, 0, wide * high * CHANNELS);
        blurAlongRows(working, picture, wide, high, spread);
        blurDownColumns(picture, working, wide, high, spread);
        System.arraycopy(working, 0, picture, 0, wide * high * CHANNELS);
    }

    private static void blurAlongRows(byte[] from, byte[] into,
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

    private static void blurDownColumns(byte[] from, byte[] into,
                                        int wide, int high, int spread) {

        int row = wide * CHANNELS;
        for (int column = 0; column < wide; column++) {
            for (int channel = 0; channel < CHANNELS; channel++) {
                int writing = (column * CHANNELS) + channel;
                int leaving = writing;
                int arriving = writing + (spread * row);
                int first = read(from, writing);
                int last = read(from, writing + ((high - 1) * row));
                int total = (spread + 1) * first;
                for (int step = 0; step < spread; step++) {
                    total += read(from, writing + (step * row));
                }
                for (int step = 0; step <= spread; step++) {
                    total += read(from, arriving) - first;
                    write(into, writing, total / ((spread * 2) + 1));
                    arriving += row;
                    writing += row;
                }
                for (int step = spread + 1; step < high - spread; step++) {
                    total += read(from, arriving) - read(from, leaving);
                    write(into, writing, total / ((spread * 2) + 1));
                    leaving += row;
                    arriving += row;
                    writing += row;
                }
                for (int step = high - spread; step < high; step++) {
                    total += last - read(from, leaving);
                    write(into, writing, total / ((spread * 2) + 1));
                    leaving += row;
                    writing += row;
                }
            }
        }
    }

    private static int measureRoomBlurRunsInto(int wide) {
        return (wide * CHANNELS) + CHANNELS;
    }

    private static byte[] flattenChannels(ImageStorage storage, int wide, int high) {
        byte[] channels = new byte[(wide * high * CHANNELS)
                + measureRoomBlurRunsInto(wide)];
        for (int pixel = 1; pixel <= wide * high; pixel++) {
            int[] rgba = storage.readPixelAt(pixel);
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

    private static final int CHANNELS = 4;

    public static ImageValue resize(ImageValue image, int wide, int high) {
        ImageStorage from = image.storage();
        ImageStorage into = ImageStorage.of(wide, high);
        for (int row = 0; row < high; row++) {
            for (int column = 0; column < wide; column++) {
                int[] sampled = readNearestPixelFrom(from, row, column, wide, high);
                int pixel = (row * wide) + column + 1;
                into.setColourAt(pixel, sampled[0], sampled[1], sampled[2]);
                into.setAlphaAt(pixel, sampled[3]);
            }
        }
        return new ImageValue(into, 1);
    }

    private static int[] readNearestPixelFrom(
            ImageStorage from, int row, int column, int wide, int high) {
        return from.readPixelAt(((row * from.high() / high) * from.wide())
                + (column * from.wide() / wide) + 1);
    }

    public static double measureDifferenceBetween(ImageValue first, ImageValue second) {
        ImageStorage left = first.storage();
        ImageStorage right = second.storage();
        int wide = measureOverlap(left.wide(), right.wide());
        int high = measureOverlap(left.high(), right.high());
        return measureHowFarApart(left, right, 0, 0, wide, high);
    }

    private static int measureOverlap(int ours, int theirs) {
        return Math.min(ours, theirs);
    }

    public static double measureDifferenceOverRectangle(ImageValue first, ImageValue second,
                                                        int cornerX, int cornerY, int rectangleWide, int rectangleHigh) {

        ImageStorage left = first.storage();
        ImageStorage right = second.storage();
        int widestOfTwo = Math.max(left.wide(), right.wide());
        int tallestOfTwo = Math.max(left.high(), right.high());

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
        if (fromX + wide > widestOfTwo) {
            wide = widestOfTwo - fromX - wide;
        }
        if (fromY + high > tallestOfTwo) {
            high = tallestOfTwo - fromY - high;
        }
        if (wide == 0 || high == 0) {
            throw Raised.of(EvaluationFailure.INVALID_DATA,
                    PairValue.of(rectangleWide, rectangleHigh));
        }
        if (fromX >= widestOfTwo || fromY >= tallestOfTwo) {
            throw Raised.of(EvaluationFailure.INVALID_DATA,
                    PairValue.of(cornerX, cornerY));
        }
        return measureHowFarApart(left, right, fromX, fromY, wide, high);
    }

    private static double measureHowFarApart(ImageStorage left, ImageStorage right,
                                             int fromX, int fromY, int wide, int high) {

        long leftCursor = (long) left.wide() * fromY;
        long rightCursor = (long) right.wide() * fromY;
        double apart = 0;
        for (int row = 0; row < high; row++) {
            leftCursor += fromX;
            rightCursor += fromX;
            for (int column = 0; column < wide; column++) {
                apart += measureRedmeanDistance(
                        left.readPixelAt((int) leftCursor + 1),
                        right.readPixelAt((int) rightCursor + 1));
                leftCursor++;
                rightCursor++;
            }
            leftCursor += left.wide() - fromX - wide;
            rightCursor += right.wide() - fromX - wide;
        }
        long counted = (long) wide * high;
        if (counted == 0) {
            return NOTHING_WAS_LOOKED_AT;
        }
        return Math.round((apart / counted) * PICOUNITS)
                / WIDEST_DISTANCE_IN_PICOUNITS;
    }

    private static final double NOTHING_WAS_LOOKED_AT = Double.NaN;

    private static final double PICOUNITS = 1_000_000_000_000.0;

    private static final double WIDEST_DISTANCE_IN_PICOUNITS = 764_833_315_173_967.0;

    private static double measureRedmeanDistance(int[] left, int[] right) {
        long red = left[0] - right[0];
        long green = left[1] - right[1];
        long blue = left[2] - right[2];
        long meanRed = ((long) left[0] + right[0]) / 2;
        return Math.sqrt((((512 + meanRed) * red * red) >> 8)
                + (4 * green * green)
                + (((767 - meanRed) * blue * blue) >> 8));
    }

}
