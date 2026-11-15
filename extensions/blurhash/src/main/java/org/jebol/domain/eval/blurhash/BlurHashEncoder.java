package org.jebol.domain.eval.blurhash;

import java.util.Optional;

/**
 * Wolt's BlurHash encoder as Rebol-BlurHash 1.0.0 carries it: four bytes a
 * pixel, of which the first three are read as red, green and blue, computed in
 * the C's mix of float and double.
 */
public final class BlurHashEncoder {

    static final String DIGITS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~";

    private BlurHashEncoder() {
    }

    /**
     * The hash of a picture of {@code xComponents} by {@code yComponents}
     * components, or nothing when either count is outside 1 to 9.
     */
    public static Optional<String> encode(int xComponents, int yComponents, int width, int height,
                                          byte[] pixels, int bytesPerRow) {
        if (xComponents < 1 || xComponents > 9 || yComponents < 1 || yComponents > 9) {
            return Optional.empty();
        }
        float[] factors = new float[yComponents * xComponents * 3];
        for (int y = 0; y < yComponents; y++) {
            for (int x = 0; x < xComponents; x++) {
                float[] factor = multiplyBasisFunction(x, y, width, height, pixels, bytesPerRow);
                System.arraycopy(factor, 0, factors, (y * xComponents + x) * 3, 3);
            }
        }
        int acCount = xComponents * yComponents - 1;
        StringBuilder hash = new StringBuilder();
        appendDigits((xComponents - 1) + (yComponents - 1) * 9, 1, hash);

        float maximumValue;
        if (acCount > 0) {
            float actualMaximumValue = 0;
            for (int i = 0; i < acCount * 3; i++) {
                actualMaximumValue = CFloats.max(Math.abs(factors[3 + i]), actualMaximumValue);
            }
            int quantisedMaximumValue = (int) CFloats.max(0,
                    CFloats.min(82, CFloats.floor(actualMaximumValue * 166 - 0.5)));
            maximumValue = ((float) quantisedMaximumValue + 1) / 166;
            appendDigits(quantisedMaximumValue, 1, hash);
        } else {
            maximumValue = 1;
            appendDigits(0, 1, hash);
        }

        appendDigits(encodeDc(factors[0], factors[1], factors[2]), 4, hash);
        for (int i = 0; i < acCount; i++) {
            int at = 3 + i * 3;
            appendDigits(encodeAc(factors[at], factors[at + 1], factors[at + 2], maximumValue), 2, hash);
        }
        return Optional.of(hash.toString());
    }

    private static float[] multiplyBasisFunction(int xComponent, int yComponent, int width, int height,
                                                 byte[] pixels, int bytesPerRow) {
        float r = 0;
        float g = 0;
        float b = 0;
        float normalisation = (xComponent == 0 && yComponent == 0) ? 1 : 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float basis = CFloats.cos(Math.PI * xComponent * x / width)
                        * CFloats.cos(Math.PI * yComponent * y / height);
                r += basis * CFloats.toLinear(pixels[4 * x + y * bytesPerRow] & 0xFF);
                g += basis * CFloats.toLinear(pixels[4 * x + 1 + y * bytesPerRow] & 0xFF);
                b += basis * CFloats.toLinear(pixels[4 * x + 2 + y * bytesPerRow] & 0xFF);
            }
        }
        float scale = normalisation / (width * height);
        return new float[] {r * scale, g * scale, b * scale};
    }

    private static int encodeDc(float r, float g, float b) {
        return (CFloats.toSRgb(r) << 16) + (CFloats.toSRgb(g) << 8) + CFloats.toSRgb(b);
    }

    private static int encodeAc(float r, float g, float b, float maximumValue) {
        return quantise(r, maximumValue) * 19 * 19 + quantise(g, maximumValue) * 19 + quantise(b, maximumValue);
    }

    private static int quantise(float component, float maximumValue) {
        return (int) CFloats.max(0, CFloats.min(18,
                CFloats.floor(CFloats.signPow(component / maximumValue, 0.5f) * 9 + 9.5)));
    }

    private static void appendDigits(int value, int length, StringBuilder hash) {
        int divisor = 1;
        for (int i = 0; i < length - 1; i++) {
            divisor *= 83;
        }
        for (int i = 0; i < length; i++) {
            hash.append(DIGITS.charAt((value / divisor) % 83));
            divisor /= 83;
        }
    }
}
