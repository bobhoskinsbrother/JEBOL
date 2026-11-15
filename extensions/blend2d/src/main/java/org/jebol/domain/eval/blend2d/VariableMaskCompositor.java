package org.jebol.domain.eval.blend2d;

import static org.jebol.domain.eval.blend2d.Lanes.add;
import static org.jebol.domain.eval.blend2d.Lanes.addBytesSaturated;
import static org.jebol.domain.eval.blend2d.Lanes.andNot;
import static org.jebol.domain.eval.blend2d.Lanes.broadcast;
import static org.jebol.domain.eval.blend2d.Lanes.divideBy255;
import static org.jebol.domain.eval.blend2d.Lanes.expandAlpha;
import static org.jebol.domain.eval.blend2d.Lanes.compareGreaterSigned;
import static org.jebol.domain.eval.blend2d.Lanes.invert255;
import static org.jebol.domain.eval.blend2d.Lanes.laneAt;
import static org.jebol.domain.eval.blend2d.Lanes.maximumBytes;
import static org.jebol.domain.eval.blend2d.Lanes.minimumBytes;
import static org.jebol.domain.eval.blend2d.Lanes.minimumSigned;
import static org.jebol.domain.eval.blend2d.Lanes.minimumUnsigned;
import static org.jebol.domain.eval.blend2d.Lanes.multiply;
import static org.jebol.domain.eval.blend2d.Lanes.of;
import static org.jebol.domain.eval.blend2d.Lanes.pack;
import static org.jebol.domain.eval.blend2d.Lanes.shiftLeftOne;
import static org.jebol.domain.eval.blend2d.Lanes.subtract;
import static org.jebol.domain.eval.blend2d.Lanes.subtractSaturated;
import static org.jebol.domain.eval.blend2d.Lanes.unpack;
import static org.jebol.domain.eval.blend2d.Lanes.withLane;
import static org.jebol.domain.eval.blend2d.Lanes.zeroAlpha;

final class VariableMaskCompositor {

    private static final int ALPHA = 3;

    private static final long ALL_255 = broadcast(0xFF);

    private static final long ALL_127 = broadcast(0x7F);

    private static final long ALPHA_LANE_SET = of(0, 0, 0, 0xFFFF);

    private static final float SMALLEST_DIVISOR = 1e-3f;

    private static final float ONE_IN_255 = 1.0f / 255.0f;

    private VariableMaskCompositor() {
    }

    static int composite(CompositingOperator operator, int destination, int source,
                         int mask, boolean hasMask, boolean useSourceAlpha) {
        long d = unpack(destination);
        long s = unpack(source);
        long m = broadcast(mask);
        boolean useSa = useSourceAlpha || hasMask;
        return switch (operator) {
            case SOURCE_COPY -> hasMask
                    ? pack(divideBy255(add(multiply(d, invert255(m)), multiply(s, m))))
                    : source;
            case SOURCE_OVER -> sourceOver(d, s, source, m, hasMask);
            case SOURCE_IN -> hasMask
                    ? pack(divideBy255(add(multiply(d, invert255(m)),
                            multiply(divideBy255(multiply(expandAlpha(d), s)), m))))
                    : pack(divideBy255(multiply(expandAlpha(d), s)));
            case SOURCE_OUT -> hasMask
                    ? pack(divideBy255(add(multiply(d, invert255(m)),
                            multiply(divideBy255(multiply(invert255(expandAlpha(d)), s)), m))))
                    : pack(divideBy255(multiply(invert255(expandAlpha(d)), s)));
            case SOURCE_ATOP -> sourceAtop(d, maskSource(s, m, hasMask));
            case DESTINATION_OVER -> {
                long scaled = maskSource(s, m, hasMask);
                yield pack(divideBy255(multiply(invert255(expandAlpha(d)), scaled))) + destination;
            }
            case DESTINATION_IN -> hasMask
                    ? pack(divideBy255(multiply(d,
                            invert255(divideBy255(multiply(invert255(expandAlpha(s)), m))))))
                    : pack(divideBy255(multiply(d, expandAlpha(s))));
            case DESTINATION_OUT -> hasMask
                    ? pack(divideBy255(multiply(d, invert255(divideBy255(multiply(expandAlpha(s), m))))))
                    : pack(divideBy255(multiply(d, invert255(expandAlpha(s)))));
            case DESTINATION_ATOP -> destinationAtop(d, s, m, hasMask);
            case XOR -> xor(d, s, m, hasMask);
            case PLUS -> hasMask
                    ? addBytesSaturated(destination, pack(divideBy255(multiply(s, m))))
                    : addBytesSaturated(destination, source);
            case MINUS -> hasMask ? minusMasked(d, s, m) : minus(d, s);
            case MODULATE -> hasMask
                    ? pack(divideBy255(multiply(d, subtract(add(divideBy255(multiply(s, m)), ALL_255), m))))
                    : pack(divideBy255(multiply(d, s)));
            case MULTIPLY -> pack(multiplyBlend(d, maskSource(s, m, hasMask), useSa));
            case SCREEN -> {
                long scaled = maskSource(s, m, hasMask);
                yield pack(add(divideBy255(multiply(d, invert255(scaled))), scaled));
            }
            case OVERLAY -> pack(overlay(d, maskSource(s, m, hasMask), useSa));
            case DARKEN -> pack(darkenOrLighten(d, maskSource(s, m, hasMask), useSa, true));
            case LIGHTEN -> pack(darkenOrLighten(d, maskSource(s, m, hasMask), useSa, false));
            case COLOR_DODGE -> pack(colorDodge(destination, maskSource(s, m, hasMask)));
            case COLOR_BURN -> pack(colorBurn(destination, maskSource(s, m, hasMask)));
            case LINEAR_BURN -> pack(linearBurn(d, maskSource(s, m, hasMask), useSa));
            case LINEAR_LIGHT -> pack(linearLight(d, maskSource(s, m, hasMask)));
            case PIN_LIGHT -> pack(pinLight(d, maskSource(s, m, hasMask), useSa));
            case HARD_LIGHT -> pack(hardLight(d, maskSource(s, m, hasMask)));
            case SOFT_LIGHT -> softLight(destination, maskSource(s, m, hasMask));
            case DIFFERENCE -> pack(difference(d, maskSource(s, m, hasMask)));
            case EXCLUSION -> pack(exclusion(d, maskSource(s, m, hasMask)));
            case DESTINATION_COPY, CLEAR -> throw new IllegalStateException(operator + " is resolved before compositing");
        };
    }

    private static long maskSource(long s, long m, boolean hasMask) {
        return hasMask ? divideBy255(multiply(s, m)) : s;
    }

    private static int sourceOver(long d, long s, int source, long m, boolean hasMask) {
        if (!hasMask) {
            return pack(divideBy255(multiply(d, invert255(expandAlpha(s))))) + source;
        }
        long scaled = divideBy255(multiply(s, m));
        return pack(add(divideBy255(multiply(d, invert255(expandAlpha(scaled)))), scaled));
    }

    private static int sourceAtop(long d, long s) {
        return pack(divideBy255(add(multiply(d, invert255(expandAlpha(s))), multiply(expandAlpha(d), s))));
    }

    private static int destinationAtop(long d, long s, long m, boolean hasMask) {
        if (!hasMask) {
            return pack(divideBy255(add(multiply(d, expandAlpha(s)),
                    multiply(invert255(expandAlpha(d)), s))));
        }
        long scaled = divideBy255(multiply(s, m));
        long kept = invert255(divideBy255(multiply(invert255(expandAlpha(s)), m)));
        return pack(divideBy255(add(multiply(d, kept), multiply(invert255(expandAlpha(d)), scaled))));
    }

    private static int xor(long d, long s, long m, boolean hasMask) {
        long scaled = maskSource(s, m, hasMask);
        return pack(divideBy255(add(multiply(d, invert255(expandAlpha(scaled))),
                multiply(scaled, invert255(expandAlpha(d))))));
    }

    private static int minus(long d, long s) {
        long added = divideBy255(multiply(invert255(expandAlpha(d)), s));
        return pack(add(subtractSaturated(d, zeroAlpha(s)), added));
    }

    private static int minusMasked(long d, long s, long m) {
        long uncovered = invert255(expandAlpha(d));
        long kept = d;
        long result = subtractSaturated(d, s);
        long added = divideBy255(multiply(s, uncovered));
        result = add(zeroAlpha(result), added);
        result = multiply(result, m);
        kept = multiply(kept, invert255(zeroAlpha(m)));
        return pack(divideBy255(add(result, kept)));
    }

    private static long multiplyBlend(long d, long s, boolean useSa) {
        if (useSa) {
            long sourceTerm = add(invert255(expandAlpha(s)), s);
            return divideBy255(add(multiply(d, sourceTerm), multiply(invert255(expandAlpha(d)), s)));
        }
        return divideBy255(multiply(add(d, invert255(expandAlpha(d))), s));
    }

    private static long overlay(long d, long s, boolean useSa) {
        if (useSa) {
            long sourceTimesDa = multiply(expandAlpha(d), s);
            long destinationTimesSa = multiply(expandAlpha(s), d);
            long both = multiply(d, s);
            long result = add(s, d);
            long cross = subtract(sourceTimesDa, both);
            both = zeroAlpha(both);
            cross = add(cross, destinationTimesSa);
            long da = expandAlpha(d);
            cross = subtract(cross, both);
            long isDark = compareGreaterSigned(da, shiftLeftOne(d));
            cross = divideBy255(cross);
            isDark = isDark | ALPHA_LANE_SET;
            long product = expandAlpha(cross);
            cross = subtract(cross ^ isDark, isDark);
            long correction = andNot(product, isDark);
            return subtract(add(result, cross), correction);
        }
        long da = expandAlpha(d);
        long doubled = shiftLeftOne(d);
        long isDark = compareGreaterSigned(da, doubled);
        long difference = subtract(da, doubled);
        difference = subtract(difference ^ isDark, isDark);
        long correction = andNot(difference, isDark);
        difference = add(difference, ALL_255);
        return subtract(divideBy255(multiply(difference, s)), correction);
    }

    private static long darkenOrLighten(long d, long s, boolean useSa, boolean isDarken) {
        long left;
        long right;
        if (useSa) {
            left = add(d, divideBy255(multiply(invert255(expandAlpha(d)), s)));
            right = add(s, divideBy255(multiply(invert255(expandAlpha(s)), d)));
        } else {
            left = add(d, divideBy255(multiply(invert255(expandAlpha(d)), s)));
            right = s;
        }
        return isDarken ? minimumBytes(left, right) : maximumBytes(left, right);
    }

    private static long linearBurn(long d, long s, boolean useSa) {
        if (useSa) {
            long both = divideBy255(multiply(expandAlpha(s), expandAlpha(d)));
            return subtractSaturated(add(d, s), both);
        }
        return subtractSaturated(add(d, s), expandAlpha(d));
    }

    private static long linearLight(long d, long s) {
        long da = expandAlpha(d);
        long sa = expandAlpha(s);
        long kept = divideBy255(add(multiply(d, invert255(sa)), multiply(s, invert255(da))));
        long destinationTimesSa = divideBy255(multiply(d, sa));
        long sourceTimesDa = divideBy255(multiply(s, da));
        long both = expandAlpha(sourceTimesDa);
        long light = add(add(destinationTimesSa, sourceTimesDa), sourceTimesDa);
        light = minimumSigned(subtractSaturated(light, both), both);
        return add(light, kept);
    }

    private static long pinLight(long d, long s, boolean useSa) {
        if (!useSa) {
            long sourceTimesDa = divideBy255(multiply(expandAlpha(d), s));
            long darker = subtract(add(d, s), sourceTimesDa);
            long isLight = compareGreaterSigned(s, ALL_127);
            long lighter = add(s, sourceTimesDa);
            lighter = subtract(lighter, expandAlpha(sourceTimesDa) & isLight);
            return minimumSigned(darker ^ isLight, lighter ^ isLight) ^ isLight;
        }
        long destinationTimesSa = divideBy255(multiply(expandAlpha(s), d));
        long sourceTimesDa = divideBy255(multiply(expandAlpha(d), s));
        long sum = add(d, s);
        long negated = subtract(destinationTimesSa, sum);
        long darker = subtract(sum, sourceTimesDa);
        long lighter = subtract(sourceTimesDa, negated);
        long isLight = compareGreaterSigned(shiftLeftOne(s), expandAlpha(s));
        long both = expandAlpha(subtract(darker, lighter)) & isLight;
        lighter = add(lighter, both);
        return minimumSigned(darker ^ isLight, lighter ^ isLight) ^ isLight;
    }

    private static long hardLight(long d, long s) {
        long sourceTimesDa = multiply(expandAlpha(d), s);
        long destinationTimesSa = multiply(expandAlpha(s), d);
        long both = multiply(d, s);
        long result = add(d, s);
        long cross = subtract(add(subtract(sourceTimesDa, both), destinationTimesSa), both);
        long alphas = divideBy255(expandAlpha(destinationTimesSa));
        cross = divideBy255(cross);
        long isDark = compareGreaterSigned(expandAlpha(s), shiftLeftOne(s));
        cross = subtract(cross ^ isDark, isDark);
        long correction = andNot(alphas, zeroAlpha(isDark));
        return subtract(add(result, cross), correction);
    }

    private static long difference(long d, long s) {
        long smaller = minimumUnsigned(multiply(expandAlpha(s), d), multiply(expandAlpha(d), s));
        smaller = divideBy255(smaller);
        return subtract(subtract(add(d, s), smaller), zeroAlpha(smaller));
    }

    private static long exclusion(long d, long s) {
        long product = divideBy255(multiply(d, s));
        return subtract(subtract(add(d, s), product), zeroAlpha(product));
    }

    private static long addUncoveredTerms(long d, long s) {
        long sourceUncovered = invert255(expandAlpha(s));
        long destinationUncovered = invert255(expandAlpha(d));
        return add(multiply(d, sourceUncovered), multiply(s, destinationUncovered));
    }

    private static long colorDodge(int destination, long s) {
        float[] sf = bgraAsFloats(s);
        float[] df = bgraAsFloats(Lanes.unpack(destination));
        float sa = sf[ALPHA];
        float[] z = new float[4];
        for (int index = 0; index < 4; index++) {
            float divisor = index == ALPHA ? sa : sa - sf[index];
            divisor = Math.max(divisor, SMALLEST_DIVISOR);
            z[index] = df[index] * sa / divisor * sa;
        }
        return addTruncated(addUncoveredTerms(unpack(destination), s), clampToAlpha(z));
    }

    private static long colorBurn(int destination, long s) {
        float[] sf = bgraAsFloats(s);
        float[] df = bgraAsFloats(Lanes.unpack(destination));
        float sa = sf[ALPHA];
        float[] divisors = new float[4];
        for (int index = 0; index < 4; index++) {
            divisors[index] = Math.max(sf[index], SMALLEST_DIVISOR);
        }
        float[] scaled = new float[4];
        for (int index = 0; index < 4; index++) {
            scaled[index] = df[index] * sa;
        }
        float both = scaled[ALPHA];
        float[] z = new float[4];
        for (int index = 0; index < 4; index++) {
            float negated = index == ALPHA ? 0.0f : -scaled[index];
            z[index] = (negated + both) / divisors[index] * divisors[ALPHA];
        }
        float limit = z[ALPHA];
        long burned = 0;
        for (int index = 0; index < 4; index++) {
            float smaller = index == ALPHA ? 0.0f : Math.min(z[index], limit);
            burned = withLane(burned, index, truncate(limit - smaller));
        }
        return divideBy255(add(addUncoveredTerms(unpack(destination), s), burned));
    }

    private static long addTruncated(long kept, float[] z) {
        long truncated = 0;
        for (int index = 0; index < 4; index++) {
            truncated = withLane(truncated, index, truncate(z[index]));
        }
        return divideBy255(add(kept, truncated));
    }

    private static float[] clampToAlpha(float[] z) {
        float limit = z[ALPHA];
        float[] result = new float[4];
        for (int index = 0; index < 4; index++) {
            result[index] = Math.min(z[index], limit);
        }
        return result;
    }

    private static int softLight(int destination, long s) {
        float[] sf = bgraAsFloats(s);
        float[] df = bgraAsFloats(Lanes.unpack(destination));
        for (int index = 0; index < 4; index++) {
            sf[index] *= ONE_IN_255;
            df[index] *= ONE_IN_255;
        }
        float da = df[ALPHA];
        float sa = sf[ALPHA];
        float divisor = Math.max(da, SMALLEST_DIVISOR);
        int pixel = 0;
        for (int index = 0; index < 4; index++) {
            float sourceTimesDa = sf[index] * da;
            float dc = df[index] / divisor;
            float result = df[index] + sf[index] - sourceTimesDa;
            float lift = (sf[index] + sf[index] - sa) * divisor;
            float fourDc = dc * 4.0f;
            float root = (float) Math.sqrt(dc);
            float cubic = (FusedMultiplyAdd.multiplyThenAdd(fourDc, dc, dc) - fourDc + 1.0f) * fourDc;
            float curve = fourDc <= 1.0f ? cubic : root;
            float term = 0.0f < lift ? curve - dc : (1.0f - dc) * dc;
            if (index == ALPHA) {
                lift = 0.0f;
            }
            float scaledUp = (result + lift * term) * 255.0f;
            pixel |= Lanes.saturateSignedToByte(saturateToShort(convertAsX86((float) Math.rint(scaledUp)))) << (8 * index);
        }
        return pixel;
    }

    private static int saturateToShort(int value) {
        return Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE) & 0xFFFF;
    }

    private static float[] bgraAsFloats(long lanes) {
        float[] result = new float[4];
        for (int index = 0; index < 4; index++) {
            result[index] = laneAt(lanes, index);
        }
        return result;
    }

    private static int truncate(float value) {
        return Math.clamp(convertAsX86(value), 0, 0xFFFF);
    }

    private static int convertAsX86(float whole) {
        boolean fitsInInt = whole > -2147483904.0f && whole < 2147483648.0f;
        return fitsInInt ? (int) whole : Integer.MIN_VALUE;
    }
}
