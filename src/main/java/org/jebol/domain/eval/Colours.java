package org.jebol.domain.eval;

final class Colours {

    private Colours() {
    }

    static int grey(int red, int green, int blue) {
        return (red + green + blue) / 3;
    }

    static int luminosityTruncatedRatherThanRounded(
            int red, int green, int blue, boolean luma) {
        return luma
                ? (int) ((0.299 * red) + (0.587 * green) + (0.114 * blue))
                : (int) ((0.2126 * red) + (0.7152 * green) + (0.0722 * blue));
    }

    static double perceptionDistance(int[] one, int[] other) {
        long red = one[0] - other[0];
        long green = one[1] - other[1];
        long blue = one[2] - other[2];
        long meanRed = ((long) one[0] + other[0]) / 2;
        return Math.sqrt((((512 + meanRed) * red * red) >> 8)
                + 4 * green * green
                + (((767 - meanRed) * blue * blue) >> 8));
    }

    static int[] hsvToRgb(int hue, int saturation, int value) {
        if (saturation == 0) {
            return aGreyTakesNoArithmeticAtAll(value);
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

    private static int[] aGreyTakesNoArithmeticAtAll(int value) {
        return new int[] {value, value, value};
    }

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
        return new int[] {aNegativeHueWrapsRatherThanClamping(hue), saturation, largest};
    }

    private static int aNegativeHueWrapsRatherThanClamping(double hue) {
        return (int) hue & 0xFF;
    }

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
