package org.jebol.domain.eval;

public final class HalfFloats {

    private HalfFloats() {
    }

    public static short of(float value) {
        int bits = Float.floatToRawIntBits(value);
        int sign = (bits >>> 16) & 0x8000;
        int exponent = (bits >>> 23) & 0xFF;
        int significand = bits & 0x7F_FFFF;
        if (Float.isNaN(value)) {
            return (short) (sign | 0x7C00 | (significand >>> 13) | 0x200);
        }
        float magnitude = Math.abs(value);
        if (magnitude >= 0x1.ffcp15f + 0x0.002p15f) {
            return (short) (sign | 0x7C00);
        }
        if (magnitude <= 0x1.0p-24f * 0.5f) {
            return (short) sign;
        }
        int halfExponent = Math.getExponent(value);
        int excessShift = 0;
        int hiddenBit = 0;
        if (halfExponent < -14) {
            excessShift = -14 - halfExponent;
            halfExponent = -15;
            hiddenBit = 0x80_0000;
        }
        int wholeSignificand = significand | hiddenBit;
        int shift = 13 + excessShift;
        int halfSignificand = wholeSignificand >> shift;
        int leastSignificant = wholeSignificand & (1 << shift);
        int rounding = wholeSignificand & (1 << (shift - 1));
        int sticky = wholeSignificand & ((1 << (shift - 1)) - 1);
        if (rounding != 0 && (leastSignificant | sticky) != 0) {
            halfSignificand++;
        }
        return (short) (sign | ((halfExponent + 15) << 10) + halfSignificand);
    }

    public static float asFloat(short half) {
        int bits = half & 0xFFFF;
        int exponent = ((bits & 0x7C00) >>> 10) - 15;
        int significand = bits & 0x03FF;
        if (exponent == -15) {
            float magnitude = 0x1p-24f * significand;
            return (bits & 0x8000) != 0 ? -magnitude : magnitude;
        }
        if (exponent == 16) {
            return significand == 0
                    ? (((bits & 0x8000) != 0) ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY)
                    : Float.intBitsToFloat((bits & 0x8000) << 16 | 0x7FC0_0000 | (significand << 13));
        }
        return Float.intBitsToFloat((bits & 0x8000) << 16
                | ((exponent + 127) << 23)
                | (significand << 13));
    }
}
