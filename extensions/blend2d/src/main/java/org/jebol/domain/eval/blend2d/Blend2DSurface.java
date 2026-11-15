package org.jebol.domain.eval.blend2d;

public final class Blend2DSurface {

    private final int wide;

    private final int high;

    private final int[] pixels;

    public Blend2DSurface(int wide, int high, int[] pixels) {
        if (pixels.length != wide * high) {
            throw new IllegalArgumentException(wide + "x" + high + " is not " + pixels.length + " pixels");
        }
        this.wide = wide;
        this.high = high;
        this.pixels = pixels;
    }

    public int wide() {
        return wide;
    }

    public int high() {
        return high;
    }

    public int[] pixels() {
        return pixels;
    }

    int pixelAt(int across, int down) {
        return pixels[down * wide + across];
    }

    void setPixel(int across, int down, int pixel) {
        pixels[down * wide + across] = pixel;
    }
}
