package org.jebol.domain.eval.blend2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class EdgeBuilder {

    private static final int CLIP_X0 = 1;

    private static final int CLIP_Y0 = 2;

    private static final int CLIP_X1 = 4;

    private static final int CLIP_Y1 = 8;

    private static final int CLIP_X0_Y0 = CLIP_X0 | CLIP_Y0;

    private static final int CLIP_X1_Y0 = CLIP_X1 | CLIP_Y0;

    private static final int CLIP_X0_Y1 = CLIP_X0 | CLIP_Y1;

    private static final int CLIP_X1_Y1 = CLIP_X1 | CLIP_Y1;

    private enum Step {
        TOP,
        UNCLIPPED,
        UNCLIPPED_LOOP,
        DESCENDING_BEGIN,
        DESCENDING_LOOP_A,
        DESCENDING_LOOP_B,
        ASCENDING_BEGIN,
        ASCENDING_LOOP_A,
        ASCENDING_LOOP_B,
        BEFORE_CLIP_END,
        RESTART_CLIP,
        CLIP_START,
        CLIP_END,
        NEXT,
        DONE,
        INVALID
    }

    record EdgeVector(int[] points, int signBit) {
    }

    private final double clipX0;

    private final double clipY0;

    private final double clipX1;

    private final double clipY1;

    private final int clipWholeX0;

    private final int clipWholeX1;

    private final List<EdgeVector> edges = new ArrayList<>();

    private int boundTop = Integer.MAX_VALUE;

    private int boundBottom = Integer.MIN_VALUE;

    private double leftBorderFrom;

    private double leftBorderTo;

    private double rightBorderFrom;

    private double rightBorderTo;

    private IntList building;

    private boolean isBuildingAscending;

    private double[] figure;

    private int figureNext;

    private double ax;

    private double ay;

    private int aFlags;

    private double bx;

    private double by;

    private int bFlags;

    private double px;

    private double py;

    private double dx;

    private double dy;

    private int fx0;

    private int fy0;

    private int fx1;

    private int fy1;

    private double borderFrom;

    private double borderTo;

    EdgeBuilder(Blend2DBox clipFixed) {
        clipX0 = clipFixed.x0();
        clipY0 = clipFixed.y0();
        clipX1 = clipFixed.x1();
        clipY1 = clipFixed.y1();
        clipWholeX0 = truncate(clipX0);
        clipWholeX1 = truncate(clipX1);
        leftBorderFrom = clipY0;
        leftBorderTo = clipY0;
        rightBorderFrom = clipY0;
        rightBorderTo = clipY0;
    }

    List<EdgeVector> edges() {
        return edges;
    }

    boolean isEmpty() {
        return edges.isEmpty() || boundTop >= boundBottom;
    }

    boolean addClosedFigure(double[] points) {
        if (points.length < 2) {
            return true;
        }
        figure = points;
        figureNext = 2;
        double startX = points[0];
        double startY = points[1];
        ax = startX;
        ay = startY;
        aFlags = calculateFlags(ax, ay);
        while (figureNext < figure.length) {
            bx = figure[figureNext];
            by = figure[figureNext + 1];
            figureNext += 2;
            if (!lineTo()) {
                return false;
            }
        }
        bx = startX;
        by = startY;
        return lineTo();
    }

    void finish() {
        emitLeftBorder();
        emitRightBorder();
    }

    private boolean takeNextIntoA() {
        if (figureNext >= figure.length) {
            return false;
        }
        ax = figure[figureNext];
        ay = figure[figureNext + 1];
        figureNext += 2;
        return true;
    }

    private boolean takeNextIntoB() {
        if (figureNext >= figure.length) {
            return false;
        }
        bx = figure[figureNext];
        by = figure[figureNext + 1];
        figureNext += 2;
        return true;
    }

    private void swapPoints() {
        double x = ax;
        double y = ay;
        ax = bx;
        ay = by;
        bx = x;
        by = y;
    }

    private boolean lineTo() {
        Step step = Step.TOP;
        while (true) {
            switch (step) {
                case TOP -> step = aFlags == 0 ? Step.UNCLIPPED : Step.RESTART_CLIP;
                case UNCLIPPED -> {
                    bFlags = calculateFlags(bx, by);
                    if (bFlags != 0) {
                        step = Step.BEFORE_CLIP_END;
                    } else {
                        fx0 = truncate(ax);
                        fy0 = truncate(ay);
                        fx1 = truncate(bx);
                        fy1 = truncate(by);
                        step = Step.UNCLIPPED_LOOP;
                    }
                }
                case UNCLIPPED_LOOP -> step = stepUnclipped();
                case DESCENDING_BEGIN -> {
                    openDescending();
                    building.add(fx0, fy0);
                    building.add(fx1, fy1);
                    boundTop = Math.min(boundTop, fy0);
                    step = Step.DESCENDING_LOOP_A;
                }
                case DESCENDING_LOOP_A -> step = stepDescendingIntoA();
                case DESCENDING_LOOP_B -> step = stepDescendingIntoB();
                case ASCENDING_BEGIN -> {
                    openAscending();
                    building.add(fx0, fy0);
                    building.add(fx1, fy1);
                    boundBottom = Math.max(boundBottom, fy0);
                    step = Step.ASCENDING_LOOP_A;
                }
                case ASCENDING_LOOP_A -> step = stepAscendingIntoA();
                case ASCENDING_LOOP_B -> step = stepAscendingIntoB();
                case BEFORE_CLIP_END -> {
                    px = ax;
                    py = ay;
                    dx = bx - ax;
                    dy = by - ay;
                    step = Step.CLIP_END;
                }
                case RESTART_CLIP -> step = stepClipped();
                case CLIP_START -> step = clipStartPoint();
                case CLIP_END -> step = clipEndPoint();
                case NEXT -> step = takeNextIntoB() ? Step.TOP : Step.DONE;
                case DONE -> {
                    return true;
                }
                case INVALID -> {
                    return false;
                }
            }
        }
    }

    private Step stepUnclipped() {
        if (fy0 < fy1) {
            return Step.DESCENDING_BEGIN;
        }
        if (fy0 > fy1) {
            return Step.ASCENDING_BEGIN;
        }
        ax = bx;
        ay = by;
        if (!takeNextIntoB()) {
            return Step.DONE;
        }
        bFlags = calculateFlags(bx, by);
        if (bFlags != 0) {
            return Step.BEFORE_CLIP_END;
        }
        fx0 = fx1;
        fy0 = fy1;
        fx1 = truncate(bx);
        fy1 = truncate(by);
        return Step.UNCLIPPED_LOOP;
    }

    private Step stepDescendingIntoA() {
        if (!takeNextIntoA()) {
            closeBuilding();
            boundBottom = Math.max(boundBottom, fy1);
            ax = bx;
            ay = by;
            return Step.DONE;
        }
        bFlags = calculateFlags(ax, ay);
        if (bFlags != 0) {
            closeBuilding();
            swapPoints();
            return Step.BEFORE_CLIP_END;
        }
        fx0 = truncate(ax);
        fy0 = truncate(ay);
        if (fy0 < fy1) {
            closeBuilding();
            openAscending();
            building.add(fx1, fy1);
            building.add(fx0, fy0);
            boundBottom = Math.max(boundBottom, fy1);
            return Step.ASCENDING_LOOP_B;
        }
        building.add(fx0, fy0);
        return Step.DESCENDING_LOOP_B;
    }

    private Step stepDescendingIntoB() {
        if (!takeNextIntoB()) {
            closeBuilding();
            boundBottom = Math.max(boundBottom, fy0);
            return Step.DONE;
        }
        bFlags = calculateFlags(bx, by);
        if (bFlags != 0) {
            closeBuilding();
            boundBottom = Math.max(boundBottom, fy0);
            return Step.BEFORE_CLIP_END;
        }
        fx1 = truncate(bx);
        fy1 = truncate(by);
        if (fy1 < fy0) {
            closeBuilding();
            openAscending();
            building.add(fx0, fy0);
            building.add(fx1, fy1);
            boundBottom = Math.max(boundBottom, fy0);
            return Step.ASCENDING_LOOP_A;
        }
        building.add(fx1, fy1);
        return Step.DESCENDING_LOOP_A;
    }

    private Step stepAscendingIntoA() {
        if (!takeNextIntoA()) {
            closeBuilding();
            boundTop = Math.min(boundTop, fy1);
            ax = bx;
            ay = by;
            return Step.DONE;
        }
        bFlags = calculateFlags(ax, ay);
        if (bFlags != 0) {
            closeBuilding();
            swapPoints();
            return Step.BEFORE_CLIP_END;
        }
        fx0 = truncate(ax);
        fy0 = truncate(ay);
        if (fy0 > fy1) {
            closeBuilding();
            openDescending();
            building.add(fx1, fy1);
            building.add(fx0, fy0);
            boundTop = Math.min(boundTop, fy1);
            return Step.DESCENDING_LOOP_B;
        }
        building.add(fx0, fy0);
        return Step.ASCENDING_LOOP_B;
    }

    private Step stepAscendingIntoB() {
        if (!takeNextIntoB()) {
            closeBuilding();
            boundTop = Math.min(boundTop, fy0);
            return Step.DONE;
        }
        bFlags = calculateFlags(bx, by);
        if (bFlags != 0) {
            closeBuilding();
            boundTop = Math.min(boundTop, fy0);
            return Step.BEFORE_CLIP_END;
        }
        fx1 = truncate(bx);
        fy1 = truncate(by);
        if (fy1 > fy0) {
            closeBuilding();
            openDescending();
            building.add(fx0, fy0);
            building.add(fx1, fy1);
            boundTop = Math.min(boundTop, fy0);
            return Step.DESCENDING_LOOP_A;
        }
        building.add(fx1, fy1);
        return Step.ASCENDING_LOOP_A;
    }

    private Step stepClipped() {
        if ((aFlags & CLIP_Y0) != 0) {
            while (!(clipY0 < by)) {
                ax = bx;
                ay = by;
                if (!takeNextIntoB()) {
                    aFlags = calculateXFlags(ax) | calculateY0Flag(ay);
                    return Step.DONE;
                }
            }
            aFlags = calculateXFlags(ax) | calculateY0Flag(ay);
            bFlags = calculateXFlags(bx) | calculateY1Flag(by);
            borderFrom = clipY0;
            int common = aFlags & bFlags;
            if (common != 0) {
                borderTo = minimum(clipY1, by);
                accumulateBorder((common & CLIP_X0) != 0, borderFrom, borderTo);
                ax = bx;
                ay = by;
                aFlags = bFlags;
                return Step.NEXT;
            }
            return Step.CLIP_START;
        }
        if ((aFlags & CLIP_Y1) != 0) {
            while (!(clipY1 > by)) {
                ax = bx;
                ay = by;
                if (!takeNextIntoB()) {
                    aFlags = calculateXFlags(ax) | calculateY1Flag(ay);
                    return Step.DONE;
                }
            }
            aFlags = calculateXFlags(ax) | calculateY1Flag(ay);
            bFlags = calculateXFlags(bx) | calculateY0Flag(by);
            borderFrom = clipY1;
            int common = aFlags & bFlags;
            if (common != 0) {
                borderTo = maximum(clipY0, by);
                accumulateBorder((common & CLIP_X0) != 0, borderFrom, borderTo);
                ax = bx;
                ay = by;
                aFlags = bFlags;
                return Step.NEXT;
            }
            return Step.CLIP_START;
        }
        boolean isLeft = (aFlags & CLIP_X0) != 0;
        borderFrom = clampToClipRows(ay);
        while (isLeft ? !(clipX0 < bx) : !(clipX1 > bx)) {
            ax = bx;
            ay = by;
            if (!takeNextIntoB()) {
                aFlags = calculateYFlags(ay) | (isLeft ? calculateX0Flag(ax) : calculateX1Flag(ax));
                borderTo = clampToClipRows(ay);
                if (borderFrom != borderTo) {
                    accumulateBorder(isLeft, borderFrom, borderTo);
                }
                return Step.DONE;
            }
        }
        borderTo = clampToClipRows(ay);
        if (borderFrom != borderTo) {
            accumulateBorder(isLeft, borderFrom, borderTo);
        }
        aFlags = (isLeft ? calculateX0Flag(ax) : calculateX1Flag(ax)) | calculateYFlags(ay);
        bFlags = (isLeft ? calculateX1Flag(bx) : calculateX0Flag(bx)) | calculateYFlags(by);
        if ((aFlags & bFlags) != 0) {
            return Step.RESTART_CLIP;
        }
        borderFrom = borderTo;
        return Step.CLIP_START;
    }

    private Step clipStartPoint() {
        dx = bx - ax;
        dy = by - ay;
        px = clipX1;
        py = clipY1;
        switch (aFlags) {
            case 0 -> {
                px = ax;
                py = ay;
            }
            case CLIP_X0_Y0, CLIP_X1_Y0 -> {
                if (aFlags == CLIP_X0_Y0) {
                    px = clipX0;
                }
                py = ay + (px - ax) * dy / dx;
                aFlags = calculateYFlags(py);
                if (!(py >= clipY0)) {
                    clipStartToTop();
                }
            }
            case CLIP_Y0 -> clipStartToTop();
            case CLIP_X0_Y1, CLIP_X1_Y1 -> {
                if (aFlags == CLIP_X0_Y1) {
                    px = clipX0;
                }
                py = ay + (px - ax) * dy / dx;
                aFlags = calculateYFlags(py);
                if (!(py <= clipY1)) {
                    clipStartToBottom();
                }
            }
            case CLIP_Y1 -> clipStartToBottom();
            case CLIP_X0, CLIP_X1 -> {
                if (aFlags == CLIP_X0) {
                    px = clipX0;
                }
                py = ay + (px - ax) * dy / dx;
                aFlags = calculateYFlags(py);
            }
            default -> {
                return Step.INVALID;
            }
        }
        if (aFlags != 0) {
            borderTo = clampToClipRows(by);
            if (px <= clipX0) {
                accumulateBorder(true, borderFrom, borderTo);
            } else if (px >= clipX1) {
                accumulateBorder(false, borderFrom, borderTo);
            }
            ax = bx;
            ay = by;
            aFlags = bFlags;
            return Step.NEXT;
        }
        borderTo = clampToClipRows(py);
        if (borderFrom != borderTo) {
            accumulateBorder(px <= clipX0, borderFrom, borderTo);
        }
        if (bFlags == 0) {
            ax = bx;
            ay = by;
            aFlags = 0;
            fx0 = truncate(px);
            fy0 = truncate(py);
            fx1 = truncate(bx);
            fy1 = truncate(by);
            if (fy0 == fy1) {
                return Step.NEXT;
            }
            return fy0 < fy1 ? Step.DESCENDING_BEGIN : Step.ASCENDING_BEGIN;
        }
        return Step.CLIP_END;
    }

    private void clipStartToTop() {
        py = clipY0;
        px = ax + (py - ay) * dx / dy;
        aFlags = calculateXFlags(px);
    }

    private void clipStartToBottom() {
        py = clipY1;
        px = ax + (py - ay) * dx / dy;
        aFlags = calculateXFlags(px);
    }

    private Step clipEndPoint() {
        double qx = clipX1;
        double qy;
        switch (bFlags) {
            case CLIP_X0_Y0, CLIP_X1_Y0 -> {
                if (bFlags == CLIP_X0_Y0) {
                    qx = clipX0;
                }
                qy = ay + (qx - ax) * dy / dx;
                if (!(qy >= clipY0)) {
                    qy = clipY0;
                    qx = ax + (qy - ay) * dx / dy;
                }
            }
            case CLIP_Y0 -> {
                qy = clipY0;
                qx = ax + (qy - ay) * dx / dy;
            }
            case CLIP_X0_Y1, CLIP_X1_Y1 -> {
                if (bFlags == CLIP_X0_Y1) {
                    qx = clipX0;
                }
                qy = ay + (qx - ax) * dy / dx;
                if (!(qy <= clipY1)) {
                    qy = clipY1;
                    qx = ax + (qy - ay) * dx / dy;
                }
            }
            case CLIP_Y1 -> {
                qy = clipY1;
                qx = ax + (qy - ay) * dx / dy;
            }
            case CLIP_X0, CLIP_X1 -> {
                if (bFlags == CLIP_X0) {
                    qx = clipX0;
                }
                qy = ay + (qx - ax) * dy / dx;
            }
            default -> {
                return Step.INVALID;
            }
        }
        addLineSegment(px, py, qx, qy);
        double clippedBy = clampToClipRows(by);
        if (qy != clippedBy) {
            accumulateBorder(qx == clipX0, qy, clippedBy);
        }
        ax = bx;
        ay = by;
        aFlags = bFlags;
        return Step.NEXT;
    }

    private void addLineSegment(double x0, double y0, double x1, double y1) {
        int wholeX0 = truncate(x0);
        int wholeY0 = truncate(y0);
        int wholeX1 = truncate(x1);
        int wholeY1 = truncate(y1);
        if (wholeY0 == wholeY1) {
            return;
        }
        if (wholeY0 < wholeY1) {
            boundTop = Math.min(boundTop, wholeY0);
            boundBottom = Math.max(boundBottom, wholeY1);
            edges.add(new EdgeVector(new int[] {wholeX0, wholeY0, wholeX1, wholeY1}, 0));
        } else {
            boundTop = Math.min(boundTop, wholeY1);
            boundBottom = Math.max(boundBottom, wholeY0);
            edges.add(new EdgeVector(new int[] {wholeX1, wholeY1, wholeX0, wholeY0}, 1));
        }
    }

    private void openDescending() {
        building = new IntList();
        isBuildingAscending = false;
    }

    private void openAscending() {
        building = new IntList();
        isBuildingAscending = true;
    }

    private void closeBuilding() {
        int[] points = building.toArray();
        if (isBuildingAscending) {
            reversePoints(points);
            edges.add(new EdgeVector(points, 1));
        } else {
            edges.add(new EdgeVector(points, 0));
        }
        building = null;
    }

    private static void reversePoints(int[] points) {
        for (int low = 0, high = points.length - 2; low < high; low += 2, high -= 2) {
            int x = points[low];
            int y = points[low + 1];
            points[low] = points[high];
            points[low + 1] = points[high + 1];
            points[high] = x;
            points[high + 1] = y;
        }
    }

    private void accumulateBorder(boolean isLeft, double from, double to) {
        if (isLeft) {
            if (leftBorderTo == from) {
                leftBorderTo = to;
                return;
            }
            emitLeftBorder();
            leftBorderFrom = from;
            leftBorderTo = to;
        } else {
            if (rightBorderTo == from) {
                rightBorderTo = to;
                return;
            }
            emitRightBorder();
            rightBorderFrom = from;
            rightBorderTo = to;
        }
    }

    private void emitLeftBorder() {
        emitBorder(clipWholeX0, leftBorderFrom, leftBorderTo);
    }

    private void emitRightBorder() {
        emitBorder(clipWholeX1, rightBorderFrom, rightBorderTo);
    }

    private void emitBorder(int x, double from, double to) {
        int wholeFrom = truncate(from);
        int wholeTo = truncate(to);
        if (wholeFrom == wholeTo) {
            return;
        }
        int top = Math.min(wholeFrom, wholeTo);
        int bottom = Math.max(wholeFrom, wholeTo);
        boundTop = Math.min(boundTop, top);
        boundBottom = Math.max(boundBottom, bottom);
        edges.add(new EdgeVector(new int[] {x, top, x, bottom}, wholeFrom > wholeTo ? 1 : 0));
    }

    private double clampToClipRows(double y) {
        return minimum(clipY1, maximum(clipY0, y));
    }

    private static double minimum(double a, double b) {
        return b < a ? b : a;
    }

    private static double maximum(double a, double b) {
        return a < b ? b : a;
    }

    private int calculateFlags(double x, double y) {
        return calculateXFlags(x) | calculateYFlags(y);
    }

    private int calculateXFlags(double x) {
        return calculateX0Flag(x) | calculateX1Flag(x);
    }

    private int calculateYFlags(double y) {
        return calculateY0Flag(y) | calculateY1Flag(y);
    }

    private int calculateX0Flag(double x) {
        return x >= clipX0 ? 0 : CLIP_X0;
    }

    private int calculateX1Flag(double x) {
        return x <= clipX1 ? 0 : CLIP_X1;
    }

    private int calculateY0Flag(double y) {
        return y >= clipY0 ? 0 : CLIP_Y0;
    }

    private int calculateY1Flag(double y) {
        return y <= clipY1 ? 0 : CLIP_Y1;
    }

    static int truncate(double value) {
        if (!(value >= Integer.MIN_VALUE && value < 2147483648.0)) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private static final class IntList {

        private int[] values = new int[16];

        private int size;

        void add(int x, int y) {
            if (size + 2 > values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = x;
            values[size++] = y;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
