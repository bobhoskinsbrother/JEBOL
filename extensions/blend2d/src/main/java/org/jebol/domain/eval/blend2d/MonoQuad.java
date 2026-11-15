package org.jebol.domain.eval.blend2d;

final class MonoQuad implements MonoCurve {

    private static final int STACK_POINTS = 32 * 3;

    private final double toleranceSquared;

    private final double[] stack = new double[STACK_POINTS * 2];

    private int stackSize;

    private double x0;

    private double y0;

    private double x1;

    private double y1;

    private double x2;

    private double y2;

    private double x01;

    private double y01;

    private double x12;

    private double y12;

    private double x012;

    private double y012;

    MonoQuad(double[] spline, int at, int signBit, double toleranceSquared) {
        this.toleranceSquared = toleranceSquared;
        x1 = spline[at + 2];
        y1 = spline[at + 3];
        if (signBit == 0) {
            x0 = spline[at];
            y0 = spline[at + 1];
            x2 = spline[at + 4];
            y2 = spline[at + 5];
        } else {
            x0 = spline[at + 4];
            y0 = spline[at + 5];
            x2 = spline[at];
            y2 = spline[at + 1];
        }
    }

    @Override
    public double firstX() {
        return x0;
    }

    @Override
    public double firstY() {
        return y0;
    }

    @Override
    public double lastX() {
        return x2;
    }

    @Override
    public double lastY() {
        return y2;
    }

    @Override
    public double midX() {
        return x012;
    }

    @Override
    public double midY() {
        return y012;
    }

    @Override
    public boolean isLeftToRight() {
        return x0 < x2;
    }

    @Override
    public void boundLeftToRight() {
        x1 = clamp(x1, x0, x2);
        y1 = clamp(y1, y0, y2);
    }

    @Override
    public void boundRightToLeft() {
        x1 = clamp(x1, x2, x0);
        y1 = clamp(y1, y0, y2);
    }

    private static double clamp(double value, double low, double high) {
        double raised = low < value ? value : low;
        return raised < high ? raised : high;
    }

    @Override
    public boolean isFlat() {
        double v1x = x1 - x0;
        double v1y = y1 - y0;
        double v2x = x2 - x0;
        double v2y = y2 - y0;
        double d = v2x * v1y - v2y * v1x;
        double lengthSquared = v2x * v2x + v2y * v2y;
        return d * d <= toleranceSquared * lengthSquared;
    }

    @Override
    public void split() {
        x01 = (x0 + x1) * 0.5;
        y01 = (y0 + y1) * 0.5;
        x12 = (x1 + x2) * 0.5;
        y12 = (y1 + y2) * 0.5;
        x012 = (x01 + x12) * 0.5;
        y012 = (y01 + y12) * 0.5;
    }

    @Override
    public boolean canPush() {
        return stackSize != STACK_POINTS;
    }

    @Override
    public void push() {
        int at = stackSize * 2;
        stack[at] = x012;
        stack[at + 1] = y012;
        stack[at + 2] = x12;
        stack[at + 3] = y12;
        stack[at + 4] = x2;
        stack[at + 5] = y2;
        stackSize += 3;
        x1 = x01;
        y1 = y01;
        x2 = x012;
        y2 = y012;
    }

    @Override
    public boolean canPop() {
        return stackSize != 0;
    }

    @Override
    public void pop() {
        stackSize -= 3;
        int at = stackSize * 2;
        x0 = stack[at];
        y0 = stack[at + 1];
        x1 = stack[at + 2];
        y1 = stack[at + 3];
        x2 = stack[at + 4];
        y2 = stack[at + 5];
    }

    @Override
    public void discardAndAdvance() {
        x0 = x012;
        y0 = y012;
        x1 = x12;
        y1 = y12;
    }
}
