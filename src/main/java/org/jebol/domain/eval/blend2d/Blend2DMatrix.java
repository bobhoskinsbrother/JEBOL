package org.jebol.domain.eval.blend2d;

record Blend2DMatrix(double m00, double m01, double m10, double m11, double m20, double m21) {

    static final Blend2DMatrix IDENTITY = new Blend2DMatrix(1, 0, 0, 1, 0, 0);

    private static final double FIXED_POINT_SCALE = 256.0;

    enum Kind {
        IDENTITY,
        TRANSLATE,
        SCALE,
        SWAP,
        AFFINE,
        INVALID
    }

    Blend2DMatrix postTranslate(double x, double y) {
        return new Blend2DMatrix(m00, m01, m10, m11, m20 + x, m21 + y);
    }

    Blend2DMatrix postScale(double x, double y) {
        return new Blend2DMatrix(m00 * x, m01 * y, m10 * x, m11 * y, m20 * x, m21 * y);
    }

    Blend2DMatrix postRotate(double angle) {
        double sine = StrictMath.sin(angle);
        double cosine = StrictMath.cos(angle);
        return rotatedBy(sine, cosine);
    }

    Blend2DMatrix postRotateAbout(double angle, double x, double y) {
        double sine = StrictMath.sin(angle);
        double cosine = StrictMath.cos(angle);
        Blend2DMatrix turned = rotatedBy(sine, cosine);
        return new Blend2DMatrix(turned.m00, turned.m01, turned.m10, turned.m11,
                turned.m20 + x - cosine * x + sine * y,
                turned.m21 + y - sine * x - cosine * y);
    }

    private Blend2DMatrix rotatedBy(double sine, double cosine) {
        return new Blend2DMatrix(
                m00 * cosine - m01 * sine,
                m00 * sine + m01 * cosine,
                m10 * cosine - m11 * sine,
                m10 * sine + m11 * cosine,
                m20 * cosine - m21 * sine,
                m20 * sine + m21 * cosine);
    }

    Blend2DMatrix toFixedPoint() {
        return postScale(FIXED_POINT_SCALE, FIXED_POINT_SCALE);
    }

    Kind kind() {
        int nonZero = (m00 != 0 ? 8 : 0) | (m01 != 0 ? 4 : 0) | (m10 != 0 ? 2 : 0) | (m11 != 0 ? 1 : 0);
        boolean isShapeValid = (VALID_SHAPES >>> nonZero & 1) != 0;
        double determinant = m00 * m11 - m01 * m10;
        if (!isShapeValid || !Double.isFinite(determinant) || !Double.isFinite(m20) || !Double.isFinite(m21)) {
            return Kind.INVALID;
        }
        if (nonZero != (8 | 1)) {
            return nonZero == (4 | 2) ? Kind.SWAP : Kind.AFFINE;
        }
        if (!(m00 == 1.0 && m11 == 1.0)) {
            return Kind.SCALE;
        }
        if (!(m20 == 0.0 && m21 == 0.0)) {
            return Kind.TRANSLATE;
        }
        return Kind.IDENTITY;
    }

    private static final int VALID_SHAPES = 1 << 3 | 1 << 6 | 1 << 7 | 1 << 9 | 1 << 11
            | 1 << 12 | 1 << 13 | 1 << 14 | 1 << 15;

    Blend2DBox mapBoxScaledOrSwapped(Blend2DBox box) {
        double x0 = box.x0() * m00 + box.y0() * m10 + m20;
        double y0 = box.x0() * m01 + box.y0() * m11 + m21;
        double x1 = box.x1() * m00 + box.y1() * m10 + m20;
        double y1 = box.x1() * m01 + box.y1() * m11 + m21;
        return new Blend2DBox(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1));
    }

    Blend2DBox mapBox(Blend2DBox box) {
        double x0a = box.x0() * m00;
        double y0a = box.y0() * m10;
        double x1a = box.x1() * m00;
        double y1a = box.y1() * m10;
        double x0b = box.x0() * m01;
        double y0b = box.y0() * m11;
        double x1b = box.x1() * m01;
        double y1b = box.y1() * m11;
        return new Blend2DBox(
                Math.min(x0a, x1a) + Math.min(y0a, y1a) + m20,
                Math.min(x0b, x1b) + Math.min(y0b, y1b) + m21,
                Math.max(x0a, x1a) + Math.max(y0a, y1a) + m20,
                Math.max(x0b, x1b) + Math.max(y0b, y1b) + m21);
    }
}
