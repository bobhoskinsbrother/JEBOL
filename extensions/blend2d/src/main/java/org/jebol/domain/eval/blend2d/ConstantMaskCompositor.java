package org.jebol.domain.eval.blend2d;

import static org.jebol.domain.eval.blend2d.Lanes.add;
import static org.jebol.domain.eval.blend2d.Lanes.addBytesSaturated;
import static org.jebol.domain.eval.blend2d.Lanes.broadcast;
import static org.jebol.domain.eval.blend2d.Lanes.divideBy255;
import static org.jebol.domain.eval.blend2d.Lanes.expandAlpha;
import static org.jebol.domain.eval.blend2d.Lanes.fillAlpha255;
import static org.jebol.domain.eval.blend2d.Lanes.invert255;
import static org.jebol.domain.eval.blend2d.Lanes.laneAt;
import static org.jebol.domain.eval.blend2d.Lanes.maximumBytes;
import static org.jebol.domain.eval.blend2d.Lanes.minimumBytes;
import static org.jebol.domain.eval.blend2d.Lanes.minimumUnsigned;
import static org.jebol.domain.eval.blend2d.Lanes.multiply;
import static org.jebol.domain.eval.blend2d.Lanes.multiplyBy257High;
import static org.jebol.domain.eval.blend2d.Lanes.pack;
import static org.jebol.domain.eval.blend2d.Lanes.shiftLeft;
import static org.jebol.domain.eval.blend2d.Lanes.subtract;
import static org.jebol.domain.eval.blend2d.Lanes.subtractSaturated;
import static org.jebol.domain.eval.blend2d.Lanes.unpack;
import static org.jebol.domain.eval.blend2d.Lanes.zeroAlpha;

final class ConstantMaskCompositor {

    private static final int ALPHA = 3;

    private static final int OPAQUE = 0xFF;

    private static final long HALF = broadcast(0x80);

    private static final long ALL_255 = broadcast(0xFF);

    private ConstantMaskCompositor() {
    }

    static int composite(CompositingOperator operator, int destination, int source, int mask) {
        boolean hasMask = mask != OPAQUE;
        long d = unpack(destination);
        long s = unpack(source);
        long m = broadcast(mask);
        return switch (operator) {
            case SOURCE_COPY -> hasMask
                    ? pack(multiplyBy257High(add(multiply(d, invert255(m)), add(multiply(s, m), HALF))))
                    : source;
            case SOURCE_OVER -> {
                long scaled = hasMask ? divideBy255(multiply(s, m)) : s;
                long weighted = add(subtract(shiftLeft(scaled, 8), scaled), HALF);
                yield pack(multiplyBy257High(add(multiply(d, invert255(expandAlpha(scaled))), weighted)));
            }
            case SCREEN -> {
                long scaled = hasMask ? divideBy255(multiply(s, m)) : s;
                long weighted = add(subtract(shiftLeft(scaled, 8), scaled), HALF);
                yield pack(multiplyBy257High(add(multiply(d, invert255(scaled)), weighted)));
            }
            case SOURCE_IN -> hasMask
                    ? pack(divideBy255(add(multiply(expandAlpha(d), divideBy255(multiply(s, m))),
                            multiply(d, invert255(m)))))
                    : pack(divideBy255(multiply(expandAlpha(d), s)));
            case SOURCE_OUT -> hasMask
                    ? pack(divideBy255(add(multiply(d, invert255(m)),
                            multiply(invert255(expandAlpha(d)), divideBy255(multiply(s, m))))))
                    : pack(divideBy255(multiply(invert255(expandAlpha(d)), s)));
            case SOURCE_ATOP -> {
                long scaled = hasMask ? divideBy255(multiply(s, m)) : s;
                yield pack(divideBy255(add(multiply(d, invert255(expandAlpha(scaled))),
                        multiply(expandAlpha(d), scaled))));
            }
            case XOR -> {
                long scaled = hasMask ? divideBy255(multiply(s, m)) : s;
                yield pack(destinationAtopShape(d, scaled, invert255(expandAlpha(scaled))));
            }
            case DARKEN, LIGHTEN -> {
                long scaled = hasMask ? divideBy255(multiply(s, m)) : s;
                long left = add(divideBy255(multiply(invert255(expandAlpha(d)), scaled)), d);
                long right = add(divideBy255(multiply(d, invert255(expandAlpha(scaled)))), scaled);
                yield pack(operator == CompositingOperator.DARKEN
                        ? minimumBytes(right, left)
                        : maximumBytes(right, left));
            }
            case DESTINATION_OVER -> {
                long scaled = hasMask ? divideBy255(multiply(s, m)) : s;
                yield pack(divideBy255(multiply(invert255(expandAlpha(d)), scaled))) + destination;
            }
            case DESTINATION_IN -> {
                long kept = hasMask
                        ? invert255(divideBy255(multiply(invert255(expandAlpha(s)), m)))
                        : expandAlpha(s);
                yield pack(divideBy255(multiply(d, kept)));
            }
            case DESTINATION_OUT -> {
                long kept = hasMask
                        ? invert255(divideBy255(multiply(m, expandAlpha(s))))
                        : invert255(expandAlpha(s));
                yield pack(divideBy255(multiply(d, kept)));
            }
            case DESTINATION_ATOP -> {
                if (!hasMask) {
                    yield pack(destinationAtopShape(d, s, expandAlpha(s)));
                }
                long scaled = divideBy255(multiply(s, m));
                yield pack(destinationAtopShape(d, scaled, expandAlpha(add(invert255(m), scaled))));
            }
            case MULTIPLY -> {
                if (!hasMask) {
                    yield pack(destinationAtopShape(d, s, add(invert255(expandAlpha(s)), s)));
                }
                long scaled = divideBy255(multiply(s, m));
                yield pack(destinationAtopShape(d, scaled, add(broadcast(laneAt(scaled, ALPHA) ^ OPAQUE), scaled)));
            }
            case PLUS -> addBytesSaturated(destination, hasMask ? pack(divideBy255(multiply(s, m))) : source);
            case MINUS -> hasMask ? minusMasked(d, s, m) : minus(d, s);
            case MODULATE -> {
                long factor = hasMask ? subtract(add(divideBy255(multiply(s, m)), ALL_255), m) : s;
                yield pack(divideBy255(multiply(d, factor)));
            }
            case LINEAR_BURN -> {
                long scaled = hasMask ? divideBy255(multiply(s, m)) : s;
                long both = divideBy255(multiply(expandAlpha(d), expandAlpha(scaled)));
                yield pack(subtractSaturated(add(d, scaled), both));
            }
            case DIFFERENCE -> {
                long scaled = hasMask ? divideBy255(multiply(s, m)) : s;
                long smaller = divideBy255(minimumUnsigned(multiply(expandAlpha(scaled), d),
                        multiply(expandAlpha(d), scaled)));
                yield pack(subtract(subtract(add(d, scaled), smaller), zeroAlpha(smaller)));
            }
            case EXCLUSION -> {
                long scaled = hasMask ? divideBy255(multiply(s, m)) : s;
                long product = divideBy255(multiply(d, scaled));
                yield pack(subtract(subtract(add(d, scaled), product), zeroAlpha(product)));
            }
            default -> hasMask && operator.scalesSourceByMask()
                    ? VariableMaskCompositor.composite(operator, destination,
                            pack(divideBy255(multiply(s, m))), OPAQUE, false, true)
                    : VariableMaskCompositor.composite(operator, destination, source, mask, hasMask, true);
        };
    }

    private static long destinationAtopShape(long d, long sourceTerm, long destinationFactor) {
        return divideBy255(add(multiply(d, destinationFactor), multiply(invert255(expandAlpha(d)), sourceTerm)));
    }

    private static int minus(long d, long s) {
        long added = divideBy255(multiply(invert255(expandAlpha(d)), s));
        return pack(add(subtractSaturated(d, zeroAlpha(s)), added));
    }

    private static int minusMasked(long d, long s, long m) {
        long covered = fillAlpha255(zeroAlpha(m));
        long uncovered = zeroAlpha(invert255(m));
        long kept = multiply(d, uncovered);
        long result = subtractSaturated(d, zeroAlpha(s));
        long added = divideBy255(multiply(invert255(expandAlpha(d)), s));
        result = multiply(add(result, added), covered);
        return pack(divideBy255(add(result, kept)));
    }
}
