package org.jebol.domain.eval.blend2d;

import java.util.Arrays;

final class PathStroker {

    static final int JOIN_MITER_CLIP = 0;

    static final int JOIN_MITER_BEVEL = 1;

    static final int JOIN_MITER_ROUND = 2;

    static final int JOIN_BEVEL = 3;

    static final int JOIN_ROUND = 4;

    static final int CAP_BUTT = 0;

    static final int CAP_SQUARE = 1;

    static final int CAP_ROUND = 2;

    static final int CAP_ROUND_REVERSED = 3;

    static final int CAP_TRIANGLE = 4;

    static final int CAP_TRIANGLE_REVERSED = 5;

    private static final double MITER_MINIMUM = 1e-10;

    private static final double LENGTH_EPSILON_SQUARED = 1e-10 * 1e-10;

    private static final double COLLINEARITY_EPSILON = 1e-10;

    private static final double CUSP_T_THRESHOLD = 1e-10;

    private static final double DEGENERATE_FLATNESS = 1e-6;

    private static final double OFFSET_QUAD_EPSILON_T = 1e-5;

    private static final double SIMPLIFY_TOLERANCE = 0.05;

    private static final double OFFSET_PARAMETER = 0.414213562;

    interface FigureSink {

        void accept(Blend2DPath outer, Blend2DPath inner, Blend2DPath startCap);
    }

    record Options(double width, int join, double miterLimit, int startCap, int endCap) {
    }

    private final Blend2DPath input;

    private final Options options;

    private final double miterLimit;

    private final double miterLimitSquared;

    private final int joinType;

    private final Blend2DPath a = new Blend2DPath();

    private final Blend2DPath b = new Blend2DPath();

    private final Blend2DPath c = new Blend2DPath();

    private final double distanceA;

    private int cursor;

    private Point p0;

    private Point n0;

    private Point pInitial;

    private Point nInitial;

    private boolean isOpen;

    private boolean isClosed;

    PathStroker(Blend2DPath input, Options options) {
        this.input = input;
        this.options = options;
        distanceA = options.width() * 0.5;
        int join = options.join();
        if (join == JOIN_MITER_CLIP || join == JOIN_MITER_BEVEL || join == JOIN_MITER_ROUND) {
            joinType = join == JOIN_MITER_BEVEL ? JOIN_BEVEL : join == JOIN_MITER_ROUND ? JOIN_ROUND : join;
            miterLimit = distanceA * options.miterLimit();
            miterLimitSquared = miterLimit * miterLimit;
        } else {
            joinType = join;
            miterLimit = MITER_MINIMUM;
            miterLimitSquared = MITER_MINIMUM * MITER_MINIMUM;
        }
    }

    private boolean isSideB(Point from, Point to) {
        return from.cross(to) >= 0;
    }

    private double distance(boolean isB) {
        return isB ? -distanceA : distanceA;
    }

    private double doubled(boolean isB) {
        return isB ? -options.width() : options.width();
    }

    private Blend2DPath outer(boolean isB) {
        return isB ? b : a;
    }

    private Blend2DPath inner(boolean isB) {
        return isB ? a : b;
    }

    private boolean isAtEnd() {
        return cursor >= input.size();
    }

    boolean stroke(FigureSink sink) {
        while (!isAtEnd()) {
            if (input.commandAt(cursor) != Blend2DPath.MOVE) {
                if (input.commandAt(cursor) != Blend2DPath.CLOSE) {
                    return false;
                }
                cursor++;
                continue;
            }
            a.clear();
            b.clear();
            p0 = input.pointAt(cursor);
            pInitial = p0;
            isOpen = false;
            isClosed = false;
            cursor++;
            if (!strokeFigure()) {
                return false;
            }
            cursor += isClosed ? 1 : 0;
            if (!isOpen) {
                continue;
            }
            if (isClosed) {
                joinEndPoint(nInitial);
                a.close();
                b.close();
                c.clear();
            } else {
                int startCap = sanitiseCap(options.startCap());
                int endCap = sanitiseCap(options.endCap());
                addCap(a, p0, b.pointFromEnd(1), endCap);
                c.clear();
                c.moveTo(b.pointAt(0));
                addCap(c, pInitial, a.pointAt(0), startCap);
            }
            sink.accept(a, b, c);
        }
        return true;
    }

    private static int sanitiseCap(int cap) {
        return cap <= CAP_TRIANGLE_REVERSED ? cap : CAP_BUTT;
    }

    private boolean strokeFigure() {
        while (!isAtEnd()) {
            byte command = input.commandAt(cursor);
            Point p1 = input.pointAt(cursor);
            if (command == Blend2DPath.LINE) {
                cursor++;
                strokeLinesFrom(p1);
            } else if (command == Blend2DPath.QUAD) {
                cursor += 2;
                if (cursor > input.size()) {
                    return false;
                }
                strokeQuad(p1);
            } else if (command == Blend2DPath.CUBIC) {
                cursor += 3;
                if (cursor > input.size()) {
                    return false;
                }
                strokeCubic();
            } else {
                if (isClosed || command != Blend2DPath.CLOSE) {
                    return true;
                }
                isClosed = true;
                strokeLinesFrom(pInitial);
            }
        }
        return true;
    }

    private void strokeLinesFrom(Point first) {
        Point p1 = first;
        Point v1 = p1.minus(p0);
        if (v1.lengthSquared() < LENGTH_EPSILON_SQUARED) {
            return;
        }
        Point n1 = v1.unit().normal();
        if (!isOpen) {
            openLineTo(p1, n1);
            return;
        }
        while (true) {
            joinLineTo(p1, n1);
            if (isAtEnd()) {
                return;
            }
            if (input.commandAt(cursor) != Blend2DPath.LINE) {
                return;
            }
            p1 = input.pointAt(cursor);
            cursor++;
            v1 = p1.minus(p0);
            if (v1.lengthSquared() < LENGTH_EPSILON_SQUARED) {
                return;
            }
            n1 = v1.unit().normal();
        }
    }

    private void strokeQuad(Point p1) {
        Point[] quad = {input.pointAt(cursor - 3), input.pointAt(cursor - 2), input.pointAt(cursor - 1)};
        Point p2 = quad[2];
        Point v2 = p2.minus(p1);
        Point v1 = p1.minus(p0);
        Point n1 = v1.unit().normal();
        double cm = v2.cross(v1);
        if (Math.abs(cm) <= COLLINEARITY_EPSILON) {
            double dot = v1.negate().dot(v2);
            if (dot > 0.0) {
                double r1 = p1.minus(p0).dot(v1);
                double r2 = p2.minus(p0).dot(v1);
                double t = r1 / (2.0 * r1 - r2);
                if (t > 0.0 && t < 1.0) {
                    smoothPolyTo(new Point[] {evaluateQuad(quad, t), p2});
                    return;
                }
            }
            strokeLinesFrom(p2);
            return;
        }
        if (v1.lengthSquared() < LENGTH_EPSILON_SQUARED || v2.lengthSquared() < LENGTH_EPSILON_SQUARED) {
            strokeLinesFrom(p2);
            return;
        }
        if (!isOpen) {
            openCurve(n1);
        } else {
            joinCurve(n1);
        }
        offsetQuad(quad);
    }

    private void strokeCubic() {
        Point[] p = new Point[7];
        p[0] = p0;
        p[1] = input.pointAt(cursor - 3);
        p[2] = input.pointAt(cursor - 2);
        p[3] = input.pointAt(cursor - 1);
        int cusp = 0;
        if (isCubicFlat(p, DEGENERATE_FLATNESS)) {
            double dot1 = p[0].minus(p[1]).dot(p[3].minus(p[1]));
            double dot2 = p[0].minus(p[2]).dot(p[3].minus(p[2]));
            if (!(dot1 < 0.0) || !(dot2 < 0.0)) {
                Point r = cubicStartTangent(p);
                double r1 = p[1].minus(p[0]).dot(r);
                double r2 = p[2].minus(p[0]).dot(r);
                double r3 = p[3].minus(p[0]).dot(r);
                double scale = 1.0 / (3.0 * r1 - 3.0 * r2 + r3);
                double middle = 2.0 * r1 - r2;
                double root = Math.sqrt(r2 * (r2 - r1) - r1 * (r3 - r1));
                double t1 = scale * (middle - root);
                double t2 = scale * (middle + root);
                Point[] poly = new Point[3];
                int count = 0;
                if (t1 > CUSP_T_THRESHOLD && t1 < 1.0 - CUSP_T_THRESHOLD) {
                    poly[count++] = evaluateCubic(p, t1);
                }
                if (t2 > CUSP_T_THRESHOLD && t2 < 1.0 - CUSP_T_THRESHOLD) {
                    poly[count++] = evaluateCubic(p, t2);
                }
                if (count == 0) {
                    strokeLinesFrom(p[3]);
                    return;
                }
                poly[count++] = p[3];
                smoothPolyTo(Arrays.copyOf(poly, count));
            } else {
                strokeLinesFrom(p[3]);
            }
            return;
        }
        double[] inflection = cubicInflectionParameter(p);
        double tCusp = inflection[0];
        double tl = inflection[1];
        if (tl == 0.0 && tCusp > 0.0 && tCusp < 1.0) {
            splitCubicInHalfInPlace(p);
            cusp = 1;
        }
        while (true) {
            Point v1 = p[1].minus(p0);
            if (v1.isZero()) {
                v1 = p[2].minus(p0);
            }
            Point n1 = v1.unit().normal();
            if (!isOpen) {
                openCurve(n1);
            } else if (cusp >= 0) {
                joinCurve(n1);
            } else {
                joinCusp(n1);
            }
            offsetCubic(new Point[] {p[0], p[1], p[2], p[3]});
            if (cusp <= 0) {
                return;
            }
            cusp = -1;
            p[0] = p[3];
            p[1] = p[4];
            p[2] = p[5];
            p[3] = p[6];
        }
    }

    private void openLineTo(Point p1, Point n1) {
        Point w = n1.times(distanceA);
        a.moveTo(p0.plus(w));
        b.moveTo(p0.minus(w));
        p0 = p1;
        n0 = n1;
        nInitial = n1;
        a.lineTo(p0.plus(w));
        b.lineTo(p0.minus(w));
        isOpen = true;
    }

    private void joinLineTo(Point p1, Point n1) {
        if (n0.isSameAs(n1)) {
            a.dropLast(a.commandFromEnd(2) <= Blend2DPath.LINE ? 1 : 0);
            b.dropLast(b.commandFromEnd(2) <= Blend2DPath.LINE ? 1 : 0);
            Point w1 = n1.times(distanceA);
            a.lineTo(p1.plus(w1));
            b.lineTo(p1.minus(w1));
        } else {
            boolean isB = isSideB(n0, n1);
            Point m = n0.plus(n1);
            Point k = m.times(doubled(isB)).dividedBy(m.lengthSquared());
            Point w1 = n1.times(distance(isB));
            Blend2DPath outer = outer(isB);
            boolean isMitered = outerJoin(outer, n1, w1, k, distance(isB), doubled(isB));
            outer.dropLast(isMitered ? 1 : 0);
            outer.lineTo(p1.plus(w1));
            Blend2DPath inner = inner(isB);
            innerJoinLineTo(inner, p0.minus(w1), p1.minus(w1), p0.minus(k));
            inner.lineTo(p1.minus(w1));
        }
        p0 = p1;
        n0 = n1;
    }

    private void openCurve(Point n1) {
        Point w = n1.times(distanceA);
        a.moveTo(p0.plus(w));
        b.moveTo(p0.minus(w));
        n0 = n1;
        nInitial = n1;
        isOpen = true;
    }

    private void joinCurve(Point n1) {
        if (n0.isSameAs(n1)) {
            return;
        }
        boolean isB = isSideB(n0, n1);
        Point m = n0.plus(n1);
        Point k = m.times(doubled(isB)).dividedBy(m.lengthSquared());
        Point w1 = n1.times(distance(isB));
        outerJoin(outer(isB), n1, w1, k, distance(isB), doubled(isB));
        Blend2DPath inner = inner(isB);
        inner.lineTo(p0);
        inner.lineTo(p0.minus(w1));
        n0 = n1;
    }

    private void joinCusp(Point n1) {
        boolean isB = isSideB(n0, n1);
        Point w1 = n1.times(distance(isB));
        joinDullRound(outer(isB), distance(isB), doubled(isB), w1);
        inner(isB).lineTo(p0.minus(w1));
        n0 = n1;
    }

    private void joinCuspAndLineTo(Point n1, Point p1) {
        boolean isB = isSideB(n0, n1);
        Point w1 = n1.times(distance(isB));
        Blend2DPath outer = outer(isB);
        joinDullRound(outer, distance(isB), doubled(isB), w1);
        outer.lineTo(p1.plus(w1));
        Blend2DPath inner = inner(isB);
        inner.lineTo(p0.minus(w1));
        inner.lineTo(p1.minus(w1));
        n0 = n1;
        p0 = p1;
    }

    private void smoothPolyTo(Point[] poly) {
        Point p1 = poly[0];
        Point v1 = p1.minus(p0);
        if (v1.lengthSquared() < LENGTH_EPSILON_SQUARED) {
            return;
        }
        Point n1 = v1.unit().normal();
        if (!isOpen) {
            openLineTo(p1, n1);
        } else {
            joinLineTo(p1, n1);
        }
        for (int at = 1; at < poly.length; at++) {
            p1 = poly[at];
            v1 = p1.minus(p0);
            if (v1.lengthSquared() < LENGTH_EPSILON_SQUARED) {
                continue;
            }
            n1 = v1.unit().normal();
            joinCuspAndLineTo(n1, p1);
        }
    }

    private void joinEndPoint(Point n1) {
        if (n0.isSameAs(n1)) {
            a.dropLast(a.commandFromEnd(2) <= Blend2DPath.LINE ? 1 : 0);
            b.dropLast(b.commandFromEnd(2) <= Blend2DPath.LINE ? 1 : 0);
            return;
        }
        boolean isB = isSideB(n0, n1);
        Point m = n0.plus(n1);
        Point w1 = n1.times(distance(isB));
        Point k = m.times(doubled(isB)).dividedBy(m.lengthSquared());
        Blend2DPath outer = outer(isB);
        boolean isMitered = outerJoin(outer, n1, w1, k, distance(isB), doubled(isB));
        if (isMitered && outer.commandAt(1) == Blend2DPath.LINE) {
            outer.dropLast(1);
            outer.setPointAt(0, outer.pointFromEnd(1));
            outer.dropLast(outer.commandFromEnd(2) <= Blend2DPath.LINE ? 1 : 0);
        }
        Blend2DPath inner = inner(isB);
        if (inner.commandAt(1) <= Blend2DPath.LINE) {
            Point innerPoint = p0.minus(k);
            if (inner.commandFromEnd(2) <= Blend2DPath.LINE
                    && isInnerJoinInside(inner.pointFromEnd(2), inner.pointFromEnd(1), inner.pointAt(0),
                            inner.pointAt(1), innerPoint)) {
                inner.setPointAt(0, innerPoint);
                inner.dropLast(1);
            } else {
                inner.lineTo(p0);
                inner.lineTo(inner.pointAt(0));
            }
        }
    }

    private void innerJoinLineTo(Blend2DPath out, Point lineP0, Point lineP1, Point innerPoint) {
        if (out.commandFromEnd(2) <= Blend2DPath.LINE
                && isInnerJoinInside(out.pointFromEnd(2), out.pointFromEnd(1), lineP0, lineP1, innerPoint)) {
            out.setPointFromEnd(1, innerPoint);
        } else {
            out.lineTo(p0);
            out.lineTo(lineP0);
        }
    }

    private static boolean isInnerJoinInside(Point a0, Point a1, Point b0, Point b1, Point join) {
        Point lowest = a0.minimum(a1).maximum(b0.minimum(b1));
        Point highest = a0.maximum(a1).minimum(b0.maximum(b1));
        return join.x() >= lowest.x() & join.y() >= lowest.y() & join.x() <= highest.x() & join.y() <= highest.y();
    }

    private boolean outerJoin(Blend2DPath out, Point n1, Point w1, Point k, double d, double d2) {
        Point pb = p0.plus(w1);
        if (k.lengthSquared() <= miterLimitSquared) {
            out.dropLast(out.commandFromEnd(2) <= Blend2DPath.LINE ? 1 : 0);
            out.lineTo(p0.plus(k));
            out.lineTo(pb);
            return true;
        }
        if (joinType == JOIN_MITER_CLIP) {
            double b2 = Math.abs(k.cross(n0));
            if (b2 > 0) {
                b2 = b2 * miterLimit / k.length();
            } else {
                b2 = miterLimit;
            }
            out.dropLast(out.commandFromEnd(2) <= Blend2DPath.LINE ? 1 : 0);
            out.lineTo(p0.plus(n0.times(d)).minus(n0.normal().times(b2)));
            out.lineTo(p0.plus(n1.times(d)).plus(n1.normal().times(b2)));
            out.lineTo(pb);
            return true;
        }
        if (joinType == JOIN_ROUND) {
            Point pa = out.pointFromEnd(1);
            if (p0.minus(pa).dot(p0.minus(pb)) < 0.0) {
                Point n2 = pb.minus(pa).unit().normal();
                Point m = n0.plus(n2);
                Point k0 = m.times(d2).dividedBy(m.lengthSquared());
                Point q = n2.times(d);
                Point pc1 = p0.plus(k0);
                Point pp1 = p0.plus(q);
                Point pc2 = pc1.lerp(pp1, 2.0);
                arcThroughDullAngle(out, p0, pa, pp1, pc1);
                arcThroughDullAngle(out, p0, pp1, pb, pc2);
            } else {
                Point pm = pa.midway(pb);
                Point pi = p0.plus(k);
                double w = Math.sqrt(p0.minus(pm).length() / p0.minus(pi).length());
                double weight = 4.0 * w / (3.0 * (1.0 + w));
                out.cubicTo(pa.plus(pi.minus(pa).times(weight)), pb.plus(pi.minus(pb).times(weight)), pb);
            }
            return false;
        }
        out.lineTo(pb);
        return false;
    }

    private static void arcThroughDullAngle(Blend2DPath out, Point p0, Point pa, Point pb, Point intersection) {
        Point pm = pa.plus(pb).times(0.5);
        double w = Math.sqrt(p0.minus(pm).length() / p0.minus(intersection).length());
        double weight = 4 * w / (3.0 * (1.0 + w));
        out.cubicTo(pa.plus(intersection.minus(pa).times(weight)), pb.plus(intersection.minus(pb).times(weight)), pb);
    }

    private void joinDullRound(Blend2DPath out, double d, double d2, Point w1) {
        Point pa = out.pointFromEnd(1);
        Point pb = p0.plus(w1);
        Point n2 = pb.minus(pa).unit().normal();
        if (!Double.isFinite(n2.x())) {
            return;
        }
        Point m = n0.plus(n2);
        Point k = m.times(d2).dividedBy(m.lengthSquared());
        Point q = n2.times(d);
        Point pc1 = p0.plus(k);
        Point pp1 = p0.plus(q);
        Point pc2 = pc1.lerp(pp1, 2.0);
        arcThroughDullAngle(out, p0, pa, pp1, pc1);
        arcThroughDullAngle(out, p0, pp1, pb, pc2);
    }

    private void offsetQuad(Point[] bezier) {
        double[] ts = new double[3];
        int count = quadOffsetCusps(bezier, distanceA, ts);
        ts[count++] = 1.0;
        QuadPartsByT parts = new QuadPartsByT(bezier, ts, count);
        do {
            while (true) {
                double t = quadParameterAtAngle(parts.part, OFFSET_PARAMETER);
                if (!(t > OFFSET_QUAD_EPSILON_T && t < 1.0 - OFFSET_QUAD_EPSILON_T)) {
                    t = 1.0;
                }
                Point[] before = splitQuadKeepingAfter(parts.part, t);
                offsetSimpleQuad(before[0], before[1], before[2]);
                if (t == 1.0) {
                    break;
                }
            }
        } while (parts.next());
    }

    private void offsetSimpleQuad(Point q0, Point q1, Point q2) {
        if (q0.isSameAs(q2)) {
            return;
        }
        Point v0 = q1.minus(q0);
        Point v1 = q2.minus(q1);
        Point m0 = (!q0.isSameAs(q1) ? v0 : v1).unit().normal();
        Point m2 = (!q1.isSameAs(q2) ? v1 : v0).unit().normal();
        p0 = q2;
        n0 = m2;
        Point m = m0.plus(m2);
        Point k1 = m.times(options.width()).dividedBy(m.lengthSquared());
        Point k2 = m2.times(distanceA);
        a.quadTo(q1.plus(k1), q2.plus(k2));
        b.quadTo(q1.minus(k1), q2.minus(k2));
    }

    private void offsetCubic(Point[] bezier) {
        double toleranceSquared = (54.0 * SIMPLIFY_TOLERANCE) * (54.0 * SIMPLIFY_TOLERANCE);
        double t = StrictMath.pow(toleranceSquared / cubicIdentity(bezier).lengthSquared(), 1.0 / 6.0);
        Point[] cubic = new Point[7];
        cubic[3] = bezier[0];
        cubic[4] = bezier[1];
        cubic[5] = bezier[2];
        cubic[6] = bezier[3];
        while (true) {
            t = t < 1.0 ? t : 1.0;
            if (t >= 0.999) {
                t = 1.0;
            }
            splitCubicAt(cubic, t);
            Point[] quads = approximateWithTwoQuads(new Point[] {cubic[0], cubic[1], cubic[2], cubic[3]});
            offsetQuad(new Point[] {quads[0], quads[1], quads[2]});
            offsetQuad(new Point[] {quads[2], quads[3], quads[4]});
            if (t >= 1.0) {
                return;
            }
            double oldT = t;
            t = t / (1.0 - t);
            if (oldT - t < 1e-3) {
                t += 0.01;
            }
        }
    }

    private void addCap(Blend2DPath out, Point pivot, Point p1, int cap) {
        Point start = out.pointFromEnd(1);
        Point q = p1.minus(start).normal().times(0.5);
        switch (cap) {
            case CAP_SQUARE -> {
                out.lineTo(start.plus(q));
                out.lineTo(p1.plus(q));
                out.lineTo(p1);
            }
            case CAP_ROUND -> {
                out.arcQuadrantTo(start.plus(q), pivot.plus(q));
                out.arcQuadrantTo(p1.plus(q), p1);
            }
            case CAP_ROUND_REVERSED -> {
                out.lineTo(start.plus(q));
                out.arcQuadrantTo(start, pivot);
                out.arcQuadrantTo(p1, p1.plus(q));
                out.lineTo(p1);
            }
            case CAP_TRIANGLE -> {
                out.lineTo(pivot.plus(q));
                out.lineTo(p1);
            }
            case CAP_TRIANGLE_REVERSED -> {
                out.lineTo(start.plus(q));
                out.lineTo(pivot);
                out.lineTo(p1.plus(q));
                out.lineTo(p1);
            }
            default -> out.lineTo(p1);
        }
    }

    private static Point evaluateQuad(Point[] p, double t) {
        Point v1 = p[1].minus(p[0]);
        Point v2 = p[2].minus(p[1]);
        Point qa = v2.minus(v1);
        Point qb = v1.plus(v1);
        return qa.times(t).plus(qb).times(t).plus(p[0]);
    }

    private static Point[] cubicCoefficients(Point[] p) {
        Point v1 = p[1].minus(p[0]);
        Point v2 = p[2].minus(p[1]);
        Point v3 = p[3].minus(p[2]);
        return new Point[] {v3.minus(v2).minus(v2).plus(v1), v2.minus(v1).times(3.0), v1.times(3.0), p[0]};
    }

    private static Point evaluateCubic(Point[] p, double t) {
        Point[] k = cubicCoefficients(p);
        return k[0].times(t).plus(k[1]).times(t).plus(k[2]).times(t).plus(k[3]);
    }

    private static Point cubicIdentity(Point[] p) {
        Point v1 = p[1].minus(p[0]);
        Point v2 = p[2].minus(p[1]);
        Point v3 = p[3].minus(p[2]);
        return v3.minus(v2).minus(v2).plus(v1);
    }

    private static boolean isCubicFlat(Point[] p, double f) {
        if (p[3].isSameAs(p[0])) {
            Point v = p[2].minus(p[1]);
            double area = v.cross(p[1].minus(p[0]));
            return 0.5625 * area * area <= f * f * v.lengthSquared();
        }
        Point v = p[3].minus(p[0]);
        double a1 = v.cross(p[1].minus(p[0]));
        double a2 = v.cross(p[2].minus(p[0]));
        double larger = a1 * a1 < a2 * a2 ? a2 * a2 : a1 * a1;
        return 0.5625 * larger <= f * f * v.lengthSquared();
    }

    private static Point cubicStartTangent(Point[] p) {
        Point out = p[1].minus(p[0]);
        Point t20 = p[2].minus(p[0]);
        Point t30 = p[3].minus(p[0]);
        if (out.isZero()) {
            out = t20;
        }
        if (out.isZero()) {
            out = t30;
        }
        return out;
    }

    private static Point cubicEndTangent(Point[] p) {
        Point out = p[3].minus(p[2]);
        Point t31 = p[3].minus(p[1]);
        Point t30 = p[3].minus(p[0]);
        if (out.isZero()) {
            out = t31;
        }
        if (out.isZero()) {
            out = t30;
        }
        return out;
    }

    private static Point[] cubicDerivativeCoefficients(Point[] p) {
        Point v1 = p[1].minus(p[0]);
        Point v2 = p[2].minus(p[1]);
        Point v3 = p[3].minus(p[2]);
        return new Point[] {v3.minus(v2).minus(v2).plus(v1).times(3.0), v2.minus(v1).times(6.0), v1.times(3.0)};
    }

    private static double[] cubicInflectionParameter(Point[] p) {
        Point[] k = cubicDerivativeCoefficients(p);
        double denominator = k[1].cross(k[0]);
        if (denominator != 0) {
            double tc = k[0].cross(k[2]) / denominator;
            double tl = tc * tc + k[1].cross(k[2]) / denominator;
            if (tl > 0) {
                tl = Math.sqrt(tl);
            }
            return new double[] {tc, tl};
        }
        return new double[] {-0.5 * k[2].cross(k[1]) / k[2].cross(k[0]), Double.NaN};
    }

    private static void splitCubicInHalfInPlace(Point[] p) {
        Point p01 = p[0].midway(p[1]);
        Point p12 = p[1].midway(p[2]);
        Point p23 = p[2].midway(p[3]);
        p[1] = p01;
        p[5] = p23;
        p[6] = p[3];
        p[2] = p01.midway(p12);
        p[4] = p12.midway(p23);
        p[3] = p[2].midway(p[4]);
    }

    private static void splitCubicAt(Point[] cubic, double t) {
        Point p01 = cubic[3].lerp(cubic[4], t);
        Point p12 = cubic[4].lerp(cubic[5], t);
        Point p23 = cubic[5].lerp(cubic[6], t);
        cubic[0] = cubic[3];
        cubic[1] = p01;
        cubic[5] = p23;
        cubic[2] = p01.lerp(p12, t);
        cubic[4] = p12.lerp(p23, t);
        cubic[3] = cubic[2].lerp(cubic[4], t);
    }

    private static Point cubicDerivativeAt(Point[] p, double t) {
        Point p01 = p[0].lerp(p[1], t);
        Point p12 = p[1].lerp(p[2], t);
        Point p23 = p[2].lerp(p[3], t);
        return p12.lerp(p23, t).minus(p01.lerp(p12, t)).times(3.0);
    }

    private static Point intersectLines(Point p0, Point v0, Point p1, Point v1) {
        return p0.plus(v0.times(p1.minus(p0).cross(v1) / v0.cross(v1)));
    }

    private static Point[] approximateWithTwoQuads(Point[] p) {
        Point c1 = p[0].lerp(p[1], 0.75);
        Point c2 = p[3].lerp(p[2], 0.75);
        Point pm = c1.midway(c2);
        if (c1.isSameAs(p[0])) {
            c1 = intersectLines(p[0], cubicStartTangent(p), pm, cubicDerivativeAt(p, 0.5));
        }
        if (c2.isSameAs(p[3])) {
            c2 = intersectLines(p[3], cubicEndTangent(p), pm, cubicDerivativeAt(p, 0.5));
        }
        return new Point[] {p[0], c1, pm, c2, p[3]};
    }

    private static Point[] quadDerivativeCoefficients(Point[] p) {
        Point v1 = p[1].minus(p[0]);
        Point v2 = p[2].minus(p[1]);
        return new Point[] {v2.times(2.0).minus(v1.times(2.0)), v1.times(2.0)};
    }

    private static int quadOffsetCusps(Point[] bezier, double d, double[] out) {
        Point[] k = quadDerivativeCoefficients(bezier);
        double bxa = k[1].cross(k[0]);
        double boa = k[1].dot(k[0]);
        if (bxa == 0) {
            return 0;
        }
        double aLengthSquared = k[0].lengthSquared();
        double bLengthSquared = k[1].lengthSquared();
        double factor = -1.0 / aLengthSquared;
        double root = Math.sqrt(boa * boa - aLengthSquared * (bLengthSquared - StrictMath.cbrt(d * d * bxa * bxa)));
        double t0 = factor * (boa + root);
        double t1 = factor * (boa - root);
        t0 = t0 < 0.0 ? 0.0 : t0;
        int count = t0 > 0.0 && t0 < 1.0 ? 1 : 0;
        out[0] = t0;
        out[count] = t1;
        return count + (t1 > t0 && t1 < 1.0 ? 1 : 0);
    }

    private static double quadParameterAtAngle(Point[] p, double m) {
        Point[] k = quadDerivativeCoefficients(p);
        double aob = k[0].dot(k[1]);
        double axb = k[0].cross(k[1]);
        if (aob == 0.0) {
            return 1.0;
        }
        return m * k[1].lengthSquared() / (Math.abs(axb) - m * aob);
    }

    private static Point[] splitQuadKeepingAfter(Point[] p, double t) {
        Point p01 = p[0].lerp(p[1], t);
        Point p12 = p[1].lerp(p[2], t);
        Point[] before = {p[0], p01, p01.lerp(p12, t)};
        p[1] = p12;
        p[0] = before[2];
        return before;
    }

    private static final class QuadPartsByT {

        private final Point[] input;

        private final double[] ts;

        private final int count;

        private int next;

        private final Point[] part = new Point[3];

        private Point between01;

        private Point between12;

        QuadPartsByT(Point[] input, double[] ts, int count) {
            this.input = input.clone();
            this.ts = ts;
            this.count = count;
            double t = ts[next++];
            between01 = this.input[0].lerp(this.input[1], t);
            between12 = this.input[1].lerp(this.input[2], t);
            part[0] = this.input[0];
            part[1] = between01;
            part[2] = part[1].lerp(between12, t);
        }

        boolean next() {
            if (next >= count) {
                return false;
            }
            double t = ts[next++];
            part[0] = part[2];
            part[1] = between01.lerp(between12, t);
            between01 = input[0].lerp(input[1], t);
            between12 = input[1].lerp(input[2], t);
            part[2] = between01.lerp(between12, t);
            return true;
        }
    }
}
