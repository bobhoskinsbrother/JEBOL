package org.jebol.domain.render;

/**
 * An affine transform, as the six numbers both toolkits take.
 *
 * <p>Carried on an instruction rather than baked into its coordinates, because
 * a stroke is not a stroke of a transformed path: under a non-uniform scale a
 * baked path draws a line of even width where the toolkit draws one that
 * thickens.
 *
 * <p>{@code AffineTransform} and a canvas {@code setTransform} take them
 * in the same order and mean the same thing by them.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public record Transform(
        double acrossScale, double downSkew,
        double acrossSkew, double downScale,
        double acrossMove, double downMove) {

    /** No transform at all: what a draw block starts with. */
    public static final Transform NONE = new Transform(1, 0, 0, 1, 0, 0);

    public boolean isNone() {
        return equals(NONE);
    }

    /** This transform followed by another, as one. */
    public Transform combinedWith(Transform then) {
        return new Transform(
                acrossScale * then.acrossScale + acrossSkew * then.downSkew,
                downSkew * then.acrossScale + downScale * then.downSkew,
                acrossScale * then.acrossSkew + acrossSkew * then.downScale,
                downSkew * then.acrossSkew + downScale * then.downScale,
                acrossScale * then.acrossMove + acrossSkew * then.downMove + acrossMove,
                downSkew * then.acrossMove + downScale * then.downMove + downMove);
    }

    /**
     * The transform that undoes this one, or this one where there is none.
     *
     * <p>A transform that squashes everything onto a line has no undoing, and
     * {@code scale 0 1} is the ordinary way to write one by accident. The C
     * divides by the determinant without looking; dividing by zero here would
     * put infinities into every coordinate that followed.
     */
    public Transform inverted() {
        double determinant = acrossScale * downScale - downSkew * acrossSkew;
        if (determinant == 0 || !Double.isFinite(determinant)) {
            return this;
        }
        return new Transform(
                downScale / determinant,
                -downSkew / determinant,
                -acrossSkew / determinant,
                acrossScale / determinant,
                (acrossSkew * downMove - downScale * acrossMove) / determinant,
                (downSkew * acrossMove - acrossScale * downMove) / determinant);
    }

    public static Transform movedBy(double across, double down) {
        return new Transform(1, 0, 0, 1, across, down);
    }

    public static Transform scaledBy(double across, double down) {
        return new Transform(across, 0, 0, down, 0, 0);
    }

    /** Clockwise, in degrees, which is how the dialect writes an angle. */
    public static Transform turnedBy(double degrees) {
        double turn = Math.toRadians(degrees);
        return new Transform(
                Math.cos(turn), Math.sin(turn),
                -Math.sin(turn), Math.cos(turn), 0, 0);
    }

    /** Skewed by an angle in each direction, in degrees. */
    public static Transform skewedBy(double across, double down) {
        return new Transform(
                1, Math.tan(Math.toRadians(down)),
                Math.tan(Math.toRadians(across)), 1, 0, 0);
    }
}
