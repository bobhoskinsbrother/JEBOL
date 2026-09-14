package org.jebol.domain.render;

import org.jebol.domain.eval.Delect;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A gob's draw block, read once into paint instructions.
 *
 * <p>The block arrives already parsed by DELECT into a flat run of commands,
 * each followed by its slots in declared order and padded with none, so this
 * walks a list rather than parsing anything.
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
     * What a draw block paints on a gob of this size. Nothing is not the same
     * as empty: a block of gradients and images reads whole and paints none.
     */
    public static List<PaintInstruction> instructionsFor(
            BlockValue drawBlock, ObjectValue dialect,
            Placement where, double wide, double high) {

        Reading reading = new Reading(dialect, where, wide, high);
        reading.walk(drawBlock);
        return reading.painted();
    }

    private static final class Reading {

        private final ObjectValue dialect;
        private final Placement where;
        private final double wide;
        private final double high;
        private final List<PaintInstruction> painted = new ArrayList<>();

        private PaintState state = PaintState.AT_THE_START;
        private Transform transform = Transform.NONE;
        private ClipRectangle clip;
        private Optional<Colour> gapColour = Optional.empty();
        private Optional<Colour> arrowColour = Optional.empty();
        private double gamma = 1;
        private Resampling resampling = Resampling.BILINEAR;

        Reading(ObjectValue dialect, Placement where, double wide, double high) {
            this.dialect = dialect;
            this.where = where;
            this.wide = wide;
            this.high = high;
            this.clip = where.clip();
        }

        List<PaintInstruction> painted() {
            return List.copyOf(painted);
        }

        void walk(BlockValue block) {
            try {
                readEveryCommandOfOneAtATime(block);
            } catch (org.jebol.domain.eval.Raised
                    malformedSoWhatWasPaintedStandsRatherThanTakingTheWindowDown) {
            }
        }

        private void readEveryCommandOfOneAtATime(BlockValue block) {
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
                case "line-pattern" -> {
                    state = state.withDashes(everyNumberIn(arguments));
                    gapColour = colourAt(arguments, THE_DASH_COLOUR);
                }
                case "arrow" -> {
                    state = state.withArrowEnds(pairAt(arguments, 1)
                            .map(ArrowEnds::fromFlags).orElse(ArrowEnds.NEITHER));
                    arrowColour = colourIn(arguments);
                }
                case "grad-pen" -> state =
                        state.withFillGradient(aGradientFrom(arguments));
                case "gamma" -> gamma = numberAt(arguments, 0).orElse(1.0);
                case "image-filter" -> resampling = wordAt(arguments, 0)
                        .flatMap(Resampling::named).orElse(Resampling.BILINEAR);
                case "box" -> paint(aBox(arguments));
                case "circle" -> paint(aCircle(arguments));
                case "ellipse" -> paint(anEllipse(arguments));
                case "line" -> paint(aRunOfPoints(arguments, false));
                case "polygon" -> paint(aRunOfPoints(arguments, true));
                case "curve" -> paint(aCurve(arguments));
                case "arc" -> paint(anArc(arguments));
                case "triangle" -> paintTheTriangle(arguments);
                case "spline" -> paint(aSplineThroughEveryPoint(arguments));
                case "shape" -> paint(aHandWrittenPath(arguments));
                case "image" -> showTheImage(arguments);
                case "text" -> writeTheText(arguments);
                case "clip" -> clippedTo(arguments);
                case "transform" -> transformedAboutACentre(arguments);
                case "invert-matrix" -> transform = transform.inverted();
                case "translate" -> movedBy(arguments);
                case "scale" -> scaledBy(arguments);
                case "rotate" -> numberAt(arguments, 0).ifPresent(degrees ->
                        transform = transform.combinedWith(Transform.turnedBy(degrees)));
                case "skew" -> skewedBy(arguments);
                case "matrix" -> matrixFrom(arguments);
                case "reset-matrix" -> transform = Transform.NONE;
                case "push" -> drawnWithEverythingPutBackAfterwards(arguments);
                default -> {
                }
            }
        }

        private void paint(List<PathStep> path) {
            if (path.isEmpty() || state.paintsNothing()) {
                return;
            }
            paintTheGapsBetweenTheDashes(path);
            paintAGradientNoToolkitHas(path);
            painted.add(new PaintInstruction.Drawn(whereTheClipStandsNow(),
                    path, transform, theStateARendererGets()));
            paintAnyArrowheads(path);
        }

        private PaintState theStateARendererGets() {
            PaintState carried = state.fillGradient()
                    .filter(gradient -> !gradient.shape().aToolkitCanDrawIt())
                    .isPresent()
                    ? state.withFillGradient(Optional.empty())
                            .withFill(Optional.empty())
                    : state;
            return underTheGamma(carried);
        }

        private PaintState underTheGamma(PaintState carried) {
            if (gamma == 1) {
                return carried;
            }
            return carried
                    .withStroke(carried.strokeColour()
                            .map(colour -> colour.underGamma(gamma)))
                    .withFill(carried.fillColour()
                            .map(colour -> colour.underGamma(gamma)))
                    .withFillGradient(carried.fillGradient().map(this::underTheGamma));
        }

        private Gradient underTheGamma(Gradient gradient) {
            return new Gradient(gradient.shape(),
                    gradient.acrossOffset(), gradient.downOffset(),
                    gradient.from(), gradient.to(), gradient.angle(),
                    gradient.colours().stream()
                            .map(colour -> colour.underGamma(gamma)).toList(),
                    gradient.stops());
        }

        private void paintAGradientNoToolkitHas(List<PathStep> path) {
            Optional<Gradient> awkward = state.fillGradient()
                    .filter(gradient -> !gradient.shape().aToolkitCanDrawIt());
            if (awkward.isEmpty()) {
                return;
            }
            PaintState wasStanding = state;
            for (WedgesAndRings.Piece piece
                    : WedgesAndRings.of(awkward.orElseThrow(), theAreaCovered(path))) {
                state = wasStanding
                        .withStroke(Optional.empty())
                        .withFillGradient(Optional.empty())
                        .withFill(Optional.of(piece.colour()));
                painted.add(new PaintInstruction.Drawn(
                        clippedToTheShapeItFills(path), piece.path(), transform, state));
            }
            state = wasStanding;
        }

        private Placement clippedToTheShapeItFills(List<PathStep> path) {
            ClipRectangle area = theAreaCovered(path);
            return new Placement(where.across(), where.down(),
                    where.wide(), where.high(), clip.overlapWith(area),
                    where.opacity()).insideTheShape(path);
        }

        private ClipRectangle theAreaCovered(List<PathStep> path) {
            double left = Double.MAX_VALUE;
            double top = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            double bottom = -Double.MAX_VALUE;
            for (double[] corner : cornersOf(path)) {
                left = Math.min(left, corner[0]);
                top = Math.min(top, corner[1]);
                right = Math.max(right, corner[0]);
                bottom = Math.max(bottom, corner[1]);
            }
            if (left > right) {
                return clip;
            }
            return new ClipRectangle(
                    where.across() + (int) Math.floor(left),
                    where.down() + (int) Math.floor(top),
                    (int) Math.ceil(right - left) + 1,
                    (int) Math.ceil(bottom - top) + 1);
        }

        private static List<double[]> cornersOf(List<PathStep> path) {
            List<double[]> corners = new ArrayList<>();
            for (PathStep step : path) {
                switch (step) {
                    case PathStep.MoveTo to -> corners.add(
                            new double[] {to.across(), to.down()});
                    case PathStep.LineTo to -> corners.add(
                            new double[] {to.across(), to.down()});
                    case PathStep.QuadraticTo to -> corners.add(
                            new double[] {to.across(), to.down()});
                    case PathStep.CubicTo to -> corners.add(
                            new double[] {to.across(), to.down()});
                    case PathStep.EllipseAt round -> {
                        corners.add(new double[] {
                                round.centreAcross() - round.radiusAcross(),
                                round.centreDown() - round.radiusDown()});
                        corners.add(new double[] {
                                round.centreAcross() + round.radiusAcross(),
                                round.centreDown() + round.radiusDown()});
                    }
                    case PathStep.ArcTo arc -> {
                        corners.add(new double[] {
                                arc.centreAcross() - arc.radiusAcross(),
                                arc.centreDown() - arc.radiusDown()});
                        corners.add(new double[] {
                                arc.centreAcross() + arc.radiusAcross(),
                                arc.centreDown() + arc.radiusDown()});
                    }
                    default -> {
                    }
                }
            }
            return corners;
        }

        private void paintTheGapsBetweenTheDashes(List<PathStep> path) {
            if (state.dashes().isEmpty() || gapColour.isEmpty()
                    || state.strokeColour().isEmpty()) {
                return;
            }
            List<PathStep> gaps = Dashes.theGapsIn(path, state.dashes());
            if (gaps.isEmpty()) {
                return;
            }
            painted.add(new PaintInstruction.Drawn(whereTheClipStandsNow(), gaps,
                    transform, state.withDashes(List.of())
                            .withFill(Optional.empty())
                            .withFillGradient(Optional.empty())
                            .withStroke(gapColour)));
        }

        private void paintAnyArrowheads(List<PathStep> path) {
            if (!state.arrowEnds().anyAtAll()) {
                return;
            }
            List<PathStep> heads =
                    Arrowheads.onlyTheHeadsOf(path, state.arrowEnds(), state.lineWidth());
            if (heads.isEmpty()) {
                return;
            }
            Optional<Colour> filled =
                    arrowColour.isPresent() ? arrowColour : state.strokeColour();
            painted.add(new PaintInstruction.Drawn(whereTheClipStandsNow(), heads,
                    transform, state.withDashes(List.of())
                            .withStroke(Optional.empty())
                            .withFillGradient(Optional.empty())
                            .withFill(filled)));
        }

        private static final int THE_GRADIENT_TYPE = 0;
        private static final int THE_GRADIENT_OFFSET = 2;
        private static final int THE_GRADIENT_RANGE_FROM = 4;
        private static final int THE_GRADIENT_RANGE_TO = 5;
        private static final int THE_GRADIENT_ANGLE = 6;
        private static final int THE_GRADIENT_COLOURS = 9;

        private Optional<Gradient> aGradientFrom(List<Value> arguments) {
            Optional<String> asked = wordAt(arguments, THE_GRADIENT_TYPE);
            if (asked.isEmpty()) {
                return Optional.empty();
            }
            Optional<GradientShape> shape = GradientShape.named(asked.orElseThrow());
            List<Colour> colours = everyColourIn(arguments, THE_GRADIENT_COLOURS);
            if (shape.isEmpty() || colours.size() < 2) {
                return Optional.empty();
            }
            PairValue offset = pairAt(arguments, THE_GRADIENT_OFFSET)
                    .orElse(PairValue.of(0, 0));
            double angle = numberAt(arguments, THE_GRADIENT_ANGLE).orElse(0.0);
            return Optional.of(new Gradient(
                    shape.orElseThrow(),
                    offset.x(), offset.y(),
                    numberAt(arguments, THE_GRADIENT_RANGE_FROM).orElse(0.0),
                    numberAt(arguments, THE_GRADIENT_RANGE_TO).orElse(Math.max(wide, high)),
                    shape.orElseThrow() == GradientShape.DIAGONAL ? angle + 45 : angle,
                    colours, stopsFor(colours.size(), shape.orElseThrow())));
        }

        private static List<Colour> everyColourIn(List<Value> arguments, int slot) {
            if (slot >= arguments.size()
                    || !(arguments.get(slot) instanceof BlockValue given)) {
                return List.of();
            }
            return given.remaining().stream()
                    .filter(TupleValue.class::isInstance)
                    .map(TupleValue.class::cast)
                    .map(Colour::ofTuple)
                    .toList();
        }

        private static List<Double> stopsFor(int howMany, GradientShape shape) {
            List<Double> stops = new ArrayList<>();
            for (int at = 0; at < howMany; at++) {
                double evenly = (double) at / (howMany - 1);
                stops.add(shape == GradientShape.CUBIC
                        ? evenly * evenly * (3 - 2 * evenly)
                        : evenly);
            }
            return List.copyOf(stops);
        }

        private Placement whereTheClipStandsNow() {
            return clip == where.clip()
                    ? where
                    : new Placement(where.across(), where.down(),
                            where.wide(), where.high(), clip, where.opacity());
        }

        private static final int THE_IMAGE_KEY_COLOUR = 1;

        private void showTheImage(List<Value> arguments) {
            if (arguments.isEmpty()
                    || !(arguments.getFirst() instanceof ImageValue given)) {
                return;
            }
            ImageValue pixels = colourAt(arguments, THE_IMAGE_KEY_COLOUR)
                    .map(key -> PicturesWorkedOutHere.withThatColourSeeThrough(given, key))
                    .orElse(given);
            List<PairValue> corners = everyPairIn(arguments);
            if (corners.size() >= 3) {
                showTheImagePulledOntoItsCorners(pixels, corners);
                return;
            }
            PairValue at = corners.isEmpty() ? PairValue.of(0, 0) : corners.getFirst();
            PairValue size = corners.size() >= 2
                    ? PairValue.of(corners.get(1).x() - at.x(),
                            corners.get(1).y() - at.y())
                    : pixels.size();
            addThePicture(pixels, at.x(), at.y(), size.x(), size.y());
        }

        private void showTheImagePulledOntoItsCorners(
                ImageValue pixels, List<PairValue> corners) {

            PairValue[] four = corners.size() >= 4
                    ? new PairValue[] {corners.get(0), corners.get(1),
                            corners.get(2), corners.get(3)}
                    : new PairValue[] {corners.get(0), corners.get(1),
                            corners.get(2), corners.get(0)};
            double left = Math.min(Math.min(four[0].x(), four[1].x()),
                    Math.min(four[2].x(), four[3].x()));
            double top = Math.min(Math.min(four[0].y(), four[1].y()),
                    Math.min(four[2].y(), four[3].y()));
            double right = Math.max(Math.max(four[0].x(), four[1].x()),
                    Math.max(four[2].x(), four[3].x()));
            double bottom = Math.max(Math.max(four[0].y(), four[1].y()),
                    Math.max(four[2].y(), four[3].y()));
            int boxWide = (int) Math.round(right - left);
            int boxHigh = (int) Math.round(bottom - top);
            if (boxWide <= 0 || boxHigh <= 0) {
                return;
            }
            addThePicture(PicturesWorkedOutHere.pulledOntoFourCorners(
                            pixels, four, boxWide, boxHigh,
                            (int) Math.round(left), (int) Math.round(top)),
                    left, top, boxWide, boxHigh);
        }

        private void addThePicture(
                ImageValue pixels, double at, double down, double sizeAcross,
                double sizeDown) {

            int wideOnTheSurface = (int) Math.round(sizeAcross);
            int highOnTheSurface = (int) Math.round(sizeDown);
            ImageValue atThatSize =
                    resampling.resized(pixels, wideOnTheSurface, highOnTheSurface);
            painted.add(new PaintInstruction.Picture(
                    new Placement(
                            where.across() + (int) Math.round(at),
                            where.down() + (int) Math.round(down),
                            wideOnTheSurface, highOnTheSurface,
                            clip, where.opacity()),
                    atThatSize, sizeAcross, sizeDown, transform));
        }

        private static final int THE_TEXT_OFFSET = 1;
        private static final int THE_TEXT_BLOCK = 3;

        private void writeTheText(List<Value> arguments) {
            if (THE_TEXT_BLOCK >= arguments.size()
                    || !(arguments.get(THE_TEXT_BLOCK) instanceof BlockValue written)
                    || state.strokeColour().isEmpty()) {
                return;
            }
            PairValue at = pairAt(arguments, THE_TEXT_OFFSET)
                    .orElse(PairValue.of(0, 0));
            PairValue size = pairAt(arguments, THE_TEXT_OFFSET + 1)
                    .orElse(PairValue.of(wide - at.x(), high - at.y()));
            double alongTheLine = 0;
            for (RichText.Run run
                    : RichText.runsIn(written, state.strokeColour().orElseThrow())) {
                painted.add(new PaintInstruction.Writing(
                        new Placement(
                                where.across()
                                        + (int) Math.round(at.x() + alongTheLine),
                                where.down() + (int) Math.round(at.y()),
                                (int) Math.round(size.x()), (int) Math.round(size.y()),
                                clip, where.opacity()),
                        run.text(), run.colour(), run.size(), run.bold(), run.italic()));
                alongTheLine += RichText.howWideItRunsOut(run);
            }
        }

        private void clippedTo(List<Value> arguments) {
            Optional<PairValue> origin = pairAt(arguments, 0);
            Optional<PairValue> corner = pairAt(arguments, 1);
            if (origin.isEmpty() || corner.isEmpty()) {
                clip = where.clip();
                return;
            }
            clip = where.clip().overlapWith(theBoxBetween(origin.get(), corner.get()));
        }

        private ClipRectangle theBoxBetween(PairValue origin, PairValue corner) {
            int left = (int) Math.round(Math.min(origin.x(), corner.x()));
            int top = (int) Math.round(Math.min(origin.y(), corner.y()));
            int right = (int) Math.round(Math.max(origin.x(), corner.x()));
            int bottom = (int) Math.round(Math.max(origin.y(), corner.y()));
            return new ClipRectangle(
                    where.across() + left, where.down() + top,
                    right - left, bottom - top);
        }

        private void transformedAboutACentre(List<Value> arguments) {
            double angle = numberAt(arguments, 0).orElse(0.0);
            PairValue centre = pairAt(arguments, 1).orElse(PairValue.of(0, 0));
            double acrossScale = numberAt(arguments, 2).orElse(1.0);
            double downScale = numberAt(arguments, 3).orElse(acrossScale);
            PairValue move = pairAt(arguments, 4).orElse(PairValue.of(0, 0));
            transform = transform
                    .combinedWith(Transform.movedBy(move.x(), move.y()))
                    .combinedWith(Transform.movedBy(centre.x(), centre.y()))
                    .combinedWith(Transform.turnedBy(angle))
                    .combinedWith(Transform.scaledBy(acrossScale, downScale))
                    .combinedWith(Transform.movedBy(-centre.x(), -centre.y()));
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

        private void drawnWithEverythingPutBackAfterwards(List<Value> arguments) {
            if (arguments.isEmpty()
                    || !(arguments.getFirst() instanceof BlockValue inside)) {
                return;
            }
            PaintState stateBefore = state;
            Transform transformBefore = transform;
            ClipRectangle clipBefore = clip;
            Optional<Colour> gapBefore = gapColour;
            Optional<Colour> arrowBefore = arrowColour;
            double gammaBefore = gamma;
            Resampling resamplingBefore = resampling;
            walk(inside);
            gamma = gammaBefore;
            resampling = resamplingBefore;
            state = stateBefore;
            transform = transformBefore;
            clip = clipBefore;
            gapColour = gapBefore;
            arrowColour = arrowBefore;
        }

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

        private List<PathStep> aCircle(List<Value> arguments) {
            PairValue centre = pairAt(arguments, 0)
                    .orElse(PairValue.of(wide / 2, high / 2));
            double across = numberAt(arguments, 1)
                    .orElse(Math.min(centre.x(), centre.y()));
            double down = numberAt(arguments, 2).orElse(across);
            return List.of(new PathStep.EllipseAt(
                    centre.x(), centre.y(), across, down));
        }

        private List<PathStep> anEllipse(List<Value> arguments) {
            PairValue corner = pairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue across = pairAt(arguments, 1).orElse(PairValue.of(wide, high));
            return List.of(new PathStep.EllipseAt(
                    corner.x() + across.x() / 2, corner.y() + across.y() / 2,
                    across.x() / 2, across.y() / 2));
        }

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

        private List<PathStep> anArc(List<Value> arguments) {
            PairValue centre = pairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue radius = pairAt(arguments, 1).orElse(PairValue.of(wide, high));
            double begins = numberAt(arguments, 2).orElse(0.0);
            double turns = numberAt(arguments, 3).orElse(90.0);
            boolean closes = wordAt(arguments, 4).filter("closed"::equals).isPresent();
            return List.of(new PathStep.ArcTo(centre.x(), centre.y(),
                    radius.x(), radius.y(), begins, turns, closes));
        }

        private static final int THE_FIRST_TRIANGLE_COLOUR = 3;

        private void paintTheTriangle(List<Value> arguments) {
            List<Colour> corners = theThreeCornerColours(arguments);
            if (corners.size() < 3) {
                paint(aTriangle(arguments));
                return;
            }
            PairValue first = pairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue second = pairAt(arguments, 1).orElse(PairValue.of(wide, high));
            PairValue third = pairAt(arguments, 2)
                    .orElse(PairValue.of(first.x(), second.y()));
            PaintState wasStanding = state;
            List<PathStep> outline = aTriangle(arguments);
            for (GouraudMesh.Piece piece : GouraudMesh.shading(
                    new GouraudMesh.Corner(first.x(), first.y(), corners.get(0)),
                    new GouraudMesh.Corner(second.x(), second.y(), corners.get(1)),
                    new GouraudMesh.Corner(third.x(), third.y(), corners.get(2)))) {
                state = wasStanding
                        .withStroke(Optional.empty())
                        .withFillGradient(Optional.empty())
                        .withFill(Optional.of(piece.colour()));
                painted.add(new PaintInstruction.Drawn(
                        whereTheClipStandsNow().insideTheShape(outline),
                        piece.path(), transform, theStateARendererGets()));
            }
            state = wasStanding.withFill(Optional.empty())
                    .withFillGradient(Optional.empty());
            paint(aTriangle(arguments));
            state = wasStanding;
        }

        private static List<Colour> theThreeCornerColours(List<Value> arguments) {
            List<Colour> corners = new ArrayList<>();
            for (int slot = THE_FIRST_TRIANGLE_COLOUR;
                    slot < THE_FIRST_TRIANGLE_COLOUR + 3; slot++) {
                if (slot < arguments.size()
                        && arguments.get(slot) instanceof TupleValue parts) {
                    corners.add(Colour.ofTuple(parts));
                }
            }
            return corners;
        }

        private List<PathStep> aTriangle(List<Value> arguments) {
            PairValue first = pairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue second = pairAt(arguments, 1).orElse(PairValue.of(wide, high));
            PairValue third = pairAt(arguments, 2)
                    .orElse(PairValue.of(first.x(), second.y()));
            return List.of(
                    new PathStep.MoveTo(first.x(), first.y()),
                    new PathStep.LineTo(second.x(), second.y()),
                    new PathStep.LineTo(third.x(), third.y()),
                    new PathStep.Close());
        }

        private List<PathStep> aSplineThroughEveryPoint(List<Value> arguments) {
            List<PairValue> knots = everyPairIn(arguments);
            if (knots.size() < 2) {
                return List.of();
            }
            boolean closes = wordAt(arguments, 1).filter("closed"::equals).isPresent();
            return oneCubicPerSpanThrough(knots, closes);
        }

        private List<PathStep> aHandWrittenPath(List<Value> arguments) {
            return arguments.isEmpty() || !(arguments.getFirst() instanceof BlockValue steps)
                    ? List.of()
                    : ShapeSubDialect.pathFrom(steps);
        }
    }

    private static final double THE_CATMULL_ROM_SIXTH = 6.0;

    private static List<PathStep> oneCubicPerSpanThrough(
            List<PairValue> knots, boolean closes) {

        List<PathStep> path = new ArrayList<>();
        path.add(new PathStep.MoveTo(knots.getFirst().x(), knots.getFirst().y()));
        int spans = closes ? knots.size() : knots.size() - 1;
        for (int span = 0; span < spans; span++) {
            PairValue before = knotAt(knots, span - 1, closes);
            PairValue from = knotAt(knots, span, closes);
            PairValue to = knotAt(knots, span + 1, closes);
            PairValue after = knotAt(knots, span + 2, closes);
            path.add(new PathStep.CubicTo(
                    from.x() + (to.x() - before.x()) / THE_CATMULL_ROM_SIXTH,
                    from.y() + (to.y() - before.y()) / THE_CATMULL_ROM_SIXTH,
                    to.x() - (after.x() - from.x()) / THE_CATMULL_ROM_SIXTH,
                    to.y() - (after.y() - from.y()) / THE_CATMULL_ROM_SIXTH,
                    to.x(), to.y()));
        }
        if (closes) {
            path.add(new PathStep.Close());
        }
        return List.copyOf(path);
    }

    private static PairValue knotAt(List<PairValue> knots, int at, boolean closes) {
        if (closes) {
            return knots.get(Math.floorMod(at, knots.size()));
        }
        return knots.get(Math.clamp(at, 0, knots.size() - 1));
    }

    private static final int THE_DASH_COLOUR = 1;

    private static Optional<Colour> colourAt(List<Value> arguments, int slot) {
        return slot < arguments.size() && arguments.get(slot) instanceof TupleValue parts
                ? Optional.of(Colour.ofTuple(parts))
                : Optional.empty();
    }

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

    private static List<Double> everyNumberIn(List<Value> arguments) {
        return arguments.stream()
                .filter(one -> one instanceof DecimalValue || one instanceof IntegerValue)
                .map(DrawDialect::asNumber)
                .toList();
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
