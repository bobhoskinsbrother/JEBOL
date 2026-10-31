package org.jebol.domain.eval.blend2d;

import java.util.List;

public final class Blend2DShape {

    public enum ArcClosing {
        OPEN,
        PIE,
        CHORD
    }

    private final Blend2DPath path;

    private Blend2DShape(Blend2DPath path) {
        this.path = path;
    }

    Blend2DPath path() {
        return path;
    }

    public static Blend2DShape polygon(double[] points) {
        Blend2DPath path = new Blend2DPath();
        if (points.length > 0) {
            path.moveTo(points[0], points[1]);
            for (int at = 2; at < points.length; at += 2) {
                path.lineTo(points[at], points[at + 1]);
            }
            path.close();
        }
        return new Blend2DShape(path);
    }

    public static Blend2DShape polylines(List<double[]> figures) {
        Blend2DPath path = new Blend2DPath();
        for (double[] points : figures) {
            path.moveTo(points[0], points[1]);
            for (int at = 2; at < points.length; at += 2) {
                path.lineTo(points[at], points[at + 1]);
            }
        }
        return new Blend2DShape(path);
    }

    public static Blend2DShape box(double x, double y, double wide, double high) {
        Blend2DPath path = new Blend2DPath();
        path.addBox(x, y, wide + x, high + y);
        return new Blend2DShape(path);
    }

    public static Blend2DShape ellipse(double cx, double cy, double rx, double ry) {
        Blend2DPath path = new Blend2DPath();
        path.addEllipse(cx, cy, rx, ry);
        return new Blend2DShape(path);
    }

    public static Blend2DShape roundedBox(double x, double y, double wide, double high, double radiusX, double radiusY) {
        Blend2DPath path = new Blend2DPath();
        path.addRoundedBox(x, y, wide, high, radiusX, radiusY);
        return new Blend2DShape(path);
    }

    public static Blend2DShape arc(double cx, double cy, double rx, double ry, double start, double sweep,
                                   ArcClosing closing) {
        Blend2DPath path = new Blend2DPath();
        if (closing == ArcClosing.PIE) {
            path.moveTo(cx, cy);
        }
        path.addArc(cx, cy, rx, ry, start, sweep, closing != ArcClosing.PIE);
        if (closing != ArcClosing.OPEN) {
            path.close();
        }
        return new Blend2DShape(path);
    }

    public static Blend2DShape cubics(double[] points) {
        Blend2DPath path = new Blend2DPath();
        path.moveTo(points[0], points[1]);
        for (int at = 2; at + 5 < points.length; at += 6) {
            path.cubicTo(points[at], points[at + 1], points[at + 2], points[at + 3], points[at + 4], points[at + 5]);
        }
        return new Blend2DShape(path);
    }
}
