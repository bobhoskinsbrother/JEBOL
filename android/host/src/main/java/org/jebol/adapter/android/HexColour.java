package org.jebol.adapter.android;

import android.graphics.Color;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class HexColour {

    private static final Pattern SIX_HEX_DIGITS = Pattern.compile("#?[0-9a-fA-F]{6}");

    private HexColour() {
    }

    public static Optional<int[]> read(String written) {
        String trimmed = written.trim();
        if (!SIX_HEX_DIGITS.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        String digits = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        return Optional.of(new int[] {
                Integer.parseInt(digits.substring(0, 2), 16),
                Integer.parseInt(digits.substring(2, 4), 16),
                Integer.parseInt(digits.substring(4, 6), 16)});
    }

    public static String write(int[] colour) {
        return String.format(Locale.ROOT, "%02x%02x%02x", colour[0] & 0xFF, colour[1] & 0xFF, colour[2] & 0xFF);
    }

    public static float[] toHsv(int[] colour) {
        float[] hsv = new float[3];
        Color.RGBToHSV(colour[0], colour[1], colour[2], hsv);
        return hsv;
    }

    public static int[] fromHsv(float[] hsv) {
        int packed = Color.HSVToColor(hsv);
        return new int[] {Color.red(packed), Color.green(packed), Color.blue(packed)};
    }
}
