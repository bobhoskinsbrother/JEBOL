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

    private static final double FLATTEN_TOLERANCE = 0.2 * 256.0;

    private static final double FLATTEN_TOLERANCE_SQUARED = FLATTEN_TOLERANCE * FLATTEN_TOLERANCE;

    private static final double ONE_FIXED_POINT_STEP = 0.00390625;

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

    private Blend2DPath path;

    private int cursor;

    private double flatteningStart;

    private int flatteningOut;

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

    boolean addFilledPath(Blend2DPath mapped) {
        path = mapped;
        cursor = 0;
        while (beginFigure()) {
            double startX = ax;
            double startY = ay;
            aFlags = calculateFlags(ax, ay);
            while (true) {
                if (isLineNext()) {
                    takeNextIntoB();
                    if (!lineTo()) {
                        return false;
                    }
                } else if (isCubicNext()) {
                    cubicTo();
                } else {
                    bx = startX;
                    by = startY;
                    if (!lineTo()) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }

    void finish() {
        emitLeftBorder();
        emitRightBorder();
    }

    private boolean beginFigure() {
        while (cursor < path.size()) {
            byte command = path.commandAt(cursor);
            cursor++;
            if (command == Blend2DPath.MOVE) {
                ax = path.xAt(cursor - 1);
                ay = path.yAt(cursor - 1);
                return true;
            }
        }
        return false;
    }

    private boolean isLineNext() {
        return cursor < path.size() && path.commandAt(cursor) == Blend2DPath.LINE;
    }

    private boolean isCubicNext() {
        return cursor < path.size() - 2 && path.commandAt(cursor) == Blend2DPath.CUBIC;
    }

    private boolean takeNextIntoA() {
        if (!isLineNext()) {
            return false;
        }
        ax = path.xAt(cursor);
        ay = path.yAt(cursor);
        cursor++;
        return true;
    }

    private boolean takeNextIntoB() {
        if (!isLineNext()) {
            return false;
        }
        bx = path.xAt(cursor);
        by = path.yAt(cursor);
        cursor++;
        return true;
    }

    private void takeNextCubic(double[] controls) {
        for (int at = 0; at < 3; at++) {
            controls[2 * at] = path.xAt(cursor + at);
            controls[2 * at + 1] = path.yAt(cursor + at);
        }
        cursor += 3;
    }

    private void cubicTo() {
        double[] controls = new double[6];
        takeNextCubic(controls);
        while (true) {
            int p1Flags = calculateFlags(controls[0], controls[1]);
            int p2Flags = calculateFlags(controls[2], controls[3]);
            int p3Flags = calculateFlags(controls[4], controls[5]);
            int common = aFlags & p1Flags & p2Flags & p3Flags;
            if (common != 0) {
                boolean isEnd = rejectCubicsOutside(common, controls);
                aFlags = calculateFlags(ax, ay);
                if (isEnd) {
                    return;
                }
                continue;
            }
            double[] spline = CubicSpline.split(ax, ay, controls);
            boolean isClipped = (aFlags | p1Flags | p2Flags | p3Flags) != 0;
            for (int at = 0; at + 6 < spline.length; at += 6) {
                int signBit = spline[at + 1] > spline[at + 7] ? 1 : 0;
                MonoCubic curve = new MonoCubic(spline, at, signBit, FLATTEN_TOLERANCE_SQUARED);
                if (isClipped) {
                    flattenClippedCurve(curve, signBit);
                } else {
                    flattenCurve(curve, signBit);
                }
            }
            ax = spline[spline.length - 2];
            ay = spline[spline.length - 1];
            if (isClipped) {
                aFlags = p3Flags;
            }
            if (!isCubicNext()) {
                return;
            }
            takeNextCubic(controls);
        }
    }

    private boolean rejectCubicsOutside(int common, double[] controls) {
        if ((common & CLIP_Y0) != 0) {
            while (true) {
                ax = controls[4];
                ay = controls[5];
                if (!isCubicNext()) {
                    return true;
                }
                takeNextCubic(controls);
                if (!(controls[1] <= clipY0 & controls[3] <= clipY0 & controls[5] <= clipY0)) {
                    return false;
                }
            }
        }
        if ((common & CLIP_Y1) != 0) {
            while (true) {
                ax = controls[4];
                ay = controls[5];
                if (!isCubicNext()) {
                    return true;
                }
                takeNextCubic(controls);
                if (!(controls[1] >= clipY1 & controls[3] >= clipY1 & controls[5] >= clipY1)) {
                    return false;
                }
            }
        }
        boolean isLeft = (common & CLIP_X0) != 0;
        double from = clampToClipRows(ay);
        boolean isEnd;
        while (true) {
            ax = controls[4];
            ay = controls[5];
            if (!isCubicNext()) {
                isEnd = true;
                break;
            }
            takeNextCubic(controls);
            boolean isStillOutside = isLeft
                    ? controls[0] <= clipX0 & controls[2] <= clipX0 & controls[4] <= clipX0
                    : controls[0] >= clipX1 & controls[2] >= clipX1 & controls[4] >= clipX1;
            if (!isStillOutside) {
                isEnd = false;
                break;
            }
        }
        accumulateBorder(isLeft, from, clampToClipRows(ay));
        return isEnd;
    }

    private void openAppender(double x, double y) {
        openDescending();
        building.add(truncate(x), truncate(y));
    }

    private void appendLine(double x, double y) {
        building.add(truncate(x), truncate(y));
    }

    private void closeAppender(int signBit) {
        int[] points = building.toArray();
        building = null;
        int top = points[1];
        int bottom = points[points.length - 1];
        if (top == bottom) {
            return;
        }
        boundTop = Math.min(boundTop, top);
        boundBottom = Math.max(boundBottom, bottom);
        edges.add(new EdgeVector(points, signBit));
    }

    private void flattenCurve(MonoCurve curve, int signBit) {
        if (curve.isLeftToRight()) {
            curve.boundLeftToRight();
        } else {
            curve.boundRightToLeft();
        }
        openAppender(curve.firstX(), curve.firstY());
        while (true) {
            if (!curve.isFlat() && curve.canPush()) {
                curve.split();
                curve.push();
                continue;
            }
            appendLine(curve.lastX(), curve.lastY());
            if (!curve.canPop()) {
                break;
            }
            curve.pop();
        }
        closeAppender(signBit);
    }

    private enum Region {
        ABOVE,
        BEFORE,
        BEFORE_AT_CLIP,
        BEFORE_AT_POP,
        VISIBLE,
        VISIBLE_AT_ADD_LINE,
        FINISHED
    }

    private void flattenClippedCurve(MonoCurve curve, int signBit) {
        flatteningStart = curve.firstY();
        double yEnd = minimum(curve.lastY(), clipY1);
        if (flatteningStart >= yEnd | yEnd <= clipY0) {
            return;
        }
        double xDelta = Math.abs(curve.firstX() - curve.lastX());
        flatteningOut = 0;
        if (xDelta <= ONE_FIXED_POINT_STEP) {
            flattenStraightCurve(curve, yEnd, signBit);
            return;
        }
        boolean isLeftToRight = curve.isLeftToRight();
        if (isLeftToRight) {
            curve.boundLeftToRight();
        } else {
            curve.boundRightToLeft();
        }
        Region region;
        if (flatteningStart < clipY0) {
            flatteningStart = clipY0;
            region = Region.ABOVE;
        } else if (flatteningStart < clipY1) {
            boolean isBefore = isLeftToRight ? curve.firstX() < clipX0 : curve.firstX() > clipX1;
            boolean isVisible = isLeftToRight ? curve.firstX() < clipX1 : curve.firstX() > clipX0;
            if (isBefore) {
                region = Region.BEFORE;
            } else if (isVisible) {
                openAppender(curve.firstX(), curve.firstY());
                region = Region.VISIBLE;
            } else {
                flatteningOut = isLeftToRight ? CLIP_X1 : CLIP_X0;
                region = Region.FINISHED;
            }
        } else {
            return;
        }
        while (region != Region.FINISHED) {
            region = switch (region) {
                case ABOVE -> flattenAbove(curve, isLeftToRight);
                case BEFORE, BEFORE_AT_CLIP, BEFORE_AT_POP -> flattenBefore(curve, isLeftToRight, region, yEnd, signBit);
                case VISIBLE, VISIBLE_AT_ADD_LINE -> flattenVisible(curve, isLeftToRight, region, yEnd, signBit);
                case FINISHED -> Region.FINISHED;
            };
        }
        if (flatteningOut != 0 && flatteningStart < yEnd) {
            accumulateBorder((flatteningOut & CLIP_X0) != 0, flatteningStart, yEnd, signBit);
        }
    }

    private void flattenStraightCurve(MonoCurve curve, double yEnd, int signBit) {
        double yStart = maximum(flatteningStart, clipY0);
        double xMin = minimum(curve.firstX(), curve.lastX());
        double xMax = maximum(curve.firstX(), curve.lastX());
        if (xMax <= clipX0) {
            accumulateBorder(true, yStart, yEnd, signBit);
        } else if (xMin >= clipX1) {
            accumulateBorder(false, yStart, yEnd, signBit);
        } else {
            openAppender(curve.firstX(), yStart);
            appendLine(curve.lastX(), yEnd);
            closeAppender(signBit);
        }
    }

    private Region flattenAbove(MonoCurve curve, boolean isLeftToRight) {
        int nearSide = isLeftToRight ? CLIP_X1 : CLIP_X0;
        int farSide = isLeftToRight ? CLIP_X0 : CLIP_X1;
        while (true) {
            boolean isPastFarEdge = isLeftToRight ? curve.firstX() >= clipX1 : curve.firstX() <= clipX0;
            if (isPastFarEdge) {
                flatteningOut = nearSide;
                return Region.FINISHED;
            }
            if (!curve.isFlat()) {
                curve.split();
                if (curve.midY() <= clipY0) {
                    curve.discardAndAdvance();
                    continue;
                }
                if (curve.canPush()) {
                    curve.push();
                    continue;
                }
            }
            if (curve.lastY() > clipY0) {
                boolean isBeforeNearEdge = isLeftToRight ? curve.lastX() < clipX0 : curve.lastX() > clipX1;
                if (isBeforeNearEdge) {
                    flatteningOut = farSide;
                    return Region.BEFORE_AT_POP;
                }
                double xClipped = curve.firstX() + (clipY0 - curve.firstY()) * ((curve.lastX() - curve.firstX())
                        / (curve.lastY() - curve.firstY()));
                boolean isClippedBefore = isLeftToRight ? xClipped <= clipX0 : xClipped >= clipX1;
                if (isClippedBefore) {
                    return Region.BEFORE_AT_CLIP;
                }
                boolean isClippedPast = isLeftToRight ? xClipped >= clipX1 : xClipped <= clipX0;
                if (isClippedPast) {
                    flatteningOut = nearSide;
                    return Region.FINISHED;
                }
                openAppender(xClipped, clipY0);
                return Region.VISIBLE_AT_ADD_LINE;
            }
            if (!curve.canPop()) {
                flatteningOut = 0;
                return Region.FINISHED;
            }
            curve.pop();
        }
    }

    private Region flattenBefore(MonoCurve curve, boolean isLeftToRight, Region entry, double yEnd, int signBit) {
        int side = isLeftToRight ? CLIP_X0 : CLIP_X1;
        double edge = isLeftToRight ? clipX0 : clipX1;
        Region at = entry;
        while (true) {
            if (at == Region.BEFORE) {
                if (curve.firstY() >= clipY1) {
                    flatteningOut = side;
                    return Region.FINISHED;
                }
                if (!curve.isFlat()) {
                    curve.split();
                    boolean isMidBefore = isLeftToRight ? curve.midX() <= clipX0 : curve.midX() >= clipX1;
                    if (isMidBefore) {
                        curve.discardAndAdvance();
                        continue;
                    }
                    if (curve.canPush()) {
                        curve.push();
                        continue;
                    }
                }
                boolean isLastInside = isLeftToRight ? curve.lastX() > clipX0 : curve.lastX() < clipX1;
                at = isLastInside ? Region.BEFORE_AT_CLIP : Region.BEFORE_AT_POP;
                if (!isLastInside) {
                    if (curve.lastY() >= yEnd) {
                        flatteningOut = side;
                        return Region.FINISHED;
                    }
                    flatteningOut = 0;
                }
            }
            if (at == Region.BEFORE_AT_CLIP) {
                double yClipped = curve.firstY() + (edge - curve.firstX()) * ((curve.lastY() - curve.firstY())
                        / (curve.lastX() - curve.firstX()));
                if (yClipped >= yEnd) {
                    flatteningOut = side;
                    return Region.FINISHED;
                }
                if (flatteningStart < yClipped) {
                    accumulateBorder(isLeftToRight, flatteningStart, yClipped, signBit);
                }
                openAppender(edge, yClipped);
                return Region.VISIBLE_AT_ADD_LINE;
            }
            if (!curve.canPop()) {
                flatteningOut = flatteningOut != 0 ? side : 0;
                return Region.FINISHED;
            }
            curve.pop();
            at = Region.BEFORE;
        }
    }

    private Region flattenVisible(MonoCurve curve, boolean isLeftToRight, Region entry, double yEnd, int signBit) {
        int side = isLeftToRight ? CLIP_X1 : CLIP_X0;
        double edge = isLeftToRight ? clipX1 : clipX0;
        boolean isAtAddLine = entry == Region.VISIBLE_AT_ADD_LINE;
        int out;
        while (true) {
            if (!isAtAddLine && !curve.isFlat()) {
                curve.split();
                if (curve.canPush()) {
                    curve.push();
                    continue;
                }
            }
            isAtAddLine = false;
            boolean isPast = isLeftToRight ? curve.lastX() > clipX1 : curve.lastX() < clipX0;
            out = isPast ? 1 : 0;
            if (isPast) {
                double yClipped = curve.firstY() + (edge - curve.firstX()) * ((curve.lastY() - curve.firstY())
                        / (curve.lastX() - curve.firstX()));
                if (yClipped <= yEnd) {
                    flatteningStart = yClipped;
                    appendLine(edge, yClipped);
                    break;
                }
            }
            out = curve.lastY() >= clipY1 ? 1 : 0;
            if (out != 0) {
                double xAtBottom = curve.firstX() + (clipY1 - curve.firstY()) * ((curve.lastX() - curve.firstX())
                        / (curve.lastY() - curve.firstY()));
                appendLine(isLeftToRight ? minimum(xAtBottom, clipX1) : maximum(xAtBottom, clipX0), clipY1);
                out = 0;
                break;
            }
            appendLine(curve.lastX(), curve.lastY());
            if (!curve.canPop()) {
                break;
            }
            curve.pop();
        }
        closeAppender(signBit);
        flatteningOut = out != 0 ? side : 0;
        return Region.FINISHED;
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

    private void accumulateBorder(boolean isLeft, double from, double to, int signBit) {
        if (signBit != 0) {
            accumulateBorder(isLeft, to, from);
        } else {
            accumulateBorder(isLeft, from, to);
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
