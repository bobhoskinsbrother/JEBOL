package org.jebol.domain.render;

import org.jebol.domain.value.ImageValue;

import java.util.Locale;
import java.util.Optional;

/**
 * How a picture is resized when it is drawn at a size other than its own.
 *
 * <p>IMAGE-FILTER names the method, and the resizing is done here rather than
 * left to a toolkit: scaling quality is a rasteriser's own business and the
 * two disagree about it, so a picture scaled by Java2D and the same picture
 * scaled by a browser are not the same picture. Resized here, both are handed
 * the finished pixels and blit them.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public enum Resampling {

    NEAREST,

    BILINEAR,

    BICUBIC,

    GAUSSIAN;

    private static final int CHANNELS = 4;
    private static final int OPAQUE = 255;

    public static Optional<Resampling> named(String spelling) {
        for (Resampling how : values()) {
            if (how.name().toLowerCase(Locale.ROOT).equals(spelling)) {
                return Optional.of(how);
            }
        }
        return Optional.empty();
    }

    /** The picture at that size, resized this way. */
    public ImageValue resized(ImageValue picture, int wide, int high) {
        int fromWide = (int) Math.round(picture.size().x());
        int fromHigh = (int) Math.round(picture.size().y());
        if (wide <= 0 || high <= 0 || fromWide <= 0 || fromHigh <= 0
                || (wide == fromWide && high == fromHigh)) {
            return picture;
        }
        int[] source = channelsOf(picture, fromWide, fromHigh);
        int[] read = this == GAUSSIAN ? softened(source, fromWide, fromHigh) : source;
        int[] made = new int[wide * high * CHANNELS];
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                double fromAcross = (across + 0.5) * fromWide / wide - 0.5;
                double fromDown = (down + 0.5) * fromHigh / high - 0.5;
                int[] parts = this == NEAREST
                        ? nearestIn(read, fromWide, fromHigh, fromAcross, fromDown)
                        : blendedIn(read, fromWide, fromHigh, fromAcross, fromDown);
                System.arraycopy(parts, 0, made,
                        (down * wide + across) * CHANNELS, CHANNELS);
            }
        }
        return madeOf(made, wide, high);
    }

    private int[] nearestIn(
            int[] source, int wide, int high, double across, double down) {

        return pixelIn(source, wide, high,
                (int) Math.round(across), (int) Math.round(down));
    }

    private int[] blendedIn(
            int[] source, int wide, int high, double across, double down) {

        int leftOf = (int) Math.floor(across);
        int above = (int) Math.floor(down);
        double alongIt = across - leftOf;
        double downIt = down - above;
        int[] blended = new int[CHANNELS];
        for (int channel = 0; channel < CHANNELS; channel++) {
            double topLeft = pixelIn(source, wide, high, leftOf, above)[channel];
            double topRight = pixelIn(source, wide, high, leftOf + 1, above)[channel];
            double bottomLeft = pixelIn(source, wide, high, leftOf, above + 1)[channel];
            double bottomRight =
                    pixelIn(source, wide, high, leftOf + 1, above + 1)[channel];
            double acrossTheTop = topLeft + (topRight - topLeft) * alongIt;
            double acrossTheBottom = bottomLeft + (bottomRight - bottomLeft) * alongIt;
            double mixed = acrossTheTop + (acrossTheBottom - acrossTheTop) * downIt;
            blended[channel] = Math.clamp((int) Math.round(
                    this == BICUBIC ? sharpened(mixed, topLeft, bottomRight) : mixed),
                    0, OPAQUE);
        }
        return blended;
    }

    private static double sharpened(double mixed, double one, double other) {
        double average = (one + other) / 2;
        return mixed + (mixed - average) * 0.25;
    }

    private static int[] softened(int[] source, int wide, int high) {
        int[] blurred = new int[source.length];
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                for (int channel = 0; channel < CHANNELS; channel++) {
                    int total = 0;
                    int counted = 0;
                    for (int downBy = -1; downBy <= 1; downBy++) {
                        for (int acrossBy = -1; acrossBy <= 1; acrossBy++) {
                            total += pixelIn(source, wide, high,
                                    across + acrossBy, down + downBy)[channel];
                            counted++;
                        }
                    }
                    blurred[(down * wide + across) * CHANNELS + channel] =
                            total / counted;
                }
            }
        }
        return blurred;
    }

    private static int[] pixelIn(int[] source, int wide, int high, int across, int down) {
        int atAcross = Math.clamp(across, 0, wide - 1);
        int atDown = Math.clamp(down, 0, high - 1);
        int at = (atDown * wide + atAcross) * CHANNELS;
        return new int[] {source[at], source[at + 1], source[at + 2], source[at + 3]};
    }

    private static int[] channelsOf(ImageValue picture, int wide, int high) {
        int[] channels = new int[wide * high * CHANNELS];
        for (int pixel = 0; pixel < wide * high; pixel++) {
            int[] parts = picture.pixelAt(pixel + 1);
            channels[pixel * CHANNELS] = parts[0];
            channels[pixel * CHANNELS + 1] = parts[1];
            channels[pixel * CHANNELS + 2] = parts[2];
            channels[pixel * CHANNELS + 3] = parts.length >= 4 ? parts[3] : OPAQUE;
        }
        return channels;
    }

    private static ImageValue madeOf(int[] channels, int wide, int high) {
        ImageValue made = ImageValue.of(wide, high);
        for (int pixel = 0; pixel < wide * high; pixel++) {
            int at = pixel * CHANNELS;
            made.storage().setColourAt(pixel + 1,
                    channels[at], channels[at + 1], channels[at + 2]);
            made.storage().setAlphaAt(pixel + 1, channels[at + 3]);
        }
        return made;
    }
}
