package org.jebol.domain.eval.blend2d;

import java.util.Arrays;

final class Blend2DPath {

    static final byte MOVE = 0;

    static final byte LINE = 1;

    static final byte QUAD = 2;

    static final byte CUBIC = 4;

    static final byte CLOSE = 5;

    private static final double KAPPA = 0.55228474983;

    private static final double EPSILON = 1e-14;

    private static final double ANGLE_EPSILON = 1e-8;

    private static final double PI_MUL_2 = 6.28318530717958647692;

    private static final double[] QUARTER_TURNS = {
        1.57079632679489661923, 3.14159265358979323846, 4.71238898038468985769, PI_MUL_2};

    private byte[] commands = new byte[16];

    private double[] vertices = new double[32];

    private int size;

    int size() {
        return size;
    }

    byte commandAt(int at) {
        return commands[at];
    }

    double xAt(int at) {
        return vertices[2 * at];
    }

    double yAt(int at) {
        return vertices[2 * at + 1];
    }

    boolean isEmpty() {
        return size == 0;
    }

    void addRaw(byte command, double x, double y) {
        add(command, x, y);
    }

    Point pointAt(int at) {
        return new Point(xAt(at), yAt(at));
    }

    void setPointAt(int at, Point point) {
        vertices[2 * at] = point.x();
        vertices[2 * at + 1] = point.y();
    }

    byte commandFromEnd(int back) {
        return commands[size - back];
    }

    Point pointFromEnd(int back) {
        return pointAt(size - back);
    }

    void setPointFromEnd(int back, Point point) {
        setPointAt(size - back, point);
    }

    void dropLast(int count) {
        size -= count;
    }

    void clear() {
        size = 0;
    }

    void moveTo(Point point) {
        moveTo(point.x(), point.y());
    }

    void lineTo(Point point) {
        lineTo(point.x(), point.y());
    }

    void quadTo(Point control, Point end) {
        add(QUAD, control.x(), control.y());
        add(LINE, end.x(), end.y());
    }

    void cubicTo(Point first, Point second, Point end) {
        cubicTo(first.x(), first.y(), second.x(), second.y(), end.x(), end.y());
    }

    void quadTo(double x1, double y1, double x2, double y2) {
        add(QUAD, x1, y1);
        add(LINE, x2, y2);
    }

    void smoothQuadTo(double x2, double y2) {
        if (size == 0 || commands[size - 1] >= CLOSE) {
            return;
        }
        double x1 = xAt(size - 1);
        double y1 = yAt(size - 1);
        if (size >= 2 && commands[size - 2] == QUAD) {
            x1 += x1 - xAt(size - 2);
            y1 += y1 - yAt(size - 2);
        }
        quadTo(x1, y1, x2, y2);
    }

    void smoothCubicTo(double x2, double y2, double x3, double y3) {
        if (size == 0 || commands[size - 1] >= CLOSE) {
            return;
        }
        double x1 = xAt(size - 1);
        double y1 = yAt(size - 1);
        if (size >= 2 && commands[size - 2] == CUBIC) {
            x1 += x1 - xAt(size - 2);
            y1 += y1 - yAt(size - 2);
        }
        cubicTo(x1, y1, x2, y2, x3, y3);
    }

    Point lastVertex() {
        if (size == 0) {
            return new Point(0, 0);
        }
        int at = size - 1;
        if (commands[at] != CLOSE) {
            return pointAt(at);
        }
        while (at > 0) {
            byte command = commands[--at];
            if (command == CLOSE) {
                return new Point(0, 0);
            }
            if (command == MOVE) {
                return pointAt(at);
            }
        }
        return new Point(0, 0);
    }

    void ellipticArcTo(double radiusX, double radiusY, double rotation, boolean isLarge, boolean isSweep,
                       double endX, double endY) {
        if (size == 0 || commands[size - 1] > LINE) {
            return;
        }
        Point p0 = pointAt(size - 1);
        Point p1 = new Point(endX, endY);
        double rx = Math.abs(radiusX);
        double ry = Math.abs(radiusY);
        if (p0.isSameAs(p1)) {
            return;
        }
        if (!(rx > EPSILON) | !(ry > EPSILON)) {
            lineTo(p1);
            return;
        }
        double rotationSin = StrictMath.sin(rotation);
        double rotationCos = StrictMath.cos(rotation);
        double[] matrix = {rotationCos, -rotationSin, rotationSin, rotationCos, 0, 0};
        Point v = map(matrix, p0.minus(p1).times(0.5));
        double scale = v.x() * v.x() / (rx * rx) + v.y() * v.y() / (ry * ry);
        if (scale > 1.0) {
            scale = Math.sqrt(scale);
            rx *= scale;
            ry *= scale;
        }
        postScale(matrix, 1.0 / rx, 1.0 / ry);
        Point pp0 = map(matrix, p0);
        Point pp1 = map(matrix, p1);
        v = pp1.minus(pp0).times(0.5);
        Point centre = pp0.plus(v);
        double lengthSquared = v.lengthSquared();
        if (lengthSquared < 1.0) {
            v = v.normal().times(Math.sqrt(1.0 / lengthSquared - 1.0));
            centre = isLarge != isSweep ? centre.plus(v) : centre.minus(v);
        }
        Point v1 = pp0.minus(centre);
        Point v2 = pp1.minus(centre);
        double[] arc = {v1.x(), v1.y(), -v1.y(), v1.x(), 0, 0};
        arc[4] += centre.x();
        arc[5] += centre.y();
        postScale(arc, rx, ry);
        arc = multiply(arc, new double[] {rotationCos, rotationSin, -rotationSin, rotationCos, 0, 0});
        rotationSin = v1.cross(v2);
        rotationCos = v1.dot(v2);
        double sweepAngle = StrictMath.atan2(rotationSin, rotationCos);
        if (isSweep) {
            if (sweepAngle < 0) {
                sweepAngle += PI_MUL_2;
            }
            v2 = new Point(rotationCos, rotationSin);
        } else {
            if (sweepAngle > 0) {
                sweepAngle -= PI_MUL_2;
            }
            arc[2] = -arc[2];
            arc[3] = -arc[3];
            v2 = new Point(rotationCos, -rotationSin);
            sweepAngle = Math.abs(sweepAngle);
        }
        v1 = new Point(1, 0);
        v = new Point(1, 1);
        int quarters = 3;
        if (sweepAngle < QUARTER_TURNS[2] + ANGLE_EPSILON) {
            quarters = 2;
        }
        if (sweepAngle < QUARTER_TURNS[1] + ANGLE_EPSILON) {
            quarters = 1;
        }
        if (sweepAngle < QUARTER_TURNS[0] + ANGLE_EPSILON) {
            quarters = 0;
        }
        while (quarters != 0) {
            v1 = v1.normal();
            arcQuadrantTo(map(arc, v), map(arc, v1));
            v = v.normal();
            quarters--;
        }
        v = v1.plus(v2);
        v = new Point(2.0 * v.x(), 2.0 * v.y()).dividedBy(v.dot(v));
        Point control = map(arc, v);
        double w = Math.sqrt(0.5 * (1.0 + v1.dot(v2)));
        addConicAsCubic(control.x(), control.y(), p1.x(), p1.y(), w);
    }

    private static Point map(double[] matrix, Point point) {
        return new Point(point.x() * matrix[0] + point.y() * matrix[2] + matrix[4],
                point.x() * matrix[1] + point.y() * matrix[3] + matrix[5]);
    }

    private static void postScale(double[] matrix, double x, double y) {
        matrix[0] *= x;
        matrix[1] *= y;
        matrix[2] *= x;
        matrix[3] *= y;
        matrix[4] *= x;
        matrix[5] *= y;
    }

    private static double[] multiply(double[] a, double[] b) {
        return new double[] {
            a[0] * b[0] + a[1] * b[2],
            a[0] * b[1] + a[1] * b[3],
            a[2] * b[0] + a[3] * b[2],
            a[2] * b[1] + a[3] * b[3],
            a[4] * b[0] + a[5] * b[2] + b[4],
            a[4] * b[1] + a[5] * b[3] + b[5]};
    }

    void arcQuadrantTo(Point corner, Point end) {
        Point start = pointFromEnd(1);
        cubicTo(start.plus(corner.minus(start).times(KAPPA)), end.plus(corner.minus(end).times(KAPPA)), end);
    }

    private void add(byte command, double x, double y) {
        if (size == commands.length) {
            commands = Arrays.copyOf(commands, size * 2);
            vertices = Arrays.copyOf(vertices, size * 4);
        }
        commands[size] = command;
        vertices[2 * size] = x;
        vertices[2 * size + 1] = y;
        size++;
    }

    void moveTo(double x, double y) {
        add(MOVE, x, y);
    }

    void lineTo(double x, double y) {
        add(LINE, x, y);
    }

    void cubicTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        add(CUBIC, x1, y1);
        add(CUBIC, x2, y2);
        add(LINE, x3, y3);
    }

    void close() {
        add(CLOSE, Double.NaN, Double.NaN);
    }

    void addEllipse(double cx, double cy, double rx, double ry) {
        double kx = rx * KAPPA;
        double ky = ry * KAPPA;
        moveTo(cx + rx, cy);
        cubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry);
        cubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy);
        cubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry);
        cubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy);
        close();
    }

    void addBox(double x0, double y0, double x1, double y1) {
        moveTo(x0, y0);
        lineTo(x1, y0);
        lineTo(x1, y1);
        lineTo(x0, y1);
        close();
    }

    void addRoundedBox(double x, double y, double wide, double high, double radiusX, double radiusY) {
        double x1 = x + wide;
        double y1 = y + high;
        double rx = minimum(Math.abs(radiusX), wide * 0.5);
        double ry = minimum(Math.abs(radiusY), high * 0.5);
        if (!(rx > EPSILON && ry > EPSILON)) {
            addBox(x, y, x1, y1);
            return;
        }
        double kx = rx * (1.0 - KAPPA);
        double ky = ry * (1.0 - KAPPA);
        moveTo(x + rx, y);
        lineTo(x1 - rx, y);
        cubicTo(x1 - kx, y, x1, y + ky, x1, y + ry);
        lineTo(x1, y1 - ry);
        cubicTo(x1, y1 - ky, x1 - kx, y1, x1 - rx, y1);
        lineTo(x + rx, y1);
        cubicTo(x + kx, y1, x, y1 - ky, x, y1 - ry);
        lineTo(x, y + ry);
        cubicTo(x, y + ky, x + kx, y, x + rx, y);
        close();
    }

    void addArc(double cx, double cy, double rx, double ry, double start, double sweep, boolean isFirstAMove) {
        double startSin = StrictMath.sin(start);
        double startCos = StrictMath.cos(start);
        double m00 = startCos * rx;
        double m01 = startSin * ry;
        double m10 = -startSin * rx;
        double m11 = startCos * ry;
        double m20 = 0.0 * rx + cx;
        double m21 = 0.0 * ry + cy;
        if (sweep < 0) {
            m10 = -m10;
            m11 = -m11;
            sweep = -sweep;
        }
        double[] matrix = {m00, m01, m10, m11, m20, m21};
        double v1x = 1.0;
        double v1y = 0.0;
        double vcx = 1.0;
        double vcy = 1.0;
        double v2x;
        double v2y;
        if (sweep >= PI_MUL_2 - EPSILON) {
            sweep = PI_MUL_2;
            v2x = v1x;
            v2y = v1y;
        } else {
            if (Double.isNaN(sweep)) {
                return;
            }
            v2x = StrictMath.cos(sweep);
            v2y = StrictMath.sin(sweep);
        }
        double p0x = mapX(matrix, v1x, v1y);
        double p0y = mapY(matrix, v1x, v1y);
        add(isFirstAMove ? MOVE : LINE, p0x, p0y);
        int quarter = 0;
        while (sweep > QUARTER_TURNS[quarter]) {
            double turnedX = -v1y;
            v1y = v1x;
            v1x = turnedX;
            double p1x = mapX(matrix, vcx, vcy);
            double p1y = mapY(matrix, vcx, vcy);
            double p2x = mapX(matrix, v1x, v1y);
            double p2y = mapY(matrix, v1x, v1y);
            cubicTo(p0x + (p1x - p0x) * KAPPA, p0y + (p1y - p0y) * KAPPA,
                    p2x + (p1x - p2x) * KAPPA, p2y + (p1y - p2y) * KAPPA, p2x, p2y);
            if (++quarter == 4) {
                return;
            }
            double turnedCx = -vcy;
            vcy = vcx;
            vcx = turnedCx;
            p0x = p2x;
            p0y = p2y;
        }
        vcx = v1x + v2x;
        vcy = v1y + v2y;
        double length = vcx * vcx + vcy * vcy;
        vcx = 2.0 * vcx / length;
        vcy = 2.0 * vcy / length;
        double w = Math.sqrt(0.5 * (v1x * v2x + v1y * v2y) + 0.5);
        addConicAsCubic(mapX(matrix, vcx, vcy), mapY(matrix, vcx, vcy), mapX(matrix, v2x, v2y), mapY(matrix, v2x, v2y), w);
    }

    private void addConicAsCubic(double x1, double y1, double x2, double y2, double w) {
        double k = 4.0 * w / (3.0 * (1.0 + w));
        double x0 = xAt(size - 1);
        double y0 = yAt(size - 1);
        cubicTo(x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, x2 + (x1 - x2) * k, y2 + (y1 - y2) * k, x2, y2);
    }

    private static double mapX(double[] matrix, double x, double y) {
        return x * matrix[0] + y * matrix[2] + matrix[4];
    }

    private static double mapY(double[] matrix, double x, double y) {
        return x * matrix[1] + y * matrix[3] + matrix[5];
    }

    private static double minimum(double a, double b) {
        return b < a ? b : a;
    }
}
