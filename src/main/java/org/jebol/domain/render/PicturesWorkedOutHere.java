package org.jebol.domain.render;

import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.PairValue;

final class PicturesWorkedOutHere {

    private static final int CHANNELS = 4;
    private static final int SEE_THROUGH = 0;
    private static final int OPAQUE = 255;

    private PicturesWorkedOutHere() {
    }

    static ImageValue withThatColourSeeThrough(ImageValue picture, Colour key) {
        int wide = (int) Math.round(picture.size().x());
        int high = (int) Math.round(picture.size().y());
        int[] copied = new int[wide * high * CHANNELS];
        for (int pixel = 0; pixel < wide * high; pixel++) {
            int[] parts = picture.readPixelAt(pixel + 1);
            boolean matches = parts[0] == key.red()
                    && parts[1] == key.green() && parts[2] == key.blue();
            copied[pixel * CHANNELS] = parts[0];
            copied[pixel * CHANNELS + 1] = parts[1];
            copied[pixel * CHANNELS + 2] = parts[2];
            copied[pixel * CHANNELS + 3] = matches ? SEE_THROUGH : alphaIn(parts);
        }
        return imageFromChannels(copied, wide, high);
    }

    static ImageValue pullOntoFourCorners(
            ImageValue picture, PairValue[] corners, int wide, int high, int leftAt,
            int topAt) {

        int fromWide = (int) Math.round(picture.size().x());
        int fromHigh = (int) Math.round(picture.size().y());
        int[] made = new int[Math.max(0, wide * high * CHANNELS)];
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                double[] found = findWhereInOriginal(
                        corners, leftAt + across + 0.5, topAt + down + 0.5);
                int at = (down * wide + across) * CHANNELS;
                if (found == null) {
                    made[at + 3] = SEE_THROUGH;
                    continue;
                }
                int fromAcross = Math.clamp(
                        (int) (found[0] * fromWide), 0, fromWide - 1);
                int fromDown = Math.clamp((int) (found[1] * fromHigh), 0, fromHigh - 1);
                int[] parts = picture.readPixelAt(fromDown * fromWide + fromAcross + 1);
                made[at] = parts[0];
                made[at + 1] = parts[1];
                made[at + 2] = parts[2];
                made[at + 3] = alphaIn(parts);
            }
        }
        return imageFromChannels(made, wide, high);
    }

    private static final int HOW_MANY_TIMES_TO_NARROW_IT = 24;

    private static double[] findWhereInOriginal(
            PairValue[] corners, double across, double down) {

        double alongTop = 0.5;
        double downSide = 0.5;
        double step = 0.25;
        for (int narrowing = 0; narrowing < HOW_MANY_TIMES_TO_NARROW_IT; narrowing++) {
            double[] here = pointOnQuad(corners, alongTop, downSide);
            double[] movedRight = pointOnQuad(
                    corners, Math.min(1, alongTop + step), downSide);
            double[] movedDown = pointOnQuad(
                    corners, alongTop, Math.min(1, downSide + step));
            alongTop = stepCloserOn(alongTop, step, here, movedRight, across, down);
            downSide = stepCloserOn(downSide, step, here, movedDown, across, down);
            step /= 2;
        }
        double[] landed = pointOnQuad(corners, alongTop, downSide);
        double howFarOut = Math.hypot(landed[0] - across, landed[1] - down);
        return howFarOut > 1.5 ? null : new double[] {alongTop, downSide};
    }

    private static double stepCloserOn(
            double standing, double step, double[] here, double[] moved,
            double across, double down) {

        double asItIs = Math.hypot(here[0] - across, here[1] - down);
        double ifMoved = Math.hypot(moved[0] - across, moved[1] - down);
        double changed = ifMoved < asItIs ? standing + step : standing - step;
        return Math.clamp(changed, 0, 1);
    }

    private static double[] pointOnQuad(
            PairValue[] corners, double alongTop, double downSide) {

        double acrossTop = corners[0].x()
                + (corners[1].x() - corners[0].x()) * alongTop;
        double downTop = corners[0].y()
                + (corners[1].y() - corners[0].y()) * alongTop;
        double acrossBottom = corners[3].x()
                + (corners[2].x() - corners[3].x()) * alongTop;
        double downBottom = corners[3].y()
                + (corners[2].y() - corners[3].y()) * alongTop;
        return new double[] {
                acrossTop + (acrossBottom - acrossTop) * downSide,
                downTop + (downBottom - downTop) * downSide};
    }

    private static int alphaIn(int[] parts) {
        return parts.length >= 4 ? parts[3] : OPAQUE;
    }

    private static ImageValue imageFromChannels(int[] channels, int wide, int high) {
        ImageValue made = ImageValue.of(Math.max(1, wide), Math.max(1, high));
        for (int pixel = 0; pixel < wide * high; pixel++) {
            int at = pixel * CHANNELS;
            made.storage().setColourAt(pixel + 1,
                    channels[at], channels[at + 1], channels[at + 2]);
            made.storage().setAlphaAt(pixel + 1, channels[at + 3]);
        }
        return made;
    }
}
