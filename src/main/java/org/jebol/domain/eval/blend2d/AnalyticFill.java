package org.jebol.domain.eval.blend2d;

final class AnalyticFill {

    private static final int COVERAGE_ORIGIN = 256 << 9;

    private static final int FULL_MASK = 0x200;

    private static final int NON_ZERO_RULE = 0xFFFFFFFF;

    private static final int EVEN_ODD_RULE = 0x1FF;

    private static final int GROUP = AnalyticCells.PIXELS_PER_GROUP;

    private AnalyticFill() {
    }

    enum FillRule {
        NON_ZERO,
        EVEN_ODD
    }

    static void fill(Blend2DSurface surface, AnalyticCells cells, FillRule rule, int globalAlpha, PixelSource fill) {
        int ruleMask = rule == FillRule.NON_ZERO ? NON_ZERO_RULE : EVEN_ODD_RULE;
        int end = surface.wide();
        for (int row = 0; row < cells.high(); row++) {
            fillRow(surface, cells, row, end, ruleMask, globalAlpha, fill);
        }
    }

    private static void fillRow(Blend2DSurface surface, AnalyticCells cells, int row, int end,
                                int ruleMask, int globalAlpha, PixelSource fill) {
        int groupCount = cells.groupCount();
        int group = nextMarkedGroup(cells, row, 0, groupCount);
        if (group == groupCount) {
            return;
        }
        int x = group * GROUP;
        if (x >= end) {
            return;
        }
        int coverage = COVERAGE_ORIGIN;
        while (true) {
            int spanEndGroup = nextUnmarkedGroup(cells, row, group, groupCount);
            int spanEnd = Math.min(spanEndGroup * GROUP, end);
            for (; x < spanEnd; x++) {
                coverage += cells.cellAt(row, x);
                int mask = calculateMask(coverage, ruleMask, globalAlpha);
                surface.setPixel(x, row, fill.compositeUnderAnalyticMask(surface.pixelAt(x, row), mask, x, row));
            }
            coverage += cells.cellAt(row, x);
            int gapMask = calculateMask(coverage, ruleMask, globalAlpha);
            if (x >= end) {
                return;
            }
            group = nextMarkedGroup(cells, row, spanEndGroup, groupCount);
            if (group == groupCount) {
                return;
            }
            int gapEnd = group * GROUP;
            if (gapMask != 0) {
                for (; x < gapEnd; x++) {
                    surface.setPixel(x, row, fill.compositeUnderConstantMask(surface.pixelAt(x, row), gapMask, x, row));
                }
            }
            x = gapEnd;
        }
    }

    private static int nextMarkedGroup(AnalyticCells cells, int row, int from, int groupCount) {
        int group = from;
        while (group < groupCount && !cells.isGroupMarked(row, group)) {
            group++;
        }
        return group;
    }

    private static int nextUnmarkedGroup(AnalyticCells cells, int row, int from, int groupCount) {
        int group = from;
        while (group < groupCount && cells.isGroupMarked(row, group)) {
            group++;
        }
        return group;
    }

    static int calculateMask(int coverage, int ruleMask, int globalAlpha) {
        int mask = Math.abs(((coverage >> 8) & (ruleMask << 1)) - FULL_MASK);
        if (Integer.compareUnsigned(mask, FULL_MASK) > 0) {
            mask = FULL_MASK;
        }
        return (mask * (globalAlpha << 7)) >>> 16;
    }
}
