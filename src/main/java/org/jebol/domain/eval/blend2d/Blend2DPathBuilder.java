package org.jebol.domain.eval.blend2d;

public final class Blend2DPathBuilder {

    private final Blend2DPath path = new Blend2DPath();

    public void moveTo(double x, double y) {
        path.moveTo(x, y);
    }

    public void lineTo(double x, double y) {
        path.lineTo(x, y);
    }

    public void quadTo(double x1, double y1, double x2, double y2) {
        path.quadTo(x1, y1, x2, y2);
    }

    public void smoothQuadTo(double x2, double y2) {
        path.smoothQuadTo(x2, y2);
    }

    public void cubicTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        path.cubicTo(x1, y1, x2, y2, x3, y3);
    }

    public void smoothCubicTo(double x2, double y2, double x3, double y3) {
        path.smoothCubicTo(x2, y2, x3, y3);
    }

    public void ellipticArcTo(double radiusX, double radiusY, double rotation, boolean isLarge, boolean isSweep,
                              double endX, double endY) {
        path.ellipticArcTo(radiusX, radiusY, rotation, isLarge, isSweep, endX, endY);
    }

    public void close() {
        path.close();
    }

    public double lastX() {
        return path.lastVertex().x();
    }

    public double lastY() {
        return path.lastVertex().y();
    }

    public Blend2DShape build() {
        return Blend2DShape.fromPath(path);
    }
}
