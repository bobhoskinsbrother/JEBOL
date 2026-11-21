package org.jebol.devtools;

import java.util.Locale;

public record Colour(int red, int green, int blue) {

    public static final Colour WHITE = new Colour(255, 255, 255);

    public String hex() {
        return String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue);
    }

    public int rgb() {
        return (red << 16) | (green << 8) | blue;
    }
}
