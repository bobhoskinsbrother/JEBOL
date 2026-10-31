package org.jebol.domain.eval.blend2d;

final class MonoCubic implements MonoCurve {

    private static final int RECURSION_LIMIT = 32;

    private static final int STACK_POINTS = RECURSION_LIMIT * 4;

    private final double toleranceSquared;

    private final double[] stack = new double[STACK_POINTS * 2];

    private int stackSize;

    private double x0;

    private double y0;

    private double x1;

    private double y1;

    private double x2;

    private double y2;

    private double x3;

    private double y3;

    private double x01;

    private double y01;

    private double x12;

    private double y12;

    private double x23;

    private double y23;

    private double x012;

    private double y012;

    private double x123;

    private double y123;

    private double x0123;

    private double y0123;

    MonoCubic(double[] spline, int at, int signBit, double toleranceSquared) {
        this.toleranceSquared = toleranceSquared;
        if (signBit == 0) {
            x0 = spline[at];
            y0 = spline[at + 1];
            x1 = spline[at + 2];
            y1 = spline[at + 3];
            x2 = spline[at + 4];
            y2 = spline[at + 5];
            x3 = spline[at + 6];
            y3 = spline[at + 7];
        } else {
            x0 = spline[at + 6];
            y0 = spline[at + 7];
            x1 = spline[at + 4];
            y1 = spline[at + 5];
            x2 = spline[at + 2];
            y2 = spline[at + 3];
            x3 = spline[at];
            y3 = spline[at + 1];
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
        return x3;
    }

    @Override
    public double lastY() {
        return y3;
    }

    @Override
    public double midX() {
        return x0123;
    }

    @Override
    public double midY() {
        return y0123;
    }

    @Override
    public boolean isLeftToRight() {
        return x0 < x3;
    }

    @Override
    public void boundLeftToRight() {
        x1 = clamp(x1, x0, x3);
        y1 = clamp(y1, y0, y3);
        x2 = clamp(x2, x0, x3);
        y2 = clamp(y2, y0, y3);
    }

    @Override
    public void boundRightToLeft() {
        x1 = clamp(x1, x3, x0);
        y1 = clamp(y1, y0, y3);
        x2 = clamp(x2, x3, x0);
        y2 = clamp(y2, y0, y3);
    }

    private static double clamp(double value, double low, double high) {
        double raised = low < value ? value : low;
        return raised < high ? raised : high;
    }

    @Override
    public boolean isFlat() {
        double vx = x3 - x0;
        double vy = y3 - y0;
        double cross1 = vx * (y1 - y0) - vy * (x1 - x0);
        double cross2 = vx * (y2 - y0) - vy * (x2 - x0);
        double d1Squared = cross1 * cross1;
        double d2Squared = cross2 * cross2;
        double lengthSquared = vx * vx + vy * vy;
        double value = d1Squared < d2Squared ? d2Squared : d1Squared;
        return value <= toleranceSquared * lengthSquared;
    }

    @Override
    public void split() {
        x01 = (x0 + x1) * 0.5;
        y01 = (y0 + y1) * 0.5;
        x12 = (x1 + x2) * 0.5;
        y12 = (y1 + y2) * 0.5;
        x23 = (x2 + x3) * 0.5;
        y23 = (y2 + y3) * 0.5;
        x012 = (x01 + x12) * 0.5;
        y012 = (y01 + y12) * 0.5;
        x123 = (x12 + x23) * 0.5;
        y123 = (y12 + y23) * 0.5;
        x0123 = (x012 + x123) * 0.5;
        y0123 = (y012 + y123) * 0.5;
    }

    @Override
    public boolean canPush() {
        return stackSize != STACK_POINTS;
    }

    @Override
    public void push() {
        int at = stackSize * 2;
        stack[at] = x0123;
        stack[at + 1] = y0123;
        stack[at + 2] = x123;
        stack[at + 3] = y123;
        stack[at + 4] = x23;
        stack[at + 5] = y23;
        stack[at + 6] = x3;
        stack[at + 7] = y3;
        stackSize += 4;
        x1 = x01;
        y1 = y01;
        x2 = x012;
        y2 = y012;
        x3 = x0123;
        y3 = y0123;
    }

    @Override
    public boolean canPop() {
        return stackSize != 0;
    }

    @Override
    public void pop() {
        stackSize -= 4;
        int at = stackSize * 2;
        x0 = stack[at];
        y0 = stack[at + 1];
        x1 = stack[at + 2];
        y1 = stack[at + 3];
        x2 = stack[at + 4];
        y2 = stack[at + 5];
        x3 = stack[at + 6];
        y3 = stack[at + 7];
    }

    @Override
    public void discardAndAdvance() {
        x0 = x0123;
        y0 = y0123;
        x1 = x123;
        y1 = y123;
        x2 = x23;
        y2 = y23;
    }
}
