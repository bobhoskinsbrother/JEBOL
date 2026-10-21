package org.jebol.domain.render;

import java.util.ArrayList;
import java.util.List;

final class WedgesAndRings {

    private static final int WEDGES_ROUND = 240;

    private static final int RINGS_OUT = 96;

    private WedgesAndRings() {
    }

    record Piece(List<PathStep> path, Colour colour) {
    }

    static List<Piece> of(Gradient gradient, ClipRectangle covering) {
        double reach = measureHowFarItHasToReach(gradient, covering);
        return gradient.shape() == GradientShape.CONIC
                ? makeAFanOfWedges(gradient, reach)
                : makeRingsOfDiamonds(gradient, reach);
    }

    private static double measureHowFarItHasToReach(Gradient gradient, ClipRectangle covering) {
        double acrossOne = Math.abs(gradient.acrossOffset() - covering.across());
        double acrossOther =
                Math.abs(covering.across() + covering.wide() - gradient.acrossOffset());
        double downOne = Math.abs(gradient.downOffset() - covering.down());
        double downOther =
                Math.abs(covering.down() + covering.high() - gradient.downOffset());
        double corner = Math.hypot(Math.max(acrossOne, acrossOther),
                Math.max(downOne, downOther));
        return Math.max(corner, gradient.measureTheRadius());
    }

    private static List<Piece> makeAFanOfWedges(Gradient gradient, double reach) {
        List<Piece> pieces = new ArrayList<>();
        for (int wedge = 0; wedge < WEDGES_ROUND; wedge++) {
            double from = wedge * 360.0 / WEDGES_ROUND + gradient.angle();
            double to = (wedge + 1) * 360.0 / WEDGES_ROUND + gradient.angle();
            pieces.add(new Piece(
                    makeAWedge(gradient, from, to, reach),
                    findColourAlong(gradient, wedge / (double) (WEDGES_ROUND - 1))));
        }
        return List.copyOf(pieces);
    }

    private static List<PathStep> makeAWedge(
            Gradient gradient, double from, double to, double reach) {

        double overlap = (to - from) * 0.5;
        return List.of(
                new PathStep.MoveTo(gradient.acrossOffset(), gradient.downOffset()),
                new PathStep.LineTo(
                        gradient.acrossOffset()
                                + reach * Math.cos(Math.toRadians(from - overlap)),
                        gradient.downOffset()
                                + reach * Math.sin(Math.toRadians(from - overlap))),
                new PathStep.LineTo(
                        gradient.acrossOffset()
                                + reach * Math.cos(Math.toRadians(to + overlap)),
                        gradient.downOffset()
                                + reach * Math.sin(Math.toRadians(to + overlap))),
                new PathStep.Close());
    }

    private static List<Piece> makeRingsOfDiamonds(Gradient gradient, double reach) {
        List<Piece> pieces = new ArrayList<>();
        for (int ring = RINGS_OUT; ring >= 1; ring--) {
            double howFar = reach * ring / RINGS_OUT;
            pieces.add(new Piece(
                    makeADiamond(gradient, howFar),
                    findColourAlong(gradient, (ring - 1) / (double) (RINGS_OUT - 1))));
        }
        return List.copyOf(pieces);
    }

    private static List<PathStep> makeADiamond(Gradient gradient, double howFar) {
        double across = gradient.acrossOffset();
        double down = gradient.downOffset();
        return List.of(
                new PathStep.MoveTo(across, down - howFar),
                new PathStep.LineTo(across + howFar, down),
                new PathStep.LineTo(across, down + howFar),
                new PathStep.LineTo(across - howFar, down),
                new PathStep.Close());
    }

    private static Colour findColourAlong(Gradient gradient, double share) {
        List<Colour> colours = gradient.colours();
        List<Double> stops = gradient.stops();
        double along = Math.clamp(share, 0, 1);
        for (int at = 1; at < colours.size(); at++) {
            if (along <= stops.get(at)) {
                double span = stops.get(at) - stops.get(at - 1);
                double intoIt = span <= 0 ? 0 : (along - stops.get(at - 1)) / span;
                return blend(colours.get(at - 1), colours.get(at), intoIt);
            }
        }
        return colours.getLast();
    }

    private static Colour blend(Colour from, Colour to, double intoIt) {
        return new Colour(
                mix(from.red(), to.red(), intoIt),
                mix(from.green(), to.green(), intoIt),
                mix(from.blue(), to.blue(), intoIt));
    }

    private static int mix(int from, int to, double intoIt) {
        return Math.clamp((int) Math.round(from + (to - from) * intoIt), 0, 255);
    }
}
