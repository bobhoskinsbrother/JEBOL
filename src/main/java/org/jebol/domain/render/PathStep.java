package org.jebol.domain.render;

/**
 * One piece of a path, in coordinates measured from the surface.
 *
 * <p>A kind belongs here only where every renderer executes it directly:
 * anything one would have to approximate is a place two could differ.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public sealed interface PathStep {

    PathStepKind kind();

    /** Start a new subpath here. */
    record MoveTo(double across, double down) implements PathStep {

        @Override
        public PathStepKind kind() {
            return PathStepKind.MOVE_TO;
        }
    }

    /** A straight segment to here. */
    record LineTo(double across, double down) implements PathStep {

        @Override
        public PathStepKind kind() {
            return PathStepKind.LINE_TO;
        }
    }

    /** A curve with one control point. */
    record QuadraticTo(
            double controlAcross, double controlDown,
            double across, double down) implements PathStep {

        @Override
        public PathStepKind kind() {
            return PathStepKind.QUADRATIC_TO;
        }
    }

    /** A curve with two. */
    record CubicTo(
            double firstControlAcross, double firstControlDown,
            double secondControlAcross, double secondControlDown,
            double across, double down) implements PathStep {

        @Override
        public PathStepKind kind() {
            return PathStepKind.CUBIC_TO;
        }
    }

    /**
     * A whole ellipse, as a subpath of its own.
     *
     * <p>Centre and two radii, which is how both toolkits take one. CIRCLE and
     * ELLIPSE in the dialect are two ways of saying this: a centre and a
     * radius, or a corner and a diameter.
     */
    record EllipseAt(
            double centreAcross, double centreDown,
            double radiusAcross, double radiusDown) implements PathStep {

        @Override
        public PathStepKind kind() {
            return PathStepKind.ELLIPSE_AT;
        }
    }

    /**
     * Part of an ellipse, swept from an angle by an angle.
     *
     * <p>Degrees, clockwise, as the dialect writes them. Both toolkits take
     * degrees for arcs -- Java2D in {@code Arc2D} and a canvas in radians it
     * converts -- so the conversion happens once in each renderer rather than
     * being guessed at here.
     */
    record ArcTo(
            double centreAcross, double centreDown,
            double radiusAcross, double radiusDown,
            double beginsAt, double turnsThrough,
            boolean closes) implements PathStep {

        @Override
        public PathStepKind kind() {
            return PathStepKind.ARC_TO;
        }
    }

    /** Back to where this subpath started. */
    record Close() implements PathStep {

        @Override
        public PathStepKind kind() {
            return PathStepKind.CLOSE;
        }
    }
}
