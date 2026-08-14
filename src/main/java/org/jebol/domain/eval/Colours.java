package org.jebol.domain.eval;

/**
 * The colour arithmetic of {@code n-image.c} and {@code t-image.c}.
 *
 * <p>Kept apart from the natives because every one of these is a formula over
 * three or six bytes, and a formula that is one off is a colour that fails a
 * comparison and nothing else. Each is the C's arithmetic exactly, including
 * where it truncates rather than rounds and where it does the reverse.
 *
 * <p>Hue, saturation and value live in the same three bytes as red, green and
 * blue -- {@code REBCLR} aliases them -- so a hue is a byte rather than a degree.
 * The C's comment says why: "255 because we have just one byte! Else it should be
 * 360!". Six sectors over 255 values is lossy, and the round trip only survives
 * for colours that land on a sector boundary.
 */
final class Colours {

    private Colours() {
    }

    /** `Grayscale`: `(r + g + b) / 3`, integer division and all. */
    static int grey(int red, int green, int blue) {
        return (red + green + blue) / 3;
    }

    /**
     * `Luminosity`: BT.709 by default and BT.601 under /LUMA.
     *
     * <p>Cast to a byte rather than rounded, so it truncates.
     */
    static int luminosity(int red, int green, int blue, boolean luma) {
        return luma
                ? (int) ((0.299 * red) + (0.587 * green) + (0.114 * blue))
                : (int) ((0.2126 * red) + (0.7152 * green) + (0.0722 * blue));
    }

    /**
     * `weighted_rgb_color_distance`: a perception distance, not a Euclidean one.
     *
     * <p>Green is weighted four flat while red and blue are weighted by the mean
     * red of the two colours, which is what makes two greens further apart than
     * two blues of the same numeric difference.
     */
    static double distance(int[] one, int[] other) {
        long red = one[0] - other[0];
        long green = one[1] - other[1];
        long blue = one[2] - other[2];
        long meanRed = ((long) one[0] + other[0]) / 2;
        return Math.sqrt((((512 + meanRed) * red * red) >> 8)
                + 4 * green * green
                + (((767 - meanRed) * blue * blue) >> 8));
    }

    /**
     * HSV to RGB, in place over the three bytes.
     *
     * <p>Saturation of zero is a grey and takes no arithmetic at all, which is
     * the C's first branch. Otherwise the hue picks one of six sectors and the
     * three parts are v, p, q and t in an order the sector decides.
     */
    static int[] hsvToRgb(int hue, int saturation, int value) {
        if (saturation == 0) {
            return new int[] {value, value, value};
        }
        double sixths = hue / (255.0 / 6);
        int sector = (int) sixths;
        double into = sixths - sector;
        double whole = value / 255.0;
        double spread = saturation / 255.0;
        double v = 255.0 * whole;
        double p = 255.0 * whole * (1.0 - spread);
        double q = 255.0 * whole * (1.0 - spread * into);
        double t = 255.0 * whole * (1.0 - spread * (1.0 - into));
        return switch (sector) {
            case 0 -> new int[] {(int) v, (int) t, (int) p};
            case 1 -> new int[] {(int) q, (int) v, (int) p};
            case 2 -> new int[] {(int) p, (int) v, (int) t};
            case 3 -> new int[] {(int) p, (int) q, (int) v};
            case 4 -> new int[] {(int) t, (int) p, (int) v};
            default -> new int[] {(int) v, (int) p, (int) q};
        };
    }

    /**
     * RGB to HSV, in place over the same three bytes.
     *
     * <p>The value is the largest part and the saturation is how far the smallest
     * falls below it. The hue is which part is largest plus how far the other two
     * lean, at 42.5 a sector -- and it is cast to a byte, so a negative hue wraps
     * rather than clamping. That wrap is the behaviour: red with more blue than
     * green has a hue near 255.
     */
    static int[] rgbToHsv(int red, int green, int blue) {
        int largest = Math.max(red, Math.max(green, blue));
        int smallest = Math.min(red, Math.min(green, blue));
        if (largest == 0 || largest == smallest) {
            return new int[] {0, 0, largest};
        }
        double spread = largest - smallest;
        int saturation = (int) (255.0 * spread / largest);
        double hue;
        if (largest == red) {
            hue = 42.5 * (green - blue) / spread;
        } else if (largest == green) {
            hue = 85.0 + 42.5 * (blue - red) / spread;
        } else {
            hue = 170.0 + 42.5 * (red - green) / spread;
        }
        return new int[] {(int) hue & 0xFF, saturation, largest};
    }

    /**
     * TINT: each part moves towards the mixture by the amount.
     *
     * <p>Written as two cases rather than one interpolation because the C is:
     * `(r1 >= r2) ? r2 + ((r1 - r2) * amount1) : r1 + ((r2 - r1) * amount0)`.
     * The two agree at every amount, and following the C keeps the rounding in
     * the same place.
     */
    static int[] tinted(int[] target, int[] mixture, double amount) {
        double towards = Math.clamp(amount, 0.0, 1.0);
        double away = 1.0 - towards;
        int[] mixed = new int[3];
        for (int part = 0; part < 3; part++) {
            double from = target[part];
            double to = mixture[part];
            double moved = from >= to
                    ? to + ((from - to) * away)
                    : from + ((to - from) * towards);
            mixed[part] = Math.clamp((int) (0.5 + moved), 0, 255);
        }
        return mixed;
    }
}
