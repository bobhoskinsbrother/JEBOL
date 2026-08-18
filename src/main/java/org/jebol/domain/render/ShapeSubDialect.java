package org.jebol.domain.render;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.Datatype;

import java.util.ArrayList;
import java.util.List;

/**
 * SHAPE: a path written by hand, a step at a time.
 *
 * <p>Ten commands, and each has a relative form written as a lit-word. That is
 * what the lit-word marking in DELECT is for, and it is why losing the mark
 * would have been serious rather than cosmetic: every relative path in every
 * drawing would have quietly become an absolute one.
 *
 * <p>Read directly rather than through DELECT, because the sub-dialect's
 * arguments are all pairs and numbers in written order, and the thing DELECT
 * buys -- putting arguments into slots by type -- has nothing to do here.
 * {@code system/dialects/draw} holds the shape commands in the same object as
 * the draw ones for the C's convenience, not because they are read the same
 * way.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
final class ShapeSubDialect {

    private ShapeSubDialect() {
    }

    /** Every step of a shape block, as one path. */
    static List<PathStep> pathFrom(BlockValue steps) {
        Walk walk = new Walk();
        List<Value> written = steps.remaining();
        int at = 0;
        while (at < written.size()) {
            if (!(written.get(at) instanceof WordValue command)) {
                at++;
                continue;
            }
            int ends = whereTheNextStepStarts(written, at + 1);
            walk.obey(command.canonical(),
                    command.datatype() == Datatype.LIT_WORD,
                    written.subList(at + 1, ends));
            at = ends;
        }
        return walk.path();
    }

    private static int whereTheNextStepStarts(List<Value> written, int from) {
        int ahead = from;
        while (ahead < written.size() && !(written.get(ahead) instanceof WordValue)) {
            ahead++;
        }
        return ahead;
    }

    /** Where the path stands, which is what a relative step is measured from. */
    private static final class Walk {

        private final List<PathStep> path = new ArrayList<>();
        private double across;
        private double down;
        private double startedAcross;
        private double startedDown;

        List<PathStep> path() {
            return List.copyOf(path);
        }

        void obey(String command, boolean relative, List<Value> arguments) {
            List<PairValue> points = everyPairIn(arguments);
            switch (command) {
                case "move" -> moveTo(points, relative);
                case "line" -> lineThrough(points, relative);
                case "hline" -> lineTo(numberIn(arguments, relative ? across : 0)
                        + (relative ? across : 0), down);
                case "vline" -> lineTo(across,
                        numberIn(arguments, relative ? down : 0)
                                + (relative ? down : 0));
                case "curve" -> cubicThrough(points, relative);
                case "curv" -> cubicThrough(points, relative);
                case "qcurve" -> quadraticThrough(points, relative);
                case "qcurv" -> quadraticThrough(points, relative);
                case "arc" -> arcThrough(points, arguments, relative);
                case "close" -> close();
                default -> {
                }
            }
        }

        private void moveTo(List<PairValue> points, boolean relative) {
            if (points.isEmpty()) {
                return;
            }
            PairValue to = points.getFirst();
            across = relative ? across + to.x() : to.x();
            down = relative ? down + to.y() : to.y();
            startedAcross = across;
            startedDown = down;
            path.add(new PathStep.MoveTo(across, down));
        }

        private void lineThrough(List<PairValue> points, boolean relative) {
            for (PairValue to : points) {
                lineTo(relative ? across + to.x() : to.x(),
                        relative ? down + to.y() : to.y());
            }
        }

        private void lineTo(double toAcross, double toDown) {
            across = toAcross;
            down = toDown;
            path.add(new PathStep.LineTo(across, down));
        }

        private void quadraticThrough(List<PairValue> points, boolean relative) {
            if (points.size() < 2) {
                return;
            }
            double controlAcross = pointAcross(points.get(0), relative);
            double controlDown = pointDown(points.get(0), relative);
            across = pointAcross(points.get(1), relative);
            down = pointDown(points.get(1), relative);
            path.add(new PathStep.QuadraticTo(
                    controlAcross, controlDown, across, down));
        }

        private void cubicThrough(List<PairValue> points, boolean relative) {
            if (points.size() < 3) {
                quadraticThrough(points, relative);
                return;
            }
            double firstAcross = pointAcross(points.get(0), relative);
            double firstDown = pointDown(points.get(0), relative);
            double secondAcross = pointAcross(points.get(1), relative);
            double secondDown = pointDown(points.get(1), relative);
            across = pointAcross(points.get(2), relative);
            down = pointDown(points.get(2), relative);
            path.add(new PathStep.CubicTo(
                    firstAcross, firstDown, secondAcross, secondDown, across, down));
        }

        private void arcThrough(
                List<PairValue> points, List<Value> arguments, boolean relative) {

            if (points.size() < 2) {
                return;
            }
            double toAcross = pointAcross(points.get(0), relative);
            double toDown = pointDown(points.get(0), relative);
            PairValue radius = points.get(1);
            path.add(new PathStep.ArcTo(toAcross, toDown,
                    radius.x(), radius.y(), 0, 90, false));
            across = toAcross;
            down = toDown;
        }

        private void close() {
            path.add(new PathStep.Close());
            across = startedAcross;
            down = startedDown;
        }

        private double pointAcross(PairValue point, boolean relative) {
            return relative ? across + point.x() : point.x();
        }

        private double pointDown(PairValue point, boolean relative) {
            return relative ? down + point.y() : point.y();
        }
    }

    private static List<PairValue> everyPairIn(List<Value> arguments) {
        return arguments.stream()
                .filter(PairValue.class::isInstance)
                .map(PairValue.class::cast)
                .toList();
    }

    private static double numberIn(List<Value> arguments, double whenAbsent) {
        for (Value each : arguments) {
            if (each instanceof org.jebol.domain.value.DecimalValue fraction) {
                return fraction.quantity();
            }
            if (each instanceof org.jebol.domain.value.IntegerValue whole) {
                return whole.magnitude();
            }
        }
        return whenAbsent;
    }
}
