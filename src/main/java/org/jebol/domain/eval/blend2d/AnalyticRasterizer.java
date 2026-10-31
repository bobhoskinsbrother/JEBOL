package org.jebol.domain.eval.blend2d;

final class AnalyticRasterizer {

    private static final int SHIFT = 8;

    private static final int SCALE = 1 << SHIFT;

    private static final int MASK = SCALE - 1;

    private static final int GROUP = AnalyticCells.PIXELS_PER_GROUP;

    private static final int ENTER_AT_TOP = 0;

    private static final int ENTER_AT_SKIP = 1;

    private static final int ENTER_INSIDE = 2;

    private final AnalyticCells cells;

    private int signMask;

    private int ex0;

    private int ey0;

    private int ex1;

    private int ey1;

    private int fx0;

    private int fy0;

    private int fx1;

    private int fy1;

    private int xErr;

    private int yErr;

    private int xDlt;

    private int yDlt;

    private int xRem;

    private int yRem;

    private int xLift;

    private int yLift;

    private int dx;

    private int dy;

    private int savedFy1;

    private boolean isInitialScanline;

    private boolean isVerticalOrSingle;

    private boolean isRightToLeft;

    AnalyticRasterizer(AnalyticCells cells) {
        this.cells = cells;
    }

    void rasterize(EdgeBuilder.EdgeVector edge) {
        signMask = -edge.signBit();
        int[] points = edge.points();
        for (int at = 2; at < points.length; at += 2) {
            if (prepare(points[at - 2], points[at - 1], points[at], points[at + 1])) {
                rasterizeLine();
            }
        }
    }

    private int applySign(int value) {
        return (value ^ signMask) - signMask;
    }

    private int stepXError(int accumulator, int step) {
        xErr -= step;
        int underflow = xErr >> 31;
        xErr += underflow & dy;
        return accumulator - underflow;
    }

    private int stepYError(int accumulator, int step) {
        yErr -= step;
        int underflow = yErr >> 31;
        yErr += underflow & dx;
        return accumulator - underflow;
    }

    private boolean prepare(int x0, int y0, int x1, int y1) {
        if (y0 == y1) {
            return false;
        }
        dx = x1 - x0;
        dy = y1 - y0;
        isInitialScanline = true;
        isVerticalOrSingle = false;
        isRightToLeft = false;
        if (dx < 0) {
            isRightToLeft = true;
            dx = -dx;
        }
        ex0 = x0 >> SHIFT;
        ey0 = y0 >> SHIFT;
        ex1 = x1 >> SHIFT;
        ey1 = (y1 - 1) >> SHIFT;
        fx0 = x0 & MASK;
        fy0 = y0 & MASK;
        fx1 = x1 & MASK;
        fy1 = ((y1 - 1) & MASK) + 1;
        savedFy1 = fy1;
        if (ey0 != ey1) {
            fy1 = SCALE;
        }
        if (ex0 == ex1 && (ey0 == ey1 || dx == 0)) {
            isVerticalOrSingle = true;
            return true;
        }
        long xBase = Integer.toUnsignedLong(dx) * SCALE;
        long yBase = Integer.toUnsignedLong(dy) * SCALE;
        xLift = (int) (xBase / Integer.toUnsignedLong(dy));
        xRem = (int) (xBase % Integer.toUnsignedLong(dy));
        yLift = (int) (yBase / Integer.toUnsignedLong(dx));
        yRem = (int) (yBase % Integer.toUnsignedLong(dx));
        xDlt = dx;
        yDlt = dy;
        xErr = (dy >> 1) - 1;
        yErr = (dx >> 1) - 1;
        if (ey0 != ey1) {
            long p = Integer.toUnsignedLong(SCALE - fy0) * Integer.toUnsignedLong(dx);
            xDlt = (int) (p / Integer.toUnsignedLong(dy));
            xErr -= (int) (p % Integer.toUnsignedLong(dy));
            xDlt = stepXError(xDlt, 0);
        }
        if (ex0 != ex1) {
            long p = Integer.toUnsignedLong(isRightToLeft ? fx0 : SCALE - fx0) * Integer.toUnsignedLong(dy);
            yDlt = (int) (p / Integer.toUnsignedLong(dx));
            yErr -= (int) (p % Integer.toUnsignedLong(dx));
            yDlt = stepYError(yDlt, 0);
        }
        yDlt += fy0;
        return true;
    }

    private void rasterizeLine() {
        int i = ey1 - ey0;
        int row = ey0;
        int fullCover = applySign(SCALE);
        if (isVerticalOrSingle) {
            rasterizeVerticalOrSingle(i, row, fullCover);
        } else if (dy >= dx) {
            if (isRightToLeft) {
                rasterizeSteepRightToLeft(i, row, fullCover);
            } else {
                rasterizeSteepLeftToRight(i, row, fullCover);
            }
        } else if (isRightToLeft) {
            rasterizeShallowRightToLeft(i, row);
        } else {
            rasterizeShallowLeftToRight(i, row);
        }
    }

    private void rasterizeVerticalOrSingle(int i, int row, int fullCover) {
        int area = fx0 + fx1;
        int cover = applySign(fy1 - fy0);
        cells.mergeIntoCell(row, ex0, cover, cover * area);
        cells.markGroupAt(row, ex0);
        if (i == 0) {
            return;
        }
        row++;
        cover = fullCover;
        while (--i != 0) {
            cells.mergeIntoCell(row, ex0, cover, cover * area);
            cells.markGroupAt(row, ex0);
            row++;
        }
        cover = applySign(savedFy1);
        cells.mergeIntoCell(row, ex0, cover, cover * area);
        cells.markGroupAt(row, ex0);
    }

    private void rasterizeSteepRightToLeft(int i, int row, int fullCover) {
        while (true) {
            int area = fx0;
            fx0 -= xDlt;
            boolean isSingle = true;
            if (fx0 < 0) {
                ex0--;
                fx0 += SCALE;
                yDlt &= MASK;
                if (area == 0) {
                    area = SCALE;
                    yDlt = stepYError(yDlt, yRem);
                    yDlt += yLift;
                } else {
                    isSingle = false;
                    cells.markGroupAt(row, ex0);
                    cells.markGroupAt(row, ex0 + 1);
                    int cov0 = applySign(yDlt - fy0);
                    cells.mergeIntoCell(row, ex0 + 1, cov0, cov0 * area);
                    cov0 = applySign(fy1 - yDlt);
                    cells.mergeIntoCell(row, ex0, cov0, cov0 * (fx0 + SCALE));
                    yDlt = stepYError(yDlt, yRem);
                    yDlt += yLift;
                }
            }
            if (isSingle) {
                cells.markGroupAt(row, ex0);
                int cov0 = applySign(fy1 - fy0);
                cells.mergeIntoCell(row, ex0, cov0, cov0 * (area + fx0));
            }
            fy0 = 0;
            row++;
            if (i == 0) {
                return;
            }
            while (--i != 0) {
                xDlt = stepXError(xLift, xRem);
                area = fx0;
                fx0 -= xDlt;
                boolean isSingleInLoop = true;
                if (fx0 < 0) {
                    ex0--;
                    fx0 += SCALE;
                    yDlt &= MASK;
                    if (area == 0) {
                        area = SCALE;
                        yDlt = stepYError(yDlt, yRem);
                        yDlt += yLift;
                    } else {
                        isSingleInLoop = false;
                        cells.markGroupAt(row, ex0);
                        cells.markGroupAt(row, ex0 + 1);
                        int cov1 = applySign(yDlt);
                        area = cov1 * area;
                        cells.addToCell(row, ex0 + 2, area);
                        int cov0 = fullCover - cov1;
                        cov1 = (cov1 << 9) - area;
                        area = cov0 * (fx0 + SCALE);
                        cov0 = (cov0 << 9) - area;
                        cov1 = cov1 + area;
                        cells.addToCell(row, ex0, cov0);
                        cells.addToCell(row, ex0 + 1, cov1);
                        yDlt = stepYError(yDlt, yRem);
                        yDlt += yLift;
                    }
                }
                if (isSingleInLoop) {
                    cells.markGroupAt(row, ex0);
                    cells.mergeIntoCell(row, ex0, fullCover, fullCover * (area + fx0));
                }
                row++;
            }
            fy1 = savedFy1;
            xDlt = ((ex0 - ex1) << SHIFT) + fx0 - fx1;
        }
    }

    private void rasterizeSteepLeftToRight(int i, int row, int fullCover) {
        while (true) {
            int area = fx0;
            fx0 += xDlt;
            cells.markGroupAt(row, ex0);
            if (fx0 <= SCALE) {
                int cov0 = applySign(fy1 - fy0);
                cells.mergeIntoCell(row, ex0, cov0, cov0 * (area + fx0));
                if (fx0 == SCALE) {
                    ex0++;
                    fx0 = 0;
                    yDlt += yLift;
                    yDlt = stepYError(yDlt, yRem);
                }
            } else {
                ex0++;
                fx0 &= MASK;
                yDlt &= MASK;
                cells.markGroupAt(row, ex0);
                int cov0 = applySign(yDlt - fy0);
                cells.mergeIntoCell(row, ex0 - 1, cov0, cov0 * (area + SCALE));
                cov0 = applySign(fy1 - yDlt);
                cells.mergeIntoCell(row, ex0, cov0, cov0 * fx0);
                yDlt += yLift;
                yDlt = stepYError(yDlt, yRem);
            }
            fy0 = 0;
            row++;
            if (i == 0) {
                return;
            }
            while (--i != 0) {
                xDlt = stepXError(xLift, xRem);
                area = fx0;
                fx0 += xDlt;
                cells.markGroupAt(row, ex0);
                if (fx0 <= SCALE) {
                    cells.mergeIntoCell(row, ex0, fullCover, fullCover * (area + fx0));
                    row++;
                    if (fx0 < SCALE) {
                        continue;
                    }
                    ex0++;
                    fx0 = 0;
                } else {
                    fx0 &= MASK;
                    yDlt &= MASK;
                    int cov0 = applySign(yDlt);
                    int cov1 = cov0 * (area + SCALE);
                    cov0 = (cov0 << 9) - cov1;
                    cells.addToCell(row, ex0, cov0);
                    ex0++;
                    cov0 = applySign(SCALE - yDlt);
                    area = cov0 * fx0;
                    cov0 = (cov0 << 9) - area + cov1;
                    cells.addToCell(row, ex0, cov0);
                    cells.addToCell(row, ex0 + 1, area);
                    cells.markGroupAt(row, ex0);
                    row++;
                }
                yDlt += yLift;
                yDlt = stepYError(yDlt, yRem);
            }
            fy1 = savedFy1;
            xDlt = ((ex1 - ex0) << SHIFT) + fx1 - fx0;
        }
    }

    private void rasterizeShallowRightToLeft(int i, int row) {
        int j = 1;
        int xLocal = (ex0 << SHIFT) + fx0;
        int cover = 0;
        int entry = ENTER_AT_TOP;
        if (isInitialScanline) {
            isInitialScanline = false;
            j = i;
            i = 1;
            cover = applySign(yDlt - fy0);
            if (fx0 - xDlt < 0) {
                entry = ENTER_INSIDE;
            } else {
                xLocal -= xDlt;
                cells.markGroupAt(row, ex0);
                cover = applySign(fy1 - fy0);
                cells.mergeIntoCell(row, ex0, cover, cover * (fx0 * 2 - xDlt));
                if ((xLocal & MASK) == 0) {
                    yDlt += yLift;
                    yDlt = stepYError(yDlt, yRem);
                }
                xDlt = stepXError(xLift, xRem);
                row++;
                i--;
            }
        }
        while (true) {
            while (i != 0) {
                if (entry == ENTER_AT_TOP) {
                    ex0 = (xLocal - 1) >> SHIFT;
                    fx0 = ((xLocal - 1) & MASK) + 1;
                }
                if (entry != ENTER_INSIDE) {
                    yDlt -= SCALE;
                    cover = applySign(yDlt);
                }
                entry = ENTER_AT_TOP;
                xLocal -= xDlt;
                int exLocal = xLocal >> SHIFT;
                int fxLocal = xLocal & MASK;
                cells.markGroups(row, Integer.divideUnsigned(exLocal, GROUP), Integer.divideUnsigned(ex0, GROUP));
                int area = cover * fx0;
                while (ex0 != exLocal) {
                    cells.mergeIntoCell(row, ex0, cover, area);
                    cover = stepYError(yLift, yRem);
                    yDlt += cover;
                    cover = applySign(cover);
                    area = cover * SCALE;
                    ex0--;
                }
                cover += applySign(fy1 - yDlt);
                area = cover * (fxLocal + SCALE);
                cells.mergeIntoCell(row, ex0, cover, area);
                if (fxLocal == 0) {
                    yDlt += yLift;
                    yDlt = stepYError(yDlt, yRem);
                }
                xDlt = stepXError(xLift, xRem);
                row++;
                i--;
            }
            fy0 = 0;
            fy1 = SCALE;
            if (j == 0) {
                return;
            }
            i = j - 1;
            j = 1;
            if (i == 0) {
                i = 1;
                j = 0;
                xDlt = xLocal - ((ex1 << SHIFT) + fx1);
                fy1 = savedFy1;
                ex0 = (xLocal - 1) >> SHIFT;
                fx0 = ((xLocal - 1) & MASK) + 1;
                if (fx0 - xDlt >= 0) {
                    cover = applySign(fy1);
                    cells.mergeIntoCell(row, ex0, cover, cover * (fx0 * 2 - xDlt));
                    cells.markGroupAt(row, ex0);
                    return;
                }
                entry = ENTER_AT_SKIP;
            }
        }
    }

    private void rasterizeShallowLeftToRight(int i, int row) {
        int j = 1;
        int xLocal = (ex0 << SHIFT) + fx0;
        int cover = 0;
        int entry = ENTER_AT_TOP;
        if (isInitialScanline) {
            isInitialScanline = false;
            j = i;
            i = 1;
            cover = applySign(yDlt - fy0);
            if (fx0 + xDlt > SCALE) {
                entry = ENTER_INSIDE;
            } else {
                xLocal += xDlt;
                cells.markGroupAt(row, ex0);
                cover = applySign(fy1 - fy0);
                cells.mergeIntoCell(row, ex0, cover, cover * (fx0 * 2 + xDlt));
                if (fx0 + xDlt == SCALE) {
                    yDlt += yLift;
                    yDlt = stepYError(yDlt, yRem);
                }
                xDlt = stepXError(xLift, xRem);
                row++;
                i--;
            }
        }
        while (true) {
            while (i != 0) {
                if (entry == ENTER_AT_TOP) {
                    ex0 = xLocal >> SHIFT;
                    fx0 = xLocal & MASK;
                }
                if (entry != ENTER_INSIDE) {
                    yDlt -= SCALE;
                    cover = applySign(yDlt);
                }
                entry = ENTER_AT_TOP;
                xLocal += xDlt;
                int exLocal = (xLocal - 1) >> SHIFT;
                int fxLocal = ((xLocal - 1) & MASK) + 1;
                cells.markGroups(row, Integer.divideUnsigned(ex0, GROUP), Integer.divideUnsigned(exLocal, GROUP));
                int area = cover * (fx0 + SCALE);
                while (ex0 != exLocal) {
                    cells.mergeIntoCell(row, ex0, cover, area);
                    cover = stepYError(yLift, yRem);
                    yDlt += cover;
                    cover = applySign(cover);
                    area = cover * SCALE;
                    ex0++;
                }
                cover += applySign(fy1 - yDlt);
                area = cover * fxLocal;
                cells.mergeIntoCell(row, ex0, cover, area);
                if (fxLocal == SCALE) {
                    yDlt += yLift;
                    yDlt = stepYError(yDlt, yRem);
                }
                xDlt = stepXError(xLift, xRem);
                row++;
                i--;
            }
            fy0 = 0;
            fy1 = SCALE;
            if (j == 0) {
                return;
            }
            i = j - 1;
            j = 1;
            if (i == 0) {
                i = 1;
                j = 0;
                xDlt = ((ex1 << SHIFT) + fx1) - xLocal;
                fy1 = savedFy1;
                ex0 = xLocal >> SHIFT;
                fx0 = xLocal & MASK;
                if (fx0 + xDlt <= SCALE) {
                    cover = applySign(fy1);
                    cells.mergeIntoCell(row, ex0, cover, cover * (fx0 * 2 + xDlt));
                    cells.markGroupAt(row, ex0);
                    return;
                }
                entry = ENTER_AT_SKIP;
            }
        }
    }
}
