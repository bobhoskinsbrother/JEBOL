package org.jebol.domain.eval.blend2d;

record Point(double x, double y) {

    Point plus(Point other) {
        return new Point(x + other.x, y + other.y);
    }

    Point minus(Point other) {
        return new Point(x - other.x, y - other.y);
    }

    Point times(double factor) {
        return new Point(x * factor, y * factor);
    }

    Point dividedBy(double divisor) {
        return new Point(x / divisor, y / divisor);
    }

    Point negate() {
        return new Point(-x, -y);
    }

    double dot(Point other) {
        return x * other.x + y * other.y;
    }

    double cross(Point other) {
        return x * other.y - y * other.x;
    }

    double lengthSquared() {
        return x * x + y * y;
    }

    double length() {
        return Math.sqrt(lengthSquared());
    }

    Point normal() {
        return new Point(-y, x);
    }

    Point unit() {
        return dividedBy(length());
    }

    boolean isZero() {
        return x == 0 && y == 0;
    }

    boolean isSameAs(Point other) {
        return x == other.x && y == other.y;
    }

    Point lerp(Point to, double t) {
        return new Point((x - t * x) + t * to.x, (y - t * y) + t * to.y);
    }

    Point midway(Point to) {
        return new Point(0.5 * x + 0.5 * to.x, 0.5 * y + 0.5 * to.y);
    }

    Point minimum(Point other) {
        return new Point(other.x < x ? other.x : x, other.y < y ? other.y : y);
    }

    Point maximum(Point other) {
        return new Point(x < other.x ? other.x : x, y < other.y ? other.y : y);
    }
}
