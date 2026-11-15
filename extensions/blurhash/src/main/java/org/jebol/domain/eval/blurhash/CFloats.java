package org.jebol.domain.eval.blurhash;

final class CFloats {

    private CFloats() {
    }

    static float max(float a, float b) {
        if (Float.isNaN(a)) {
            return b;
        }
        if (Float.isNaN(b)) {
            return a;
        }
        return Math.max(a, b);
    }

    static float min(float a, float b) {
        if (Float.isNaN(a)) {
            return b;
        }
        if (Float.isNaN(b)) {
            return a;
        }
        return Math.min(a, b);
    }

    static float floor(double narrowedToFloat) {
        return (float) Math.floor((float) narrowedToFloat);
    }

    static float pow(float base, float exponent) {
        return (float) Math.pow(base, exponent);
    }

    static float cos(double narrowedToFloat) {
        return (float) Math.cos((float) narrowedToFloat);
    }

    static float toLinear(int value) {
        float v = (float) value / 255;
        if (v <= 0.04045) {
            return (float) (v / 12.92);
        }
        return pow((float) ((v + 0.055) / 1.055), (float) 2.4);
    }

    static int toSRgb(float value) {
        float v = max(0, min(1, value));
        if (v <= 0.0031308) {
            return (int) (v * 12.92 * 255 + 0.5);
        }
        return (int) ((1.055 * pow(v, (float) (1 / 2.4)) - 0.055) * 255 + 0.5);
    }

    static float signPow(float value, float exponent) {
        return Math.copySign(pow(Math.abs(value), exponent), value);
    }
}
