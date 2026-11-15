package org.jebol.domain.eval.blend2d;

import java.util.Optional;

final class PaintedFill implements PixelSource {

    private static final int FULL_MASK = 0xFF;

    private final Blend2DPaint paint;

    private final int globalAlpha;

    private final double[] deviceToUser;

    private final Optional<SimplifiedOperator> simplified;

    PaintedFill(Blend2DPaint paint, CompositingOperator operator, int globalAlpha, double[] deviceToUser) {
        this.paint = paint;
        this.globalAlpha = globalAlpha;
        this.deviceToUser = deviceToUser;
        SimplifiedOperator.SourceFormat format = paint.isOpaque()
                ? SimplifiedOperator.SourceFormat.OPAQUE : SimplifiedOperator.SourceFormat.PREMULTIPLIED;
        simplified = globalAlpha == 0 || paint.isEmpty()
                ? Optional.empty() : SimplifiedOperator.forFormat(operator, format);
    }

    private int fetchAt(int across, int down) {
        double x = across + 0.5;
        double y = down + 0.5;
        double userX = x * deviceToUser[0] + y * deviceToUser[2] + deviceToUser[4];
        double userY = x * deviceToUser[1] + y * deviceToUser[3] + deviceToUser[5];
        return paint.premultipliedAt(userX, userY);
    }

    private int compositeFetched(int destination, int mask, int across, int down) {
        if (simplified.isEmpty() || mask == 0) {
            return destination;
        }
        SimplifiedOperator chosen = simplified.get();
        if (chosen.replacesSource()) {
            return ConstantMaskCompositor.composite(chosen.operator(), destination, chosen.replacement(), mask);
        }
        return VariableMaskCompositor.composite(chosen.operator(), destination, fetchAt(across, down), mask,
                mask != FULL_MASK, chosen.hasSourceAlpha());
    }

    @Override
    public int composite(int destination, int across, int down) {
        return compositeFetched(destination, globalAlpha, across, down);
    }

    @Override
    public int compositeUnderConstantMask(int destination, int mask, int across, int down) {
        return compositeFetched(destination, mask, across, down);
    }

    @Override
    public int compositeUnderVariableMask(int destination, int mask, int across, int down) {
        return compositeFetched(destination, mask, across, down);
    }

    @Override
    public int compositeUnderAnalyticMask(int destination, int mask, int across, int down) {
        if (simplified.isEmpty()) {
            return destination;
        }
        SimplifiedOperator chosen = simplified.get();
        int source = chosen.replacesSource() ? chosen.replacement() : fetchAt(across, down);
        return VariableMaskCompositor.composite(chosen.operator(), destination, source, mask, true,
                chosen.hasSourceAlpha());
    }
}
