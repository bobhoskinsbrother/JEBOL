package org.jebol.domain.render;

import java.util.ArrayList;
import java.util.List;

final class Arrowheads {

    private static final double LINE_WIDTHS_LONG = 4;

    private static final double HOW_WIDE_AGAINST_ITS_LENGTH = 0.5;

    private Arrowheads() {
    }

    static List<PathStep> onlyTheHeadsOf(
            List<PathStep> path, ArrowEnds ends, double lineWidth) {

        if (closesOnItself(path) || path.size() < 2) {
            return List.of();
        }
        List<PathStep> heads = new ArrayList<>();
        double length = LINE_WIDTHS_LONG * lineWidth;
        if (ends.atTheEnd()) {
            heads.addAll(aHeadAt(
                    pointOf(path.get(path.size() - 1)),
                    pointOf(path.get(path.size() - 2)), length));
        }
        if (ends.atTheStart()) {
            heads.addAll(aHeadAt(
                    pointOf(path.getFirst()), pointOf(path.get(1)), length));
        }
        return List.copyOf(heads);
    }

    private static boolean closesOnItself(List<PathStep> path) {
        return path.stream().anyMatch(step -> step.kind() == PathStepKind.CLOSE)
                || path.stream().anyMatch(step -> step.kind() == PathStepKind.ELLIPSE_AT);
    }

    private record Point(double across, double down) {
    }

    private static Point pointOf(PathStep step) {
        return switch (step) {
            case PathStep.MoveTo moved -> new Point(moved.across(), moved.down());
            case PathStep.LineTo drawn -> new Point(drawn.across(), drawn.down());
            case PathStep.QuadraticTo curved ->
                    new Point(curved.across(), curved.down());
            case PathStep.CubicTo curved -> new Point(curved.across(), curved.down());
            default -> new Point(0, 0);
        };
    }

    private static List<PathStep> aHeadAt(Point tip, Point cameFrom, double length) {
        double acrossWay = tip.across() - cameFrom.across();
        double downWay = tip.down() - cameFrom.down();
        double howFar = Math.hypot(acrossWay, downWay);
        if (howFar == 0) {
            return List.of();
        }
        double acrossStep = acrossWay / howFar;
        double downStep = downWay / howFar;
        double backAcross = tip.across() - acrossStep * length;
        double backDown = tip.down() - downStep * length;
        double halfWidth = length * HOW_WIDE_AGAINST_ITS_LENGTH;
        return List.of(
                new PathStep.MoveTo(tip.across(), tip.down()),
                new PathStep.LineTo(
                        backAcross - downStep * halfWidth,
                        backDown + acrossStep * halfWidth),
                new PathStep.LineTo(
                        backAcross + downStep * halfWidth,
                        backDown - acrossStep * halfWidth),
                new PathStep.Close());
    }
}
