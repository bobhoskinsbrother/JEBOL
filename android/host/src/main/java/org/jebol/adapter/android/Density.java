package org.jebol.adapter.android;

public record Density(float pixelsPerDp) {

    private static final int DPI_OF_ONE_DP_PER_PIXEL = 160;

    public Density {
        if (!(pixelsPerDp > 0)) {
            throw new IllegalArgumentException("a display's density is more than nothing, not " + pixelsPerDp);
        }
    }

    public int toDp(float pixels) {
        return (int) Math.floor(pixels / pixelsPerDp);
    }

    public int screenDpi() {
        return Math.round(DPI_OF_ONE_DP_PER_PIXEL * pixelsPerDp);
    }
}
