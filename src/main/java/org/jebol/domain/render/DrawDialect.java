package org.jebol.domain.render;

import org.jebol.domain.eval.Delect;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A gob's draw block, read once into paint instructions.
 *
 * <p>Thirty commands and ten sub-commands, understood here and drawn as many
 * times as there are renderers. Rebol's C fused the reading and the drawing in
 * {@code host-draw.c}, and the cost is visible in the source we vendor: there
 * is a win32 one and no posix one, so a stock R3 on macOS or Linux draws
 * nothing.
 *
 * <p>The block arrives already parsed by DELECT into a flat run of commands,
 * each followed by its slots in declared order and padded with none. So this
 * walks a list rather than parsing anything, and every number is already the
 * type its slot declared.
 *
 * <p>Every default here is read off the gob being drawn on, which nothing
 * documents and which is what makes the dialect terse: {@code box} alone fills
 * the gob, {@code circle} alone is the biggest circle that fits.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public final class DrawDialect {

    private DrawDialect() {
    }

    /**
     * What a draw block paints on a gob of this size.
     *
     * <p>Answers nothing when the block says nothing this build paints, which
     * is not the same as it being empty: a block of gradients and images reads
     * whole and produces no instructions.
     */
    public static List<PaintInstruction> instructionsFor(
            BlockValue drawBlock, ObjectValue dialect,
            Placement where, double wide, double high) {

        Reading reading = new Reading(dialect, where, wide, high);
        reading.walk(drawBlock);
        return reading.painted();
    }

    /** One walk of one draw block, holding the state as it changes. */
    private static final class Reading {

        private final ObjectValue dialect;
        private final Placement where;
        private final double wide;
        private final double high;
        private final List<PaintInstruction> painted = new ArrayList<>();

        private PaintState state = PaintState.AT_THE_START;
        private Transform transform = Transform.NONE;

        Reading(ObjectValue dialect, Placement where, double wide, double high) {
            this.dialect = dialect;
            this.where = where;
            this.wide = wide;
            this.high = high;
        }

        List<PaintInstruction> painted() {
            return List.copyOf(painted);
        }

        /**
         * Reads the block one command at a time.
         *
         * <p>One at a time rather than all at once, and that is what saves
         * this file from holding a copy of the dialect's command names. Asked
         * for the whole block, DELECT answers a flat run and finding where one
         * command's arguments stop would mean knowing which words are
         * commands. Asked for one, it answers exactly one and says where it
         * got to.
         */
        void walk(BlockValue block) {
            try {
                readEveryCommandOf(block);
            } catch (org.jebol.domain.eval.Raised malformed) {
                // A draw block that will not parse paints what it managed and
                // stops. Letting the raise out would take the whole window
                // down for one mistyped argument, and a gob's content is not
                // a place a script is standing: nobody is there to catch it.
                // What has been painted so far stands, which is the same
                // answer an unpainted command gets.
            }
        }

        private void readEveryCommandOf(BlockValue block) {
            BlockValue left = block;
            BlockValue answer = BlockValue.block();
            while (Delect.read(dialect, left, answer, false, null, Context.unbound())
                    instanceof BlockValue standing) {
                List<Value> read = answer.remaining();
                if (read.isEmpty()) {
                    return;
                }
                if (read.getFirst() instanceof WordValue command) {
                    obey(command.canonical(), read.subList(1, read.size()));
                }
                if (standing.index() <= left.index()) {
                    return;
                }
                left = standing;
                answer = BlockValue.block();
            }
        }

        private void obey(String command, List<Value> arguments) {
            switch (command) {
                case "pen" -> state = state.withStroke(colourIn(arguments));
                case "fill-pen" -> state = state.withFill(colourIn(arguments));
                case "line-width" -> state =
                        state.withLineWidth(numberAt(arguments, 0).orElse(1.0));
                case "line-cap" -> wordAt(arguments, 0).flatMap(LineCap::named)
                        .ifPresent(cap -> state = state.withLineCap(cap));
                case "line-join" -> wordAt(arguments, 0).flatMap(LineJoin::named)
                        .ifPresent(join -> state = state.withLineJoin(join));
                case "fill-rule" -> wordAt(arguments, 0).flatMap(FillRule::named)
                        .ifPresent(rule -> state = state.withFillRule(rule));
                case "anti-alias" -> state = state.withAntiAliasing(
                        arguments.getFirst() instanceof LogicValue said && said.truth());
                case "box" -> paint(aBox(arguments));
                case "circle" -> paint(aCircle(arguments));
                case "ellipse" -> paint(anEllipse(arguments));
                case "line" -> paint(aRunOfPoints(arguments, false));
                case "polygon" -> paint(aRunOfPoints(arguments, true));
                case "curve" -> paint(aCurve(arguments));
                case "arc" -> paint(anArc(arguments));
                case "shape" -> paint(aHandWrittenPath(arguments));
                case "translate" -> movedBy(arguments);
                case "scale" -> scaledBy(arguments);
                case "rotate" -> numberAt(arguments, 0).ifPresent(degrees ->
                        transform = transform.combinedWith(Transform.turnedBy(degrees)));
                case "skew" -> skewedBy(arguments);
                case "matrix" -> matrixFrom(arguments);
                case "reset-matrix" -> transform = Transform.NONE;
                case "push" -> pushed(arguments);
                default -> {
                }
            }
        }

        /**
         * A shape, painted with the state and transform as they stand.
         *
         * <p>A shape with nothing to paint it with is dropped rather than
         * drawn invisibly, because {@code pen off fill-pen off} is a legal way
         * to say so and a renderer should not be handed work with no effect.
         */
        private void paint(List<PathStep> path) {
            if (path.isEmpty() || state.paintsNothing()) {
                return;
            }
            painted.add(new PaintInstruction.Drawn(where, path, transform, state));
        }

        private void movedBy(List<Value> arguments) {
            pairAt(arguments, 0).ifPresent(to -> transform = transform.combinedWith(
                    Transform.movedBy(to.x(), to.y())));
        }

        private void scaledBy(List<Value> arguments) {
            double across = numberAt(arguments, 0).orElse(1.0);
            double down = numberAt(arguments, 1).orElse(across);
            transform = transform.combinedWith(Transform.scaledBy(across, down));
        }

        private void skewedBy(List<Value> arguments) {
            double across = numberAt(arguments, 0).orElse(0.0);
            transform = transform.combinedWith(Transform.skewedBy(across, 0));
        }

        private void matrixFrom(List<Value> arguments) {
            if (arguments.isEmpty()
                    || !(arguments.getFirst() instanceof BlockValue six)) {
                return;
            }
            List<Value> numbers = six.remaining();
            if (numbers.size() < 6) {
                return;
            }
            transform = transform.combinedWith(new Transform(
                    asNumber(numbers.get(0)), asNumber(numbers.get(1)),
                    asNumber(numbers.get(2)), asNumber(numbers.get(3)),
                    asNumber(numbers.get(4)), asNumber(numbers.get(5))));
        }

        /**
         * A block drawn with everything put back afterwards.
         *
         * <p>Which is what makes a piece of drawing composable: it may set
         * whatever it likes and nothing after it is affected. Without it every
         * command would have to undo itself.
         */
        private void pushed(List<Value> arguments) {
            if (arguments.isEmpty()
                    || !(arguments.getFirst() instanceof BlockValue inside)) {
                return;
            }
            PaintState stateBefore = state;
            Transform transformBefore = transform;
            walk(inside);
            state = stateBefore;
            transform = transformBefore;
        }

        /** Two corners, defaulting to the whole gob, with rounded corners. */
        private List<PathStep> aBox(List<Value> arguments) {
            PairValue corner = pairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue end = pairAt(arguments, 1).orElse(PairValue.of(wide, high));
            return List.of(
                    new PathStep.MoveTo(corner.x(), corner.y()),
                    new PathStep.LineTo(end.x(), corner.y()),
                    new PathStep.LineTo(end.x(), end.y()),
                    new PathStep.LineTo(corner.x(), end.y()),
                    new PathStep.Close());
        }

        /**
         * A centre and one radius or two, defaulting to the biggest that fits.
         *
         * <p>{@code min(centre.x, centre.y)} in the C, the centre being half
         * the gob, so a hundred-square gob gives a radius of fifty.
         */
        private List<PathStep> aCircle(List<Value> arguments) {
            PairValue centre = pairAt(arguments, 0)
                    .orElse(PairValue.of(wide / 2, high / 2));
            double across = numberAt(arguments, 1)
                    .orElse(Math.min(centre.x(), centre.y()));
            double down = numberAt(arguments, 2).orElse(across);
            return List.of(new PathStep.EllipseAt(
                    centre.x(), centre.y(), across, down));
        }

        /** A corner and a diameter: the same shape said the other way round. */
        private List<PathStep> anEllipse(List<Value> arguments) {
            PairValue corner = pairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue across = pairAt(arguments, 1).orElse(PairValue.of(wide, high));
            return List.of(new PathStep.EllipseAt(
                    corner.x() + across.x() / 2, corner.y() + across.y() / 2,
                    across.x() / 2, across.y() / 2));
        }

        /**
         * A run of points, open or closed.
         *
         * <p>Fewer than two paints nothing: one point is a position rather
         * than a shape, and painting a dot would invent a decision the dialect
         * never made.
         */
        private List<PathStep> aRunOfPoints(List<Value> arguments, boolean closes) {
            List<PairValue> points = everyPairIn(arguments);
            if (points.size() < 2) {
                return List.of();
            }
            List<PathStep> path = new ArrayList<>();
            path.add(new PathStep.MoveTo(points.getFirst().x(), points.getFirst().y()));
            points.subList(1, points.size()).forEach(point ->
                    path.add(new PathStep.LineTo(point.x(), point.y())));
            if (closes) {
                path.add(new PathStep.Close());
            }
            return List.copyOf(path);
        }

        /**
         * Three points or four: a quadratic or a cubic.
         *
         * <p>The same word meaning two different curves depending on how many
         * arrived, which is the dialect's argument counting doing real work.
         * The C switches on it the same way.
         */
        private List<PathStep> aCurve(List<Value> arguments) {
            List<PairValue> points = everyPairIn(arguments);
            if (points.size() == 3) {
                return List.of(
                        new PathStep.MoveTo(points.get(0).x(), points.get(0).y()),
                        new PathStep.QuadraticTo(
                                points.get(1).x(), points.get(1).y(),
                                points.get(2).x(), points.get(2).y()));
            }
            if (points.size() >= 4) {
                return List.of(
                        new PathStep.MoveTo(points.get(0).x(), points.get(0).y()),
                        new PathStep.CubicTo(
                                points.get(1).x(), points.get(1).y(),
                                points.get(2).x(), points.get(2).y(),
                                points.get(3).x(), points.get(3).y()));
            }
            return List.of();
        }

        /** A quarter turn unless told otherwise: {@code IS_NONE(arg+3) ? 90}. */
        private List<PathStep> anArc(List<Value> arguments) {
            PairValue centre = pairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue radius = pairAt(arguments, 1).orElse(PairValue.of(wide, high));
            double begins = numberAt(arguments, 2).orElse(0.0);
            double turns = numberAt(arguments, 3).orElse(90.0);
            boolean closes = wordAt(arguments, 4).filter("closed"::equals).isPresent();
            return List.of(new PathStep.ArcTo(centre.x(), centre.y(),
                    radius.x(), radius.y(), begins, turns, closes));
        }

        private List<PathStep> aHandWrittenPath(List<Value> arguments) {
            return arguments.isEmpty() || !(arguments.getFirst() instanceof BlockValue steps)
                    ? List.of()
                    : ShapeSubDialect.pathFrom(steps);
        }
    }

    // ---- reading one argument out of a slot -------------------------------

    private static Optional<Colour> colourIn(List<Value> arguments) {
        return arguments.isEmpty() || !(arguments.getFirst() instanceof TupleValue parts)
                ? Optional.empty()
                : Optional.of(Colour.ofTuple(parts));
    }

    private static Optional<PairValue> pairAt(List<Value> arguments, int slot) {
        return slot < arguments.size() && arguments.get(slot) instanceof PairValue pair
                ? Optional.of(pair)
                : Optional.empty();
    }

    private static Optional<Double> numberAt(List<Value> arguments, int slot) {
        if (slot >= arguments.size()) {
            return Optional.empty();
        }
        return switch (arguments.get(slot)) {
            case DecimalValue fraction -> Optional.of(fraction.quantity());
            case IntegerValue whole -> Optional.of((double) whole.magnitude());
            default -> Optional.empty();
        };
    }

    private static Optional<String> wordAt(List<Value> arguments, int slot) {
        return slot < arguments.size() && arguments.get(slot) instanceof WordValue word
                ? Optional.of(word.canonical())
                : Optional.empty();
    }

    private static List<PairValue> everyPairIn(List<Value> arguments) {
        return arguments.stream()
                .filter(PairValue.class::isInstance)
                .map(PairValue.class::cast)
                .toList();
    }

    static double asNumber(Value value) {
        return switch (value) {
            case DecimalValue fraction -> fraction.quantity();
            case IntegerValue whole -> whole.magnitude();
            default -> 0;
        };
    }
}
