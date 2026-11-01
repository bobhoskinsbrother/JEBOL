package org.jebol.application;

import org.jebol.domain.eval.blend2d.Blend2DCanvas;
import org.jebol.domain.eval.blend2d.Blend2DFont;
import org.jebol.domain.eval.blend2d.OpenTypeFace;
import org.jebol.domain.eval.blend2d.Blend2DGradient;
import org.jebol.domain.eval.blend2d.Blend2DImage;
import org.jebol.domain.eval.blend2d.Blend2DPathBuilder;
import org.jebol.domain.eval.blend2d.Blend2DPattern;
import org.jebol.domain.eval.blend2d.Blend2DShape;
import org.jebol.domain.eval.blend2d.CompositingOperator;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.GetPathValue;
import org.jebol.domain.value.GetWordValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LitWordValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.PathValue;
import org.jebol.domain.value.PercentValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorKind;
import org.jebol.domain.value.VectorValue;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class Blend2DDrawing {

    static final List<String> COMMAND_WORDS = List.of(
            "move", "line", "curve", "curv", "cubic", "quad", "hline", "vline", "qcurve", "qcurv",
            "polygon", "shape", "box", "circle", "ellipse", "arc", "image", "text", "fill-all", "clear",
            "clear-all", "point", "point-size", "triangle", "pen", "fill", "line-width", "line-cap",
            "line-join", "alpha", "blend", "composite", "reset-matrix", "rotate", "scale", "translate",
            "close", "clip", "font", "fill-pen");

    static final List<String> ARGUMENT_WORDS = List.of(
            "pad", "tile", "flip", "tile-y", "flip-y", "tile-x", "tile-x-flip-y", "flip-x", "flip-x-tile-y",
            "linear", "radial", "conical",
            "source-over", "source-copy", "source-in", "source-out", "source-atop", "destination-over",
            "destination-copy", "destination-in", "destination-out", "destination-atop", "xor", "clear",
            "plus", "minus", "modulate", "multiply", "screen", "overlay", "darken", "lighten", "color-dodge",
            "color-burn", "linear-burn", "linear-light", "pin-light", "hard-light", "soft-light", "difference",
            "exclusion", "miter", "bevel", "round", "pie", "closed", "chord", "sweep", "large");

    private static final int ARGUMENT_PAD = 1;
    private static final int ARGUMENT_LINEAR = 10;
    private static final int ARGUMENT_SOURCE_OVER = 13;
    private static final int ARGUMENT_MITER = 42;
    private static final int ARGUMENT_BEVEL = 43;
    private static final int ARGUMENT_PIE = 45;
    private static final int ARGUMENT_CHORD = 47;
    private static final int ARGUMENT_SWEEP = 48;
    private static final int ARGUMENT_LARGE = 49;

    private static final int EXTEND_MODE_LARGEST = 8;
    private static final int EXTEND_MODE_REPEAT = 1;
    private static final long GRADIENT_LINEAR = 0;
    private static final long GRADIENT_RADIAL = 1;
    private static final int GRADIENT_TYPE_LARGEST = 2;
    private static final int COMPOSITING_OPERATOR_LARGEST = 28;
    private static final int STROKE_JOIN_WORD_LARGEST = 3;
    private static final int JOIN_MITER_CLIP = 0;
    private static final int JOIN_BEVEL = 3;
    private static final int JOIN_ROUND = 4;
    private static final int STROKE_JOIN_MITER_WORD_LARGEST = 2;
    private static final long STROKE_CAP_LARGEST = 5;

    private static final int WHERE_AN_UNSET_COMMAND_POSITION_LANDS = 3;

    private static final double DEGREES_TO_RADIANS = 3.14159265358979323846264338327950288 / 180.0;

    private static final double DEFAULT_POINT_RADIUS = 1.0;

    private static final double DEFAULT_FONT_SIZE = 10.0;

    private static final int LARGEST_BYTE = 0xFF;

    private enum Kind {
        END, UNSET, NONE, HANDLE, LOGIC, INTEGER, DECIMAL, PERCENT, PAIR, TUPLE, WORD, LIT_WORD,
        STRING, FILE, BLOCK, VECTOR, IMAGE, OTHER
    }

    private record Read(Kind kind, Value value) {
    }

    private final Command call;
    private final Blend2DCanvas canvas;
    private final List<Value> series;
    private int index;
    private int commandPosition = WHERE_AN_UNSET_COMMAND_POSITION_LANDS;
    private int command;
    private boolean hasFill;
    private boolean hasStroke;
    private double pointRadius = DEFAULT_POINT_RADIUS;

    private Blend2DDrawing(Command call, Blend2DCanvas canvas, AnyBlockValue commands) {
        this.call = call;
        this.canvas = canvas;
        this.series = commands.head().remaining();
        this.index = commands.index() - 1;
    }

    static void draw(Command call, Blend2DCanvas canvas, AnyBlockValue commands) {
        new Blend2DDrawing(call, canvas, commands).walk();
    }

    static Blend2DShape buildPath(Command call, AnyBlockValue commands) {
        Blend2DDrawing dialect = new Blend2DDrawing(call, null, commands);
        dialect.commandPosition = 0;
        dialect.walkPath();
        return dialect.pathBuilder.build();
    }

    private final Blend2DPathBuilder pathBuilder = new Blend2DPathBuilder();

    private void walkPath() {
        while (index < series.size()) {
            boolean isRead = fetchWord(index++, COMMAND_WORDS) && runPathCommand();
            while (!isRead) {
                index = commandPosition;
                boolean isFound = false;
                while (index < series.size() && !isFound) {
                    isFound = fetchWord(index++, COMMAND_WORDS);
                }
                if (!isFound) {
                    return;
                }
                isRead = runPathCommand();
            }
        }
    }

    private boolean runPathCommand() {
        commandPosition = index;
        return switch (COMMAND_WORDS.get(command - 1)) {
            case "move" -> {
                double[] to = resolvePair();
                if (to != null) {
                    pathBuilder.moveTo(to[0], to[1]);
                }
                yield to != null;
            }
            case "line" -> {
                double[] to = resolvePair();
                if (to == null) {
                    yield false;
                }
                pathBuilder.lineTo(to[0], to[1]);
                while (readRaw(index).kind() == Kind.PAIR) {
                    double[] next = readPair(readRaw(index++));
                    pathBuilder.lineTo(next[0], next[1]);
                }
                yield true;
            }
            case "arc" -> addEllipticArc();
            case "curve" -> {
                while (arePairsAhead(3)) {
                    double[] first = readPair(readRaw(index));
                    double[] second = readPair(readRaw(index + 1));
                    double[] end = readPair(readRaw(index + 2));
                    index += 3;
                    pathBuilder.cubicTo(first[0], first[1], second[0], second[1], end[0], end[1]);
                }
                yield true;
            }
            case "curv" -> {
                while (arePairsAhead(2)) {
                    double[] second = readPair(readRaw(index));
                    double[] end = readPair(readRaw(index + 1));
                    index += 2;
                    pathBuilder.smoothCubicTo(second[0], second[1], end[0], end[1]);
                }
                yield true;
            }
            case "qcurve" -> {
                while (arePairsAhead(2)) {
                    double[] control = readPair(readRaw(index));
                    double[] end = readPair(readRaw(index + 1));
                    index += 2;
                    pathBuilder.quadTo(control[0], control[1], end[0], end[1]);
                }
                yield true;
            }
            case "qcurv" -> {
                while (arePairsAhead(1)) {
                    double[] end = readPair(readRaw(index++));
                    pathBuilder.smoothQuadTo(end[0], end[1]);
                }
                yield true;
            }
            case "hline" -> {
                Double x = readNumber();
                if (x != null) {
                    pathBuilder.lineTo(x, pathBuilder.lastY());
                }
                yield x != null;
            }
            case "vline" -> {
                Double y = readNumber();
                if (y != null) {
                    pathBuilder.lineTo(pathBuilder.lastX(), y);
                }
                yield y != null;
            }
            case "close" -> {
                pathBuilder.close();
                yield true;
            }
            default -> false;
        };
    }

    private boolean arePairsAhead(int count) {
        for (int at = 0; at < count; at++) {
            if (readRaw(index + at).kind() != Kind.PAIR) {
                return false;
            }
        }
        return true;
    }

    private boolean addEllipticArc() {
        double[] end = resolvePair();
        if (end == null) {
            return false;
        }
        Double radiusX = readNumber();
        if (radiusX == null) {
            return false;
        }
        Double radiusY = readNumber();
        if (radiusY == null) {
            return false;
        }
        Double rotation = readNumber();
        if (rotation == null) {
            return false;
        }
        boolean isSweep = takeArgumentWord(ARGUMENT_SWEEP);
        boolean isLarge = takeArgumentWord(ARGUMENT_LARGE);
        pathBuilder.ellipticArcTo(radiusX, radiusY, rotation * DEGREES_TO_RADIANS, isLarge, isSweep, end[0], end[1]);
        return true;
    }

    private boolean takeArgumentWord(int wanted) {
        if (fetchWord(index, ARGUMENT_WORDS) && command == wanted) {
            index++;
            return true;
        }
        return false;
    }

    private void walk() {
        while (!endedEarly && index < series.size()) {
            boolean isRead = fetchWord(index++, COMMAND_WORDS) && runCommand();
            while (!isRead && !endedEarly) {
                index = commandPosition;
                boolean isFound = false;
                while (index < series.size() && !isFound) {
                    isFound = fetchWord(index++, COMMAND_WORDS);
                }
                if (!isFound) {
                    return;
                }
                isRead = runCommand();
            }
        }
    }

    private boolean endedEarly;

    private Optional<Blend2DFont> font = Optional.empty();

    private Optional<OpenTypeFace> loadedFace = Optional.empty();

    private Optional<OpenTypeFace> handleFace = Optional.empty();

    private double fontSize = DEFAULT_FONT_SIZE;

    private boolean runCommand() {
        commandPosition = index;
        String name = COMMAND_WORDS.get(command - 1);
        return switch (name) {
            case "fill", "fill-pen" -> setFill();
            case "pen" -> setPen();
            case "line" -> drawLine();
            case "line-width" -> setLineWidth();
            case "line-join" -> setLineJoin();
            case "line-cap" -> setLineCap();
            case "cubic" -> drawCubic();
            case "polygon" -> drawPolygon();
            case "box" -> drawBox();
            case "circle" -> drawCircle();
            case "ellipse" -> drawEllipse();
            case "point" -> drawPoints();
            case "point-size" -> setPointSize();
            case "triangle" -> drawTriangles();
            case "arc" -> drawArc();
            case "image" -> drawImage();
            case "font" -> chooseFont();
            case "text" -> writeText();
            case "scale" -> scale();
            case "rotate" -> rotate();
            case "translate" -> translate();
            case "reset-matrix" -> {
                canvas.resetTransform();
                yield true;
            }
            case "alpha" -> setAlpha();
            case "blend", "composite" -> setBlend();
            case "fill-all" -> {
                canvas.fillAll();
                yield true;
            }
            case "clear-all" -> {
                canvas.clearAll();
                yield true;
            }
            case "clear" -> clearBox();
            case "clip" -> clip();
            case "shape" -> drawShape();
            default -> false;
        };
    }

    private Read readRaw(int at) {
        if (at < 0 || at >= series.size()) {
            return new Read(Kind.END, NoneValue.none());
        }
        Value value = series.get(at);
        return new Read(classify(value), value);
    }

    private Read resolveNext() {
        Read raw = readRaw(index++);
        return resolve(raw);
    }

    private Read resolveAt(int at) {
        return resolve(readRaw(at));
    }

    private Read resolve(Read raw) {
        Value value = raw.value();
        if (raw.kind() == Kind.END) {
            return raw;
        }
        if (value instanceof WordValue || value instanceof GetWordValue) {
            Value held = ((AnyWordValue) value).findBoundSlot().value();
            return new Read(classify(held), held);
        }
        if (value instanceof PathValue || value instanceof GetPathValue) {
            Value held = call.evaluate(BlockValue.of(value));
            return new Read(classify(held), held);
        }
        return raw;
    }

    private static Kind classify(Value value) {
        return switch (value) {
            case UnsetValue ignored -> Kind.UNSET;
            case NoneValue ignored -> Kind.NONE;
            case HandleValue ignored -> Kind.HANDLE;
            case LogicValue ignored -> Kind.LOGIC;
            case IntegerValue ignored -> Kind.INTEGER;
            case PercentValue ignored -> Kind.PERCENT;
            case DecimalValue ignored -> Kind.DECIMAL;
            case PairValue ignored -> Kind.PAIR;
            case TupleValue ignored -> Kind.TUPLE;
            case LitWordValue ignored -> Kind.LIT_WORD;
            case WordValue ignored -> Kind.WORD;
            case FileValue ignored -> Kind.FILE;
            case StringValue ignored -> Kind.STRING;
            case BlockValue ignored -> Kind.BLOCK;
            case VectorValue ignored -> Kind.VECTOR;
            case ImageValue ignored -> Kind.IMAGE;
            default -> Kind.OTHER;
        };
    }

    private boolean fetchWord(int at, List<String> words) {
        Read raw = readRaw(at);
        if (raw.kind() != Kind.WORD) {
            return false;
        }
        command = findWord(words, (AnyWordValue) raw.value());
        return command != 0;
    }

    private static int findWord(List<String> words, AnyWordValue word) {
        return words.indexOf(word.spelling().toLowerCase(Locale.ROOT)) + 1;
    }

    private long fetchMode(int at, int start, int largest) {
        Read raw = readRaw(at);
        long word = largest;
        if (raw.kind() == Kind.WORD || raw.kind() == Kind.LIT_WORD) {
            word = (findWord(ARGUMENT_WORDS, (AnyWordValue) raw.value()) - start) & 0xFFFFFFFFL;
        } else if (raw.kind() == Kind.INTEGER) {
            word = ((IntegerValue) raw.value()).magnitude() & 0xFFFFFFFFL;
        }
        return word <= largest ? word : -1;
    }

    private static double asFloat(double coordinate) {
        return (float) coordinate;
    }

    private double[] readPair(Read read) {
        PairValue pair = (PairValue) read.value();
        return new double[] {asFloat(pair.x()), asFloat(pair.y())};
    }

    private double[] resolvePair() {
        Read read = resolveNext();
        return read.kind() == Kind.PAIR ? readPair(read) : null;
    }

    private double[] resolvePairOptionally() {
        Read read = resolveNext();
        if (read.kind() == Kind.PAIR) {
            return readPair(read);
        }
        index--;
        return null;
    }

    private Double readNumber() {
        return toNumber(resolveNext());
    }

    private static Double toNumber(Read read) {
        return switch (read.kind()) {
            case DECIMAL, PERCENT -> ((org.jebol.domain.value.AnyDecimalValue) read.value()).quantity();
            case INTEGER -> (double) ((IntegerValue) read.value()).magnitude();
            default -> null;
        };
    }

    private Double readOptionalNumber() {
        Double number = readNumber();
        if (number == null) {
            index--;
        }
        return number;
    }

    private static int toColour(TupleValue tuple) {
        int alpha = tuple.segmentCount() == 3 ? 0xFF : tuple.readOctetAt(4);
        return alpha << 24 | tuple.readOctetAt(1) << 16 | tuple.readOctetAt(2) << 8 | tuple.readOctetAt(3);
    }

    private boolean setFill() {
        Read read = resolveNext();
        switch (read.kind()) {
            case TUPLE -> {
                canvas.setFillColour(toColour((TupleValue) read.value()));
                hasFill = true;
                return true;
            }
            case LOGIC -> {
                if (!read.value().isTruthy()) {
                    hasFill = false;
                }
                return true;
            }
            case HANDLE -> {
                if (!Blend2DExtension.isImageHandle(read.value())) {
                    return false;
                }
                return usePattern(Blend2DExtension.readImage(
                        call.unwrap(read.value(), Blend2DExtension.ImageHandle.class)));
            }
            case IMAGE -> {
                if (((ImageValue) read.value()).storage().wide() == 0 || ((ImageValue) read.value()).storage().high() == 0) {
                    return false;
                }
                return usePattern(Blend2DExtension.readImage((ImageValue) read.value()));
            }
            case UNSET -> {
                long mode = fetchMode(index - 1, ARGUMENT_LINEAR, GRADIENT_TYPE_LARGEST);
                return mode >= 0 && useGradient(mode);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean usePattern(Blend2DImage image) {
        canvas.setFillPaint(new Blend2DPattern(image, (int) readExtendMode()));
        hasFill = true;
        return true;
    }

    private long readExtendMode() {
        long mode = fetchMode(index, ARGUMENT_PAD, EXTEND_MODE_LARGEST);
        if (mode >= 0) {
            index++;
            return mode;
        }
        return EXTEND_MODE_REPEAT;
    }

    private boolean useGradient(long mode) {
        List<double[]> stops = new ArrayList<>();
        Read read = resolveNext();
        while (read.kind() == Kind.TUPLE) {
            Double offset = readNumber();
            if (offset == null) {
                return false;
            }
            stops.add(new double[] {offset, toColour((TupleValue) read.value())});
            read = resolveNext();
        }
        index--;
        double[] start = resolvePair();
        if (start == null) {
            return false;
        }
        Blend2DGradient gradient;
        if (mode == GRADIENT_RADIAL) {
            double[] focus = resolvePair();
            if (focus == null) {
                return false;
            }
            Double radius = readNumber();
            if (radius == null) {
                return false;
            }
            gradient = Blend2DGradient.radial(start[0], start[1], focus[0], focus[1], radius);
        } else if (mode == GRADIENT_LINEAR) {
            double[] end = resolvePair();
            if (end == null) {
                return false;
            }
            gradient = Blend2DGradient.linear(start[0], start[1], end[0], end[1]);
        } else {
            Double angle = readOptionalNumber();
            Double repeat = readOptionalNumber();
            gradient = Blend2DGradient.conic(start[0], start[1], angle == null ? 0 : angle, repeat == null ? 1 : repeat);
            gradient.setExtend(Blend2DGradient.Extend.REPEAT);
        }
        for (double[] stop : stops) {
            gradient.addStop(stop[0], (int) stop[1]);
        }
        canvas.setFillPaint(gradient);
        hasFill = true;
        return true;
    }

    private boolean setPen() {
        Read read = resolveNext();
        switch (read.kind()) {
            case TUPLE -> {
                canvas.setStrokeColour(toColour((TupleValue) read.value()));
                hasStroke = true;
                return true;
            }
            case LOGIC -> {
                if (!read.value().isTruthy()) {
                    hasStroke = false;
                }
                return true;
            }
            case IMAGE -> {
                if (((ImageValue) read.value()).storage().wide() == 0 || ((ImageValue) read.value()).storage().high() == 0) {
                    return false;
                }
                canvas.setFillPaint(new Blend2DPattern(Blend2DExtension.readImage((ImageValue) read.value()),
                        (int) readExtendMode()));
                hasStroke = true;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean drawLine() {
        if (!hasStroke) {
            return true;
        }
        Read read = resolveNext();
        List<double[]> figures = new ArrayList<>();
        switch (read.kind()) {
            case PAIR -> {
                List<double[]> points = new ArrayList<>();
                points.add(readPair(read));
                while (readRaw(index).kind() == Kind.PAIR) {
                    points.add(readPair(readRaw(index)));
                    index++;
                }
                figures.add(flatten(points));
            }
            case BLOCK -> {
                List<Value> inside = ((AnyBlockValue) read.value()).remaining();
                if (inside.isEmpty() || classify(inside.getFirst()) != Kind.PAIR) {
                    return false;
                }
                List<double[]> points = new ArrayList<>();
                for (Value value : inside) {
                    if (classify(value) != Kind.PAIR) {
                        break;
                    }
                    points.add(readPair(new Read(Kind.PAIR, value)));
                }
                figures.add(flatten(points));
            }
            case VECTOR -> {
                VectorValue points = (VectorValue) read.value();
                if (points.kind() != VectorKind.FLOAT64) {
                    return false;
                }
                Read edges = resolveNext();
                if (edges.kind() != Kind.VECTOR || ((VectorValue) edges.value()).kind() != VectorKind.INT32) {
                    return false;
                }
                figures.addAll(joinEdges(points, (VectorValue) edges.value()));
            }
            default -> {
                return false;
            }
        }
        canvas.stroke(Blend2DShape.polylines(figures));
        return true;
    }

    private static List<double[]> joinEdges(VectorValue points, VectorValue edges) {
        long pointCount = points.storageLength() - 1L;
        List<double[]> figures = new ArrayList<>();
        List<Double> figure = new ArrayList<>();
        long previous = 0;
        for (int at = 0; at < edges.storageLength() - 1; at += 2) {
            long from = Integer.toUnsignedLong((int) edges.storage().at(at + 1) << 1);
            long to = Integer.toUnsignedLong((int) edges.storage().at(at + 2) << 1);
            if (from >= pointCount || to >= pointCount) {
                continue;
            }
            if (at == 0 || previous != from) {
                if (!figure.isEmpty()) {
                    figures.add(unbox(figure));
                }
                figure = new ArrayList<>();
            } else if (figure.isEmpty()) {
                previous = to;
                continue;
            }
            addVectorPoint(figure, points, from);
            addVectorPoint(figure, points, to);
            previous = to;
        }
        if (!figure.isEmpty()) {
            figures.add(unbox(figure));
        }
        return figures;
    }

    private static void addVectorPoint(List<Double> figure, VectorValue points, long at) {
        figure.add(Double.longBitsToDouble(points.storage().at((int) at + 1)));
        figure.add(Double.longBitsToDouble(points.storage().at((int) at + 2)));
    }

    private static double[] unbox(List<Double> figure) {
        double[] points = new double[figure.size()];
        for (int at = 0; at < points.length; at++) {
            points[at] = figure.get(at);
        }
        return points;
    }

    private boolean setLineWidth() {
        Double width = readNumber();
        if (width == null) {
            return false;
        }
        hasStroke = width != 0;
        if (hasStroke) {
            canvas.setStrokeWidth(width);
        }
        return true;
    }

    private boolean setLineJoin() {
        long mode = fetchMode(index, ARGUMENT_MITER, STROKE_JOIN_WORD_LARGEST);
        if (mode < 0) {
            return false;
        }
        index++;
        int join;
        if (mode == 0) {
            long variant = fetchMode(index, ARGUMENT_BEVEL, STROKE_JOIN_MITER_WORD_LARGEST);
            if (variant >= 0) {
                index++;
                join = (int) variant + 1;
            } else {
                join = JOIN_MITER_CLIP;
            }
        } else {
            join = mode == 1 ? JOIN_BEVEL : JOIN_ROUND;
        }
        canvas.setStrokeJoin(join);
        return true;
    }

    private boolean setLineCap() {
        Read start = resolveNext();
        if (start.kind() != Kind.INTEGER || Long.compareUnsigned(((IntegerValue) start.value()).magnitude(), STROKE_CAP_LARGEST) > 0) {
            return false;
        }
        int startCap = (int) ((IntegerValue) start.value()).magnitude();
        Read end = resolveNext();
        if (end.kind() != Kind.INTEGER) {
            index--;
            canvas.setStrokeCaps(startCap, startCap);
            return true;
        }
        canvas.setStrokeStartCap(startCap);
        if (Long.compareUnsigned(((IntegerValue) end.value()).magnitude(), STROKE_CAP_LARGEST) > 0) {
            return false;
        }
        canvas.setStrokeEndCap((int) ((IntegerValue) end.value()).magnitude());
        return true;
    }

    private boolean drawCubic() {
        Read start = readRaw(index++);
        if (start.kind() != Kind.PAIR) {
            return false;
        }
        List<double[]> points = new ArrayList<>();
        points.add(readPair(start));
        while (readRaw(index).kind() == Kind.PAIR && readRaw(index + 1).kind() == Kind.PAIR
                && readRaw(index + 2).kind() == Kind.PAIR) {
            for (int at = 0; at < 3; at++) {
                points.add(readPair(readRaw(index + at)));
            }
            index += 3;
        }
        fillAndStroke(Blend2DShape.cubics(flatten(points)));
        return true;
    }

    private void fillAndStroke(Blend2DShape shape) {
        if (hasFill) {
            canvas.fill(shape);
        }
        if (hasStroke) {
            canvas.stroke(shape);
        }
    }

    private boolean drawPolygon() {
        double[] first = resolvePair();
        if (first == null) {
            return false;
        }
        List<double[]> corners = new ArrayList<>();
        corners.add(first);
        while (readRaw(index).kind() == Kind.PAIR) {
            corners.add(readPair(readRaw(index)));
            index++;
        }
        fillAndStroke(Blend2DShape.polygon(flatten(corners)));
        return true;
    }

    private static double[] flatten(List<double[]> corners) {
        double[] points = new double[corners.size() * 2];
        for (int at = 0; at < corners.size(); at++) {
            points[2 * at] = corners.get(at)[0];
            points[2 * at + 1] = corners.get(at)[1];
        }
        return points;
    }

    private boolean drawBox() {
        double[] corner = resolvePair();
        if (corner == null) {
            return false;
        }
        double[] size = resolvePair();
        if (size == null) {
            return false;
        }
        Read radius = resolveAt(index);
        if (radius.kind() == Kind.DECIMAL || radius.kind() == Kind.INTEGER) {
            index++;
            double radiusX = toNumber(radius);
            double radiusY = radiusX;
            Read second = resolveAt(index);
            if (second.kind() == Kind.DECIMAL || second.kind() == Kind.INTEGER) {
                index++;
                radiusY = toNumber(second);
            }
            fillAndStroke(Blend2DShape.roundedBox(corner[0], corner[1], size[0], size[1], radiusX, radiusY));
            return true;
        }
        if (hasFill) {
            canvas.fillRect(corner[0], corner[1], size[0], size[1]);
        }
        if (hasStroke) {
            canvas.stroke(Blend2DShape.box(corner[0], corner[1], size[0], size[1]));
        }
        return true;
    }

    private boolean drawCircle() {
        double[] centre = resolvePair();
        if (centre == null) {
            return false;
        }
        Double radius = readNumber();
        if (radius == null) {
            return false;
        }
        Double radiusY = readOptionalNumber();
        fillAndStroke(Blend2DShape.ellipse(centre[0], centre[1], radius, radiusY == null ? Math.abs(radius) : radiusY));
        return true;
    }

    private boolean drawEllipse() {
        double[] corner = resolvePair();
        if (corner == null) {
            return false;
        }
        double[] size = resolvePair();
        if (size == null) {
            return false;
        }
        double radiusX = size[0] * 0.5;
        double radiusY = size[1] * 0.5;
        fillAndStroke(Blend2DShape.ellipse(corner[0] + radiusX, corner[1] + radiusY, radiusX, radiusY));
        return true;
    }

    private boolean setPointSize() {
        Double size = readNumber();
        if (size == null) {
            return false;
        }
        pointRadius = size * 0.5;
        return true;
    }

    private void drawPoint(double[] centre) {
        fillAndStroke(Blend2DShape.ellipse(centre[0], centre[1], pointRadius, Math.abs(pointRadius)));
    }

    private boolean drawPoints() {
        Read read = resolveNext();
        switch (read.kind()) {
            case PAIR -> {
                do {
                    drawPoint(readPair(read));
                    read = resolveNext();
                } while (read.kind() == Kind.PAIR);
                return false;
            }
            case VECTOR -> {
                drawVectorPoints((VectorValue) read.value());
                return true;
            }
            case BLOCK -> {
                List<Value> inside = ((AnyBlockValue) read.value()).remaining();
                if (inside.isEmpty() || classify(inside.getFirst()) != Kind.PAIR) {
                    return false;
                }
                for (Value value : inside) {
                    if (classify(value) != Kind.PAIR) {
                        break;
                    }
                    drawPoint(readPair(new Read(Kind.PAIR, value)));
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private void drawVectorPoints(VectorValue vector) {
        int count = (vector.storageLength() - (vector.index() - 1)) >> 1;
        if (count <= 0 || !(hasFill || hasStroke)) {
            return;
        }
        if (vector.kind().bits() != 64) {
            canvas.refuse("points read from a vector of " + vector.kind().spelling() + " numbers");
        }
        for (int point = 0; point < count; point++) {
            int at = vector.index() + point * 2;
            drawPoint(new double[] {Double.longBitsToDouble(vector.storage().at(at)),
                Double.longBitsToDouble(vector.storage().at(at + 1))});
        }
    }

    private boolean drawTriangles() {
        Read read = resolveNext();
        if (read.kind() == Kind.PAIR) {
            index--;
            while (readRaw(index).kind() == Kind.PAIR && readRaw(index + 1).kind() == Kind.PAIR
                    && readRaw(index + 2).kind() == Kind.PAIR) {
                fillAndStroke(Blend2DShape.polygon(flatten(List.of(readPair(readRaw(index)),
                        readPair(readRaw(index + 1)), readPair(readRaw(index + 2))))));
                index += 3;
            }
            return true;
        }
        if (read.kind() == Kind.VECTOR) {
            drawVectorTriangles((VectorValue) read.value());
            return true;
        }
        return false;
    }

    private void drawVectorTriangles(VectorValue vector) {
        int count = (vector.storageLength() - (vector.index() - 1)) / 6;
        if (count <= 0 || !(hasFill || hasStroke)) {
            return;
        }
        if (vector.kind().bits() != 64) {
            canvas.refuse("triangles read from a vector of " + vector.kind().spelling() + " numbers");
        }
        for (int triangle = 0; triangle < count; triangle++) {
            double[] points = new double[6];
            for (int at = 0; at < 6; at++) {
                points[at] = Double.longBitsToDouble(vector.storage().at(vector.index() + triangle * 6 + at));
            }
            fillAndStroke(Blend2DShape.polygon(points));
        }
    }

    private boolean drawArc() {
        double[] centre = resolvePair();
        if (centre == null) {
            return false;
        }
        double[] radius = resolvePair();
        if (radius == null) {
            return false;
        }
        Double begin = readNumber();
        if (begin == null) {
            return false;
        }
        Double sweep = readNumber();
        if (sweep == null) {
            return false;
        }
        Blend2DShape.ArcClosing closing = Blend2DShape.ArcClosing.OPEN;
        if (fetchWord(index, ARGUMENT_WORDS) && command >= ARGUMENT_PIE && command <= ARGUMENT_CHORD) {
            index++;
            closing = command == ARGUMENT_CHORD ? Blend2DShape.ArcClosing.CHORD : Blend2DShape.ArcClosing.PIE;
        }
        fillAndStroke(Blend2DShape.arc(centre[0], centre[1], radius[0], radius[1],
                begin * DEGREES_TO_RADIANS, sweep * DEGREES_TO_RADIANS, closing));
        return true;
    }

    private boolean drawImage() {
        Read read = resolveNext();
        if (read.kind() == Kind.HANDLE && !Blend2DExtension.isImageHandle(read.value())) {
            return false;
        }
        Optional<Blend2DImage> source = readBlitSource(read);
        if (source.isEmpty()) {
            endedEarly = true;
            return true;
        }
        double[] corner = resolvePair();
        if (corner == null) {
            return false;
        }
        double[] size = resolvePairOptionally();
        if (size != null) {
            index++;
            canvas.blitScaledImage(source.get(), corner[0], corner[1], size[0], size[1]);
        } else {
            canvas.blitImage(source.get(), corner[0], corner[1]);
        }
        return true;
    }

    private Optional<Blend2DImage> readBlitSource(Read read) {
        return switch (read.kind()) {
            case HANDLE -> Optional.of(Blend2DExtension.readImage(
                    call.unwrap(read.value(), Blend2DExtension.ImageHandle.class)));
            case IMAGE -> Optional.of(Blend2DExtension.readImage((ImageValue) read.value()))
                    .filter(image -> image.wide() > 0 && image.high() > 0);
            case FILE -> Blend2DExtension.readImageFile(call, (FileValue) read.value());
            default -> Optional.empty();
        };
    }

    private boolean chooseFont() {
        font = Optional.empty();
        Read read = resolveNext();
        switch (read.kind()) {
            case HANDLE -> {
                if (!Blend2DExtension.isFontHandle(read.value())) {
                    handleFace = Optional.empty();
                    return false;
                }
                handleFace = Optional.of(call.unwrap(read.value(), Blend2DExtension.FontHandle.class).face());
                font = handleFace.map(face -> Blend2DFont.of(face, fontSize));
                return true;
            }
            case FILE, STRING -> {
                handleFace = Optional.empty();
                Optional<OpenTypeFace> loaded = Blend2DExtension.readFontFile(call, (AnyStringValue) read.value());
                if (loaded.isEmpty()) {
                    return false;
                }
                loadedFace = loaded;
                font = loadedFace.map(face -> Blend2DFont.of(face, fontSize));
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private boolean writeText() {
        double[] corner = resolvePair();
        if (corner == null) {
            return false;
        }
        Double size = readOptionalNumber();
        double asked = size == null ? 0.0 : size;
        if (asked != fontSize && asked > 0.0) {
            fontSize = asked;
            font = (handleFace.isPresent() ? handleFace : loadedFace).map(face -> Blend2DFont.of(face, fontSize));
        }
        Read read = resolveNext();
        if (read.kind() != Kind.STRING) {
            return false;
        }
        font.ifPresent(chosen -> canvas.fillText(chosen, corner[0], corner[1], readCodePoints((StringValue) read.value())));
        return true;
    }

    private static int[] readCodePoints(StringValue text) {
        String whole = text.storage().text();
        int[] codePoints = whole.codePoints().toArray();
        boolean isByteWide = java.util.Arrays.stream(codePoints).allMatch(codePoint -> codePoint <= LARGEST_BYTE);
        if (isByteWide) {
            return Blend2DFont.decodeUtf8(codePoints);
        }
        return Blend2DFont.decodeUtf16(whole.chars().toArray());
    }

    private boolean scale() {
        Read read = resolveNext();
        Double number = toNumber(read);
        if (number != null) {
            canvas.scale(number, number);
            return true;
        }
        if (read.kind() == Kind.PAIR) {
            double[] pair = readPair(read);
            canvas.scale(pair[0], pair[1]);
            return true;
        }
        return false;
    }

    private boolean rotate() {
        Double degrees = readNumber();
        if (degrees == null) {
            return false;
        }
        double radians = degrees * DEGREES_TO_RADIANS;
        double[] centre = resolvePairOptionally();
        if (centre == null) {
            canvas.rotate(radians);
        } else {
            canvas.rotateAbout(radians, centre[0], centre[1]);
        }
        return true;
    }

    private boolean translate() {
        double[] by = resolvePair();
        if (by == null) {
            return false;
        }
        canvas.translate(by[0], by[1]);
        return true;
    }

    private boolean setAlpha() {
        Double alpha = readNumber();
        if (alpha == null) {
            return false;
        }
        canvas.setGlobalAlpha(alpha);
        return true;
    }

    private boolean setBlend() {
        Read read = resolveNext();
        long mode = fetchMode(index - 1, ARGUMENT_SOURCE_OVER, COMPOSITING_OPERATOR_LARGEST);
        if (mode >= 0) {
            canvas.setOperator(CompositingOperator.values()[(int) mode]);
            return true;
        }
        if (read.kind() == Kind.NONE || read.kind() == Kind.LOGIC && !read.value().isTruthy()) {
            canvas.setOperator(CompositingOperator.SOURCE_OVER);
            return true;
        }
        return false;
    }

    private boolean clearBox() {
        double[] from = resolvePair();
        if (from == null) {
            return false;
        }
        double[] to = resolvePair();
        if (to == null) {
            return false;
        }
        canvas.clearRect(from[0], from[1], to[0] - from[0], to[1] - from[1]);
        return true;
    }

    private boolean clip() {
        Read read = resolveAt(index);
        if (read.kind() == Kind.NONE || read.kind() == Kind.LOGIC && !read.value().isTruthy()) {
            canvas.restoreClipping();
            index++;
            return true;
        }
        double[] from = resolvePair();
        if (from == null) {
            return false;
        }
        double[] to = resolvePair();
        if (to == null) {
            return false;
        }
        canvas.clipToRect(from[0], from[1], to[0] - from[0], to[1] - from[1]);
        return true;
    }

    private boolean drawShape() {
        Read read = resolveNext();
        if (read.kind() == Kind.HANDLE) {
            if (!Blend2DExtension.isPathHandle(read.value())) {
                return false;
            }
            fillAndStroke(call.unwrap(read.value(), Blend2DExtension.PathHandle.class).shape());
        } else if (read.kind() == Kind.BLOCK) {
            fillAndStroke(buildPath(call, (AnyBlockValue) read.value()));
        }
        return true;
    }
}
