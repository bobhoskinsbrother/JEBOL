package org.jebol.domain.eval.blend2d;

import java.util.Optional;

public final class SolidFill {

    private static final int OPAQUE = 0xFF;

    private static final int FULL_ALPHA = 255;

    private final CompositingOperator operator;

    private final int premultiplied;

    private final int alpha;

    private final boolean hasSourceAlpha;

    private SolidFill(CompositingOperator operator, int premultiplied, int alpha, boolean hasSourceAlpha) {
        this.operator = operator;
        this.premultiplied = premultiplied;
        this.alpha = alpha;
        this.hasSourceAlpha = hasSourceAlpha;
    }

    public static Optional<SolidFill> resolve(CompositingOperator operator, int argb, int globalAlpha) {
        if (globalAlpha == 0) {
            return Optional.empty();
        }
        return SimplifiedOperator.forSolid(operator, argb)
                .map(simplified -> new SolidFill(simplified.operator(),
                        simplified.replacesSource() ? simplified.replacement() : premultiply(argb),
                        globalAlpha, simplified.hasSourceAlpha()));
    }

    public static int scaleAlpha(double alpha) {
        double clamped = Math.clamp(alpha, 0.0, 1.0);
        double scaled = clamped * FULL_ALPHA;
        double nearest = Math.rint(scaled);
        return (int) nearest + (nearest - scaled == -0.5 ? 1 : 0);
    }

    public static int premultiply(int argb) {
        int alpha = argb >>> 24;
        int result = alpha << 24;
        for (int shift = 0; shift < 24; shift += 8) {
            int channel = (argb >>> shift) & OPAQUE;
            result |= Lanes.laneAt(Lanes.divideBy255(Lanes.broadcast(channel * alpha)), 0) << shift;
        }
        return result;
    }

    public int composite(int destination) {
        return ConstantMaskCompositor.composite(operator, destination, premultiplied, alpha);
    }

    public int composite(int destination, int coverage) {
        if (coverage == 0) {
            return destination;
        }
        if (coverage == OPAQUE && alpha == OPAQUE) {
            return composite(destination);
        }
        int mask = alpha == OPAQUE ? coverage : Lanes.laneAt(Lanes.divideBy255(Lanes.broadcast(coverage * alpha)), 0);
        return VariableMaskCompositor.composite(operator, destination, premultiplied, mask, true, hasSourceAlpha);
    }
}
