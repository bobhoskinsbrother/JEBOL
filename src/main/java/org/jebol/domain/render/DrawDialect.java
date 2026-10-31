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

    /**
     * What a draw block paints on a gob of this size. Nothing is not the same
     * as empty: a block of gradients and images reads whole and paints none.
     */
    public List<PaintInstruction> readInstructionsFor(
            AnyBlockValue drawBlock, ObjectValue dialect,
            Placement where, double wide, double high) {

        Reading reading = new Reading(dialect, where, wide, high);
        reading.walk(drawBlock);
        return reading.painted();
    }

    private static final class Reading {

        private final DrawArguments read = new DrawArguments();
        private final ShapeSubDialect shapes = new ShapeSubDialect();

        private final ObjectValue dialect;
        private final Placement where;
        private final double wide;
        private final double high;
        private final List<PaintInstruction> painted = new ArrayList<>();

        private PaintState state = PaintState.AT_START;
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

        void walk(AnyBlockValue block) {
            try {
                walkCommands(block);
            } catch (Raised
                    malformedSoWhatWasPaintedStandsRatherThanTakingWindowDown) {
            }
        }

        private void walkCommands(AnyBlockValue block) {
            AnyBlockValue left = block;
            AnyBlockValue answer = BlockValue.of();
            while (Delect.read(dialect, left, answer, false, null, Context.unbound())
                    instanceof AnyBlockValue standing) {
                List<Value> read = answer.remaining();
                if (read.isEmpty()) {
                    return;
                }
                if (read.getFirst() instanceof AnyWordValue command) {
                    obey(command.canonical(), read.subList(1, read.size()));
                }
                if (standing.index() <= left.index()) {
                    return;
                }
                left = standing;
                answer = BlockValue.of();
            }
        }

        private void obey(String command, List<Value> arguments) {
            switch (command) {
                case "pen" -> state = state.withStroke(read.findColourIn(arguments));
                case "fill-pen" -> state = state.withFill(read.findColourIn(arguments))
                        .withFillGradient(Optional.empty());
                case "line-width" -> state =
                        state.withLineWidth(read.readNumberAt(arguments, 0).orElse(1.0));
                case "line-cap" -> read.readWordAt(arguments, 0).flatMap(LineCap::findCapNamed)
                        .ifPresent(cap -> state = state.withLineCap(cap));
                case "line-join" -> read.readWordAt(arguments, 0).flatMap(LineJoin::findJoinNamed)
                        .ifPresent(join -> state = state.withLineJoin(join));
                case "fill-rule" -> read.readWordAt(arguments, 0).flatMap(FillRule::findRuleNamed)
                        .ifPresent(rule -> state = state.withFillRule(rule));
                case "anti-alias" -> state = state.withAntiAliasing(
                        arguments.getFirst() instanceof LogicValue(boolean truth) && truth);
                case "line-pattern" -> {
                    state = state.withDashes(read.collectEveryNumberIn(arguments));
                    gapColour = read.readColourAt(arguments, DASH_COLOUR);
                }
                case "arrow" -> {
                    state = state.withArrowEnds(read.readPairAt(arguments, 1)
                            .map(ArrowEnds::fromFlags).orElse(ArrowEnds.NEITHER));
                    arrowColour = read.findColourIn(arguments);
                }
                case "grad-pen" -> state =
                        state.withFillGradient(readGradientFrom(arguments));
                case "gamma" -> gamma = read.readNumberAt(arguments, 0).orElse(1.0);
                case "image-filter" -> resampling = read.readWordAt(arguments, 0)
                        .flatMap(Resampling::findResamplingNamed).orElse(Resampling.BILINEAR);
                case "box" -> paint(drawBox(arguments));
                case "circle" -> paint(drawCircle(arguments));
                case "ellipse" -> paint(drawEllipse(arguments));
                case "line" -> paint(drawRunOfPoints(arguments, false));
                case "polygon" -> paint(drawRunOfPoints(arguments, true));
                case "curve" -> paint(drawCurve(arguments));
                case "arc" -> paint(drawArc(arguments));
                case "triangle" -> paintTriangle(arguments);
                case "spline" -> paint(drawSplineThroughEveryPoint(arguments));
                case "shape" -> paint(drawHandWrittenPath(arguments));
                case "image" -> showImage(arguments);
                case "text" -> writeText(arguments);
                case "clip" -> clipTo(arguments);
                case "transform" -> transformAboutCentre(arguments);
                case "invert-matrix" -> transform = transform.invert();
                case "translate" -> moveBy(arguments);
                case "scale" -> scaleBy(arguments);
                case "rotate" -> read.readNumberAt(arguments, 0).ifPresent(degrees ->
                        transform = transform.combineWith(Transform.turnBy(degrees)));
                case "skew" -> skewBy(arguments);
                case "matrix" -> applyMatrixFrom(arguments);
                case "reset-matrix" -> transform = Transform.NONE;
                case "push" -> drawAndPutEverythingBack(arguments);
                default -> {
                }
            }
        }

        private void paint(List<PathStep> path) {
            if (path.isEmpty() || state.paintsNothing()) {
                return;
            }
            paintGapsBetweenDashes(path);
            paintGradientNoToolkitHas(path);
            painted.add(new PaintInstruction.Drawn(placementWithCurrentClip(),
                    path, findTransformOnSurface(), makeStateRendererGets()));
            paintAnyArrowheads(path);
        }

        private Transform findTransformOnSurface() {
            return Transform.moveBy(where.across(), where.down()).combineWith(transform);
        }

        private PaintState makeStateRendererGets() {
            PaintState carried = state.fillGradient()
                    .filter(gradient -> !gradient.shape().canBeDrawnByToolkit())
                    .isPresent()
                    ? state.withFillGradient(Optional.empty())
                            .withFill(Optional.empty())
                    : state;
            return correctForGamma(carried);
        }

        private PaintState correctForGamma(PaintState carried) {
            if (gamma == 1) {
                return carried;
            }
            return carried
                    .withStroke(carried.strokeColour()
                            .map(colour -> colour.correctForGamma(gamma)))
                    .withFill(carried.fillColour()
                            .map(colour -> colour.correctForGamma(gamma)))
                    .withFillGradient(carried.fillGradient().map(this::correctForGamma));
        }

        private Gradient correctForGamma(Gradient gradient) {
            return new Gradient(gradient.shape(),
                    gradient.acrossOffset(), gradient.downOffset(),
                    gradient.from(), gradient.to(), gradient.angle(),
                    gradient.colours().stream()
                            .map(colour -> colour.correctForGamma(gamma)).toList(),
                    gradient.stops());
        }

        private void paintGradientNoToolkitHas(List<PathStep> path) {
            Optional<Gradient> awkward = state.fillGradient()
                    .filter(gradient -> !gradient.shape().canBeDrawnByToolkit());
            if (awkward.isEmpty()) {
                return;
            }
            PaintState wasStanding = state;
            for (WedgesAndRings.Piece piece
                    : WedgesAndRings.of(awkward.orElseThrow(), measureAreaCovered(path))) {
                state = wasStanding
                        .withStroke(Optional.empty())
                        .withFillGradient(Optional.empty())
                        .withFill(Optional.of(piece.colour()));
                painted.add(new PaintInstruction.Drawn(
                        clipToShapeItFills(path), piece.path(), findTransformOnSurface(), state));
            }
            state = wasStanding;
        }

        private Placement clipToShapeItFills(List<PathStep> path) {
            ClipRectangle area = measureAreaCoveredOnSurface(path);
            return new Placement(where.across(), where.down(),
                    where.wide(), where.high(), clip.overlapWith(area),
                    where.opacity()).fitInsideShape(path);
        }

        private ClipRectangle measureAreaCoveredOnSurface(List<PathStep> path) {
            Transform onSurface = findTransformOnSurface();
            double left = Double.MAX_VALUE;
            double top = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            double bottom = -Double.MAX_VALUE;
            for (double[] corner : findCornersIn(path)) {
                double[] placed = onSurface.mapPoint(corner[0], corner[1]);
                left = Math.min(left, placed[0]);
                top = Math.min(top, placed[1]);
                right = Math.max(right, placed[0]);
                bottom = Math.max(bottom, placed[1]);
            }
            if (left > right) {
                return clip;
            }
            return new ClipRectangle(
                    (int) Math.floor(left), (int) Math.floor(top),
                    (int) Math.ceil(right - left) + 1, (int) Math.ceil(bottom - top) + 1);
        }

        private ClipRectangle measureAreaCovered(List<PathStep> path) {
            double left = Double.MAX_VALUE;
            double top = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            double bottom = -Double.MAX_VALUE;
            for (double[] corner : findCornersIn(path)) {
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

        private List<double[]> findCornersIn(List<PathStep> path) {
            List<double[]> corners = new ArrayList<>();
            for (PathStep step : path) {
                switch (step) {
                    case PathStep.MoveTo to -> corners.add(
                            new double[] {to.across(), to.down()});
                    case PathStep.LineTo to -> corners.add(
                            new double[] {to.across(), to.down()});
                    case PathStep.QuadraticTo to -> {
                        corners.add(new double[] {to.controlAcross(), to.controlDown()});
                        corners.add(new double[] {to.across(), to.down()});
                    }
                    case PathStep.CubicTo to -> {
                        corners.add(new double[] {to.firstControlAcross(), to.firstControlDown()});
                        corners.add(new double[] {to.secondControlAcross(), to.secondControlDown()});
                        corners.add(new double[] {to.across(), to.down()});
                    }
                    case PathStep.EllipseAt round -> addBoxCorners(corners, round.centreAcross(),
                            round.centreDown(), round.radiusAcross(), round.radiusDown());
                    case PathStep.ArcTo arc -> addBoxCorners(corners, arc.centreAcross(),
                            arc.centreDown(), arc.radiusAcross(), arc.radiusDown());
                    default -> {
                    }
                }
            }
            return corners;
        }

        private static void addBoxCorners(List<double[]> corners, double centreAcross, double centreDown,
                                          double radiusAcross, double radiusDown) {
            corners.add(new double[] {centreAcross - radiusAcross, centreDown - radiusDown});
            corners.add(new double[] {centreAcross + radiusAcross, centreDown - radiusDown});
            corners.add(new double[] {centreAcross - radiusAcross, centreDown + radiusDown});
            corners.add(new double[] {centreAcross + radiusAcross, centreDown + radiusDown});
        }

        private void paintGapsBetweenDashes(List<PathStep> path) {
            if (state.dashes().isEmpty() || gapColour.isEmpty()
                    || state.strokeColour().isEmpty()) {
                return;
            }
            List<PathStep> gaps = Dashes.findGapsIn(path, state.dashes());
            if (gaps.isEmpty()) {
                return;
            }
            painted.add(new PaintInstruction.Drawn(placementWithCurrentClip(), gaps,
                    findTransformOnSurface(), state.withDashes(List.of())
                            .withFill(Optional.empty())
                            .withFillGradient(Optional.empty())
                            .withStroke(gapColour)));
        }

        private void paintAnyArrowheads(List<PathStep> path) {
            if (!state.arrowEnds().hasAnyAtAll()) {
                return;
            }
            List<PathStep> heads =
                    Arrowheads.drawArrowheads(path, state.arrowEnds(), state.lineWidth());
            if (heads.isEmpty()) {
                return;
            }
            Optional<Colour> filled =
                    arrowColour.isPresent() ? arrowColour : state.strokeColour();
            painted.add(new PaintInstruction.Drawn(placementWithCurrentClip(), heads,
                    findTransformOnSurface(), state.withDashes(List.of())
                            .withStroke(Optional.empty())
                            .withFillGradient(Optional.empty())
                            .withFill(filled)));
        }

        private static final double QUARTER_CIRCLE_HANDLE = 0.5522847498;
        private static final int GRADIENT_TYPE = 0;
        private static final int GRADIENT_OFFSET = 2;
        private static final int GRADIENT_RANGE_FROM = 4;
        private static final int GRADIENT_RANGE_TO = 5;
        private static final int GRADIENT_ANGLE = 6;
        private static final int GRADIENT_COLOURS = 9;

        private Optional<Gradient> readGradientFrom(List<Value> arguments) {
            Optional<GradientShape> shape = read.readWordAt(arguments, GRADIENT_TYPE)
                    .map(GradientShape::findShapeNamed)
                    .orElse(Optional.of(GradientShape.LINEAR));
            List<Colour> colours = collectEveryColourIn(arguments, GRADIENT_COLOURS);
            if (shape.isEmpty() || colours.size() < 2) {
                return Optional.empty();
            }
            PairValue offset = read.readPairAt(arguments, GRADIENT_OFFSET)
                    .orElse(PairValue.of(0, 0));
            double angle = read.readNumberAt(arguments, GRADIENT_ANGLE).orElse(0.0);
            return Optional.of(new Gradient(
                    shape.orElseThrow(),
                    offset.x(), offset.y(),
                    read.readNumberAt(arguments, GRADIENT_RANGE_FROM).orElse(0.0),
                    read.readNumberAt(arguments, GRADIENT_RANGE_TO).orElse(Math.max(wide, high)),
                    shape.orElseThrow() == GradientShape.DIAGONAL ? angle + 45 : angle,
                    colours, stopsFor(colours.size(), shape.orElseThrow())));
        }

        private List<Colour> collectEveryColourIn(List<Value> arguments, int slot) {
            if (slot >= arguments.size()
                    || !(arguments.get(slot) instanceof AnyBlockValue given)) {
                return List.of();
            }
            return given.remaining().stream()
                    .filter(TupleValue.class::isInstance)
                    .map(TupleValue.class::cast)
                    .map(Colour::ofTuple)
                    .toList();
        }

        private List<Double> stopsFor(int howMany, GradientShape shape) {
            List<Double> stops = new ArrayList<>();
            for (int at = 0; at < howMany; at++) {
                double evenly = (double) at / (howMany - 1);
                stops.add(shape == GradientShape.CUBIC
                        ? evenly * evenly * (3 - 2 * evenly)
                        : evenly);
            }
            return List.copyOf(stops);
        }

        private Placement placementWithCurrentClip() {
            return clip == where.clip()
                    ? where
                    : new Placement(where.across(), where.down(),
                            where.wide(), where.high(), clip, where.opacity());
        }

        private static final int IMAGE_KEY_COLOUR = 1;

        private void showImage(List<Value> arguments) {
            if (arguments.isEmpty()
                    || !(arguments.getFirst() instanceof ImageValue given)) {
                return;
            }
            ImageValue pixels = read.readColourAt(arguments, IMAGE_KEY_COLOUR)
                    .map(key -> PicturesWorkedOutHere.withThatColourSeeThrough(given, key))
                    .orElse(given);
            List<PairValue> corners = read.collectEveryPairIn(arguments);
            if (corners.size() >= 3) {
                showImagePulledOntoItsCorners(pixels, corners);
                return;
            }
            PairValue at = corners.isEmpty() ? PairValue.of(0, 0) : corners.getFirst();
            PairValue size = corners.size() >= 2
                    ? PairValue.of(corners.get(1).x() - at.x(),
                            corners.get(1).y() - at.y())
                    : pixels.size();
            addPicture(pixels, at.x(), at.y(), size.x(), size.y());
        }

        private void showImagePulledOntoItsCorners(
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
            addPicture(PicturesWorkedOutHere.pullOntoFourCorners(
                            pixels, four, boxWide, boxHigh,
                            (int) Math.round(left), (int) Math.round(top)),
                    left, top, boxWide, boxHigh);
        }

        private void addPicture(
                ImageValue pixels, double at, double down, double sizeAcross,
                double sizeDown) {

            int wideOnSurface = (int) Math.round(sizeAcross);
            int highOnSurface = (int) Math.round(sizeDown);
            ImageValue atThatSize =
                    resampling.resize(pixels, wideOnSurface, highOnSurface);
            painted.add(new PaintInstruction.Picture(
                    new Placement(
                            (int) Math.round(at),
                            (int) Math.round(down),
                            wideOnSurface, highOnSurface,
                            clip, where.opacity()),
                    atThatSize, sizeAcross, sizeDown, findTransformOnSurface()));
        }

        private static final int TEXT_OFFSET = 1;
        private static final int TEXT_BLOCK = 3;

        private void writeText(List<Value> arguments) {
            if (TEXT_BLOCK >= arguments.size()
                    || !(arguments.get(TEXT_BLOCK) instanceof AnyBlockValue written)
                    || state.strokeColour().isEmpty()) {
                return;
            }
            PairValue at = read.readPairAt(arguments, TEXT_OFFSET)
                    .orElse(PairValue.of(0, 0));
            PairValue size = read.readPairAt(arguments, TEXT_OFFSET + 1)
                    .orElse(PairValue.of(wide - at.x(), high - at.y()));
            RichText.Line line = new RichText(state.strokeColour().orElseThrow()).lineIn(written);
            if (line.runs().isEmpty()) {
                return;
            }
            boolean vectorial = isVectorial(arguments);
            double[] origin = vectorial ? new double[] {at.x(), at.y()} : transform.mapPoint(at.x(), at.y());
            painted.add(new PaintInstruction.Writing(
                    new Placement(
                            where.across() + (int) Math.round(origin[0]),
                            where.down() + (int) Math.round(origin[1]),
                            (int) Math.round(size.x()), (int) Math.round(size.y()),
                            clip, where.opacity()),
                    line.runs(), line.layout(), line.caret(),
                    vectorial ? transformAboutGobCorner() : Transform.NONE));
        }

        private static boolean isVectorial(List<Value> arguments) {
            return arguments.getFirst() instanceof AnyWordValue mode && mode.spelling().equalsIgnoreCase("vectorial");
        }

        private Transform transformAboutGobCorner() {
            return Transform.moveBy(where.across(), where.down())
                    .combineWith(transform)
                    .combineWith(Transform.moveBy(-where.across(), -where.down()));
        }

        private void clipTo(List<Value> arguments) {
            Optional<PairValue> origin = read.readPairAt(arguments, 0);
            Optional<PairValue> corner = read.readPairAt(arguments, 1);
            if (origin.isEmpty() || corner.isEmpty()) {
                clip = where.clip();
                return;
            }
            clip = where.clip().overlapWith(makeBoxBetween(origin.get(), corner.get()));
        }

        private ClipRectangle makeBoxBetween(PairValue origin, PairValue corner) {
            double[] first = transform.mapPoint(origin.x(), origin.y());
            double[] second = transform.mapPoint(corner.x(), corner.y());
            int left = (int) Math.floor(Math.min(first[0], second[0]) + 0.5);
            int top = (int) Math.floor(Math.min(first[1], second[1]) + 0.5);
            int right = (int) Math.floor(Math.max(first[0], second[0]) + 0.5);
            int bottom = (int) Math.floor(Math.max(first[1], second[1]) + 0.5);
            return new ClipRectangle(
                    where.across() + left, where.down() + top,
                    right - left, bottom - top);
        }

        private void transformAboutCentre(List<Value> arguments) {
            double angle = read.readNumberAt(arguments, 0).orElse(0.0);
            PairValue centre = read.readPairAt(arguments, 1).orElse(PairValue.of(0, 0));
            double acrossScale = read.readNumberAt(arguments, 2).orElse(1.0);
            double downScale = read.readNumberAt(arguments, 3).orElse(acrossScale);
            PairValue move = read.readPairAt(arguments, 4).orElse(PairValue.of(0, 0));
            transform = transform
                    .combineWith(Transform.moveBy(move.x(), move.y()))
                    .combineWith(Transform.moveBy(centre.x(), centre.y()))
                    .combineWith(Transform.turnBy(angle))
                    .combineWith(Transform.scaleBy(acrossScale, downScale))
                    .combineWith(Transform.moveBy(-centre.x(), -centre.y()));
        }

        private void moveBy(List<Value> arguments) {
            read.readPairAt(arguments, 0).ifPresent(to -> transform = transform.combineWith(
                    Transform.moveBy(to.x(), to.y())));
        }

        private void scaleBy(List<Value> arguments) {
            double across = read.readNumberAt(arguments, 0).orElse(1.0);
            double down = read.readNumberAt(arguments, 1).orElse(across);
            transform = transform.combineWith(Transform.scaleBy(across, down));
        }

        private void skewBy(List<Value> arguments) {
            double across = read.readNumberAt(arguments, 0).orElse(0.0);
            transform = transform.combineWith(Transform.skewBy(across, 0));
        }

        private void applyMatrixFrom(List<Value> arguments) {
            if (arguments.isEmpty()
                    || !(arguments.getFirst() instanceof AnyBlockValue six)) {
                return;
            }
            List<Value> numbers = six.remaining();
            if (numbers.size() < 6) {
                return;
            }
            transform = transform.combineWith(new Transform(
                    read.asNumber(numbers.get(0)), read.asNumber(numbers.get(1)),
                    read.asNumber(numbers.get(2)), read.asNumber(numbers.get(3)),
                    read.asNumber(numbers.get(4)), read.asNumber(numbers.get(5))));
        }

        private void drawAndPutEverythingBack(List<Value> arguments) {
            if (arguments.isEmpty()
                    || !(arguments.getFirst() instanceof AnyBlockValue inside)) {
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

        private List<PathStep> drawBox(List<Value> arguments) {
            PairValue corner = read.readPairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue end = read.readPairAt(arguments, 1).orElse(PairValue.of(wide, high));
            double left = Math.min(corner.x(), end.x());
            double top = Math.min(corner.y(), end.y());
            double right = Math.max(corner.x(), end.x());
            double bottom = Math.max(corner.y(), end.y());
            double radius = Math.min(read.readNumberAt(arguments, 2).orElse(0.0),
                    Math.min(right - left, bottom - top) / 2);
            return radius > 0
                    ? drawBoxWithRoundedCorners(left, top, right, bottom, radius)
                    : List.of(
                            new PathStep.MoveTo(left, top),
                            new PathStep.LineTo(right, top),
                            new PathStep.LineTo(right, bottom),
                            new PathStep.LineTo(left, bottom),
                            new PathStep.Close());
        }

        private List<PathStep> drawBoxWithRoundedCorners(
                double left, double top, double right, double bottom, double radius) {

            double pull = radius * QUARTER_CIRCLE_HANDLE;
            return List.of(
                    new PathStep.MoveTo(left + radius, top),
                    new PathStep.LineTo(right - radius, top),
                    new PathStep.CubicTo(right - radius + pull, top, right, top + radius - pull, right, top + radius),
                    new PathStep.LineTo(right, bottom - radius),
                    new PathStep.CubicTo(right, bottom - radius + pull, right - radius + pull, bottom,
                            right - radius, bottom),
                    new PathStep.LineTo(left + radius, bottom),
                    new PathStep.CubicTo(left + radius - pull, bottom, left, bottom - radius + pull,
                            left, bottom - radius),
                    new PathStep.LineTo(left, top + radius),
                    new PathStep.CubicTo(left, top + radius - pull, left + radius - pull, top, left + radius, top),
                    new PathStep.Close());
        }

        private List<PathStep> drawCircle(List<Value> arguments) {
            PairValue centre = read.readPairAt(arguments, 0)
                    .orElse(PairValue.of(wide / 2, high / 2));
            double across = read.readNumberAt(arguments, 1)
                    .orElse(Math.min(centre.x(), centre.y()));
            double down = read.readNumberAt(arguments, 2).orElse(across);
            return List.of(new PathStep.EllipseAt(
                    centre.x(), centre.y(), across, down));
        }

        private List<PathStep> drawEllipse(List<Value> arguments) {
            PairValue corner = read.readPairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue across = read.readPairAt(arguments, 1).orElse(PairValue.of(wide, high));
            return List.of(new PathStep.EllipseAt(
                    corner.x() + across.x() / 2, corner.y() + across.y() / 2,
                    across.x() / 2, across.y() / 2));
        }

        private List<PathStep> drawRunOfPoints(List<Value> arguments, boolean closes) {
            List<PairValue> points = read.collectEveryPairIn(arguments);
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

        private List<PathStep> drawCurve(List<Value> arguments) {
            List<PairValue> points = read.collectEveryPairIn(arguments);
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

        private List<PathStep> drawArc(List<Value> arguments) {
            PairValue centre = read.readPairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue radius = read.readPairAt(arguments, 1).orElse(PairValue.of(wide, high));
            double begins = read.readNumberAt(arguments, 2).orElse(0.0);
            double turns = read.readNumberAt(arguments, 3).orElse(90.0);
            boolean closes = read.readWordAt(arguments, 4).filter("closed"::equals).isPresent();
            return List.of(new PathStep.ArcTo(centre.x(), centre.y(),
                    radius.x(), radius.y(), begins, turns, closes));
        }

        private static final int FIRST_TRIANGLE_COLOUR = 3;

        private void paintTriangle(List<Value> arguments) {
            List<Colour> corners = readThreeCornerColours(arguments);
            if (corners.size() < 3) {
                paint(drawTriangle(arguments));
                return;
            }
            PairValue first = read.readPairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue second = read.readPairAt(arguments, 1).orElse(PairValue.of(wide, high));
            PairValue third = read.readPairAt(arguments, 2)
                    .orElse(PairValue.of(first.x(), second.y()));
            PaintState wasStanding = state;
            List<PathStep> outline = drawTriangle(arguments);
            for (GouraudMesh.Piece piece : GouraudMesh.shade(
                    new GouraudMesh.Corner(first.x(), first.y(), corners.get(0)),
                    new GouraudMesh.Corner(second.x(), second.y(), corners.get(1)),
                    new GouraudMesh.Corner(third.x(), third.y(), corners.get(2)))) {
                state = wasStanding
                        .withStroke(Optional.empty())
                        .withFillGradient(Optional.empty())
                        .withFill(Optional.of(piece.colour()));
                painted.add(new PaintInstruction.Drawn(
                        placementWithCurrentClip().fitInsideShape(outline),
                        piece.path(), findTransformOnSurface(), makeStateRendererGets()));
            }
            state = wasStanding.withFill(Optional.empty())
                    .withFillGradient(Optional.empty());
            paint(drawTriangle(arguments));
            state = wasStanding;
        }

        private List<Colour> readThreeCornerColours(List<Value> arguments) {
            List<Colour> corners = new ArrayList<>();
            for (int slot = FIRST_TRIANGLE_COLOUR;
                 slot < FIRST_TRIANGLE_COLOUR + 3; slot++) {
                if (slot < arguments.size()
                        && arguments.get(slot) instanceof TupleValue parts) {
                    corners.add(Colour.ofTuple(parts));
                }
            }
            return corners;
        }

        private List<PathStep> drawTriangle(List<Value> arguments) {
            PairValue first = read.readPairAt(arguments, 0).orElse(PairValue.of(0, 0));
            PairValue second = read.readPairAt(arguments, 1).orElse(PairValue.of(wide, high));
            PairValue third = read.readPairAt(arguments, 2)
                    .orElse(PairValue.of(first.x(), second.y()));
            return List.of(
                    new PathStep.MoveTo(first.x(), first.y()),
                    new PathStep.LineTo(second.x(), second.y()),
                    new PathStep.LineTo(third.x(), third.y()),
                    new PathStep.Close());
        }

        private List<PathStep> drawSplineThroughEveryPoint(List<Value> arguments) {
            List<PairValue> knots = read.collectEveryPairIn(arguments);
            if (knots.size() < 2) {
                return List.of();
            }
            boolean closes = read.readWordAt(arguments, 1).filter("closed"::equals).isPresent();
            return fitOneCubicPerSpanThrough(knots, closes);
        }

        private List<PathStep> drawHandWrittenPath(List<Value> arguments) {
            return arguments.isEmpty() || !(arguments.getFirst() instanceof AnyBlockValue steps)
                    ? List.of()
                    : shapes.readPathFrom(steps);
        }

        private List<PathStep> fitOneCubicPerSpanThrough(List<PairValue> knots, boolean closes) {
            List<PathStep> path = new ArrayList<>();
            path.add(new PathStep.MoveTo(knots.getFirst().x(), knots.getFirst().y()));
            int spans = closes ? knots.size() : knots.size() - 1;
            for (int span = 0; span < spans; span++) {
                PairValue before = readKnotAt(knots, span - 1, closes);
                PairValue from = readKnotAt(knots, span, closes);
                PairValue to = readKnotAt(knots, span + 1, closes);
                PairValue after = readKnotAt(knots, span + 2, closes);
                path.add(new PathStep.CubicTo(
                        from.x() + (to.x() - before.x()) / CATMULL_ROM_SIXTH,
                        from.y() + (to.y() - before.y()) / CATMULL_ROM_SIXTH,
                        to.x() - (after.x() - from.x()) / CATMULL_ROM_SIXTH,
                        to.y() - (after.y() - from.y()) / CATMULL_ROM_SIXTH,
                        to.x(), to.y()));
            }
            if (closes) {
                path.add(new PathStep.Close());
            }
            return List.copyOf(path);
        }

        private PairValue readKnotAt(List<PairValue> knots, int at, boolean closes) {
            if (closes) {
                return knots.get(Math.floorMod(at, knots.size()));
            }
            return knots.get(Math.clamp(at, 0, knots.size() - 1));
        }
    }

    private static final double CATMULL_ROM_SIXTH = 6.0;

    private static final int DASH_COLOUR = 1;
}
