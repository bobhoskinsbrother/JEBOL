package org.jebol.domain.eval.blurhash;

/**
 * Wolt's BlurHash decoder as Rebol-BlurHash 1.0.0 carries it, reading the hash
 * as the C reads a string: up to its first zero byte.
 */
public final class BlurHashDecoder {

    private BlurHashDecoder() {
    }

    /**
     * Paints the hash into {@code pixels}, {@code channels} bytes a pixel in red,
     * green, blue and then alpha order, and answers whether it did. A hash that
     * does not read is refused before a byte is written.
     */
    public static boolean decodeInto(byte[] hash, int width, int height, int punch, int channels,
                                     byte[] pixels) {
        String text = readUpToZeroByte(hash);
        if (!isValid(text)) {
            return false;
        }
        int contrast = Math.max(punch, 1);
        int sizeFlag = decodeDigits(text, 0, 1);
        int numY = (int) Math.floor((float) (sizeFlag / 9)) + 1;
        int numX = (sizeFlag % 9) + 1;

        int quantisedMaximumValue = decodeDigits(text, 1, 2);
        if (quantisedMaximumValue == -1) {
            return false;
        }
        float maximumValue = ((float) (quantisedMaximumValue + 1)) / 166;

        float[] colours = new float[numX * numY * 3];
        for (int each = 0; each < numX * numY; each++) {
            if (each == 0) {
                int value = decodeDigits(text, 2, 6);
                if (value == -1) {
                    return false;
                }
                colours[0] = CFloats.toLinear(value >> 16);
                colours[1] = CFloats.toLinear((value >> 8) & 255);
                colours[2] = CFloats.toLinear(value & 255);
            } else {
                int value = decodeDigits(text, 4 + each * 2, 6 + each * 2);
                if (value == -1) {
                    return false;
                }
                decodeAc(value, maximumValue * contrast, colours, each * 3);
            }
        }

        int bytesPerRow = width * channels;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = 0;
                float g = 0;
                float b = 0;
                for (int j = 0; j < numY; j++) {
                    for (int i = 0; i < numX; i++) {
                        float basics = (float) (Math.cos((Math.PI * x * i) / width)
                                * Math.cos((Math.PI * y * j) / height));
                        int at = (i + j * numX) * 3;
                        r += colours[at] * basics;
                        g += colours[at + 1] * basics;
                        b += colours[at + 2] * basics;
                    }
                }
                int pixel = channels * x + y * bytesPerRow;
                pixels[pixel] = (byte) clampToOctet(CFloats.toSRgb(r));
                pixels[pixel + 1] = (byte) clampToOctet(CFloats.toSRgb(g));
                pixels[pixel + 2] = (byte) clampToOctet(CFloats.toSRgb(b));
                if (channels == 4) {
                    pixels[pixel + 3] = (byte) 255;
                }
            }
        }
        return true;
    }

    private static String readUpToZeroByte(byte[] hash) {
        StringBuilder text = new StringBuilder();
        for (byte octet : hash) {
            if (octet == 0) {
                break;
            }
            text.append((char) (octet & 0xFF));
        }
        return text.toString();
    }

    private static boolean isValid(String hash) {
        if (hash.length() < 6) {
            return false;
        }
        int sizeFlag = decodeDigits(hash, 0, 1);
        int numY = (int) Math.floor((float) (sizeFlag / 9)) + 1;
        int numX = (sizeFlag % 9) + 1;
        return hash.length() == 4 + 2 * numX * numY;
    }

    private static int decodeDigits(String hash, int start, int end) {
        int value = 0;
        for (int at = start; at < end; at++) {
            int digit = BlurHashEncoder.DIGITS.indexOf(hash.charAt(at));
            if (digit == -1) {
                return -1;
            }
            value = value * 83 + digit;
        }
        return value;
    }

    private static void decodeAc(int value, float maximumValue, float[] colours, int at) {
        int quantR = value / (19 * 19);
        int quantG = (value / 19) % 19;
        int quantB = value % 19;
        colours[at] = CFloats.signPow(((float) quantR - 9) / 9, 2.0f) * maximumValue;
        colours[at + 1] = CFloats.signPow(((float) quantG - 9) / 9, 2.0f) * maximumValue;
        colours[at + 2] = CFloats.signPow(((float) quantB - 9) / 9, 2.0f) * maximumValue;
    }

    private static int clampToOctet(int value) {
        if (value >= 0 && value <= 255) {
            return value;
        }
        return value < 0 ? 0 : 255;
    }
}
