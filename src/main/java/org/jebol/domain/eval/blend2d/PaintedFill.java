package org.jebol.domain.eval.blend2d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class PaintedFill implements PixelSource {

    private final Blend2DPaint paint;

    private final CompositingOperator operator;

    private final int globalAlpha;

    private final double[] deviceToUser;

    private final Map<Integer, Optional<SolidFill>> resolved = new HashMap<>();

    PaintedFill(Blend2DPaint paint, CompositingOperator operator, int globalAlpha, double[] deviceToUser) {
        this.paint = paint;
        this.operator = operator;
        this.globalAlpha = globalAlpha;
        this.deviceToUser = deviceToUser;
    }

    private Optional<SolidFill> resolveAt(int across, int down) {
        double x = across + 0.5;
        double y = down + 0.5;
        double userX = x * deviceToUser[0] + y * deviceToUser[2] + deviceToUser[4];
        double userY = x * deviceToUser[1] + y * deviceToUser[3] + deviceToUser[5];
        int prgb = paint.premultipliedAt(userX, userY);
        return resolved.computeIfAbsent(prgb, colour -> SolidFill.resolvePremultiplied(operator, colour, globalAlpha));
    }

    @Override
    public int composite(int destination, int across, int down) {
        return resolveAt(across, down).map(fill -> fill.composite(destination)).orElse(destination);
    }

    @Override
    public int compositeUnderConstantMask(int destination, int mask, int across, int down) {
        return resolveAt(across, down).map(fill -> fill.compositeUnderConstantMask(destination, mask))
                .orElse(destination);
    }

    @Override
    public int compositeUnderVariableMask(int destination, int mask, int across, int down) {
        return resolveAt(across, down).map(fill -> fill.compositeUnderVariableMask(destination, mask))
                .orElse(destination);
    }

    @Override
    public int compositeUnderAnalyticMask(int destination, int mask, int across, int down) {
        return resolveAt(across, down).map(fill -> fill.compositeUnderAnalyticMask(destination, mask))
                .orElse(destination);
    }
}
