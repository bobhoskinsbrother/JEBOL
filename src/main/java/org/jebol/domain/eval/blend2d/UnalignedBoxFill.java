package org.jebol.domain.eval.blend2d;

import java.util.ArrayList;
import java.util.List;

final class UnalignedBoxFill {

    private static final int FRACTION_MASK = 0xFF;

    private static final int ONE_PIXEL = 256;

    private static final int WIDEST_ROW_OF_MASKS_ONLY = 20;

    private static final int LEADING_MASKS = 4;

    private static final int INNER_ALIGNMENT = 8;

    private UnalignedBoxFill() {
    }

    private record Span(int from, int to, boolean isConstant, int constantMask, int[] masks) {
    }

    private record RowGroup(List<Span> spans, int rows) {
    }

    static void fill(Blend2DSurface surface, int x0, int y0, int x1, int y1, int alpha, SolidFill fill) {
        int ax0 = x0 >>> 8;
        int ay0 = y0 >>> 8;
        int ax1 = (x1 + FRACTION_MASK) >>> 8;
        int ay1 = (y1 + FRACTION_MASK) >>> 8;
        int fx0 = x0 & FRACTION_MASK;
        int fy0 = y0 & FRACTION_MASK;
        int fx1 = ((x1 - 1) & FRACTION_MASK) + 1;
        int fy1 = ((y1 - 1) & FRACTION_MASK) + 1;
        int wide = ax1 - ax0;
        int high = ay1 - ay0;
        fy0 = (high == 1 ? fy1 : ONE_PIXEL) - fy0;
        int fy0Alpha = fy0 * alpha;
        int fy1Alpha = fy1 * alpha;

        List<RowGroup> groups = new ArrayList<>();
        int top = ay0;
        int bottom = ay0 + high;

        if (wide == 1) {
            int across = fx1 - fx0;
            int m0 = (across * fy0Alpha) >>> 16;
            int m1 = (across * alpha) >>> 8;
            int m2 = (across * fy1Alpha) >>> 16;
            if (high == 1) {
                if (m0 != 0) {
                    paint(surface, List.of(new RowGroup(List.of(constantSpan(ax0, ax1, m0)), 1)), top, top + 1, fill);
                }
                return;
            }
            addRows(groups, List.of(constantSpan(ax0, ax1, m0)), 1, m0 != 0);
            top += m0 == 0 ? 1 : 0;
            addRows(groups, List.of(constantSpan(ax0, ax1, m1)), high - 2, high > 2);
            groups.add(new RowGroup(List.of(constantSpan(ax0, ax1, m2)), 1));
            bottom -= m2 == 0 ? 1 : 0;
            if (top < bottom && m1 != 0) {
                paint(surface, groups, top, bottom, fill);
            }
            return;
        }

        int m0x1 = fy0Alpha >>> 8;
        int m2x1 = fy1Alpha >>> 8;
        int fromLeft = ONE_PIXEL - fx0;

        if ((fromLeft & fx1) == ONE_PIXEL) {
            addRows(groups, List.of(constantSpan(ax0, ax1, m0x1)), 1, m0x1 != 0);
            top += m0x1 == 0 ? 1 : 0;
            addRows(groups, List.of(constantSpan(ax0, ax1, alpha)), high - 2, high > 2);
            groups.add(new RowGroup(List.of(constantSpan(ax0, ax1, m2x1)), 1));
            bottom -= m2x1 == 0 ? 1 : 0;
            if (top < bottom) {
                paint(surface, groups, top, bottom, fill);
            }
            return;
        }

        int[] edgeLeft = {(fromLeft * fy0Alpha) >>> 16, (fromLeft * alpha) >>> 8, (fromLeft * fy1Alpha) >>> 16};
        int[] edgeRight = {(fx1 * fy0Alpha) >>> 16, (fx1 * alpha) >>> 8, (fx1 * fy1Alpha) >>> 16};
        int[] middles = {m0x1, alpha, m2x1};

        int widenedBy = (LEADING_MASKS - wide % LEADING_MASKS) % LEADING_MASKS;
        if (widenedBy > ax0) {
            widenedBy = 0;
        }
        int startAt = ax0 - widenedBy;
        int spanned = wide + widenedBy;

        List<List<Span>> rowsByKind = new ArrayList<>();
        for (int kind = 0; kind < 3; kind++) {
            int[] masks = new int[spanned];
            for (int at = 0; at < spanned; at++) {
                int pixel = startAt + at;
                masks[at] = pixel < ax0 ? 0 : pixel == ax0 ? edgeLeft[kind]
                        : pixel == ax1 - 1 ? edgeRight[kind] : middles[kind];
            }
            if (spanned <= WIDEST_ROW_OF_MASKS_ONLY) {
                rowsByKind.add(List.of(new Span(startAt, ax1, false, 0, masks)));
            } else {
                int innerWidth = (spanned - 5) / INNER_ALIGNMENT * INNER_ALIGNMENT;
                int innerEnd = startAt + LEADING_MASKS + innerWidth;
                rowsByKind.add(List.of(
                        new Span(startAt, startAt + LEADING_MASKS, false, 0, masks),
                        constantSpan(startAt + LEADING_MASKS, innerEnd, middles[kind]),
                        new Span(innerEnd, ax1, false, 0, masks)));
            }
        }
        addRows(groups, rowsByKind.get(0), 1, m0x1 != 0);
        top += m0x1 == 0 ? 1 : 0;
        addRows(groups, rowsByKind.get(1), high - 2, high > 2);
        groups.add(new RowGroup(rowsByKind.get(2), 1));
        bottom -= m2x1 == 0 ? 1 : 0;
        if (top < bottom) {
            paint(surface, groups, top, bottom, fill, startAt);
        }
    }

    private static Span constantSpan(int from, int to, int mask) {
        return new Span(from, to, true, mask, new int[0]);
    }

    private static void addRows(List<RowGroup> groups, List<Span> spans, int rows, boolean isKept) {
        if (isKept) {
            groups.add(new RowGroup(spans, rows));
        }
    }

    private static void paint(Blend2DSurface surface, List<RowGroup> groups, int top, int bottom, SolidFill fill) {
        paint(surface, groups, top, bottom, fill, 0);
    }

    private static void paint(Blend2DSurface surface, List<RowGroup> groups, int top, int bottom,
                              SolidFill fill, int masksStartAt) {
        int row = top;
        for (RowGroup group : groups) {
            for (int repeat = 0; repeat < group.rows() && row < bottom; repeat++, row++) {
                for (Span span : group.spans()) {
                    paintSpan(surface, row, span, fill, masksStartAt);
                }
            }
        }
    }

    private static void paintSpan(Blend2DSurface surface, int row, Span span, SolidFill fill, int masksStartAt) {
        for (int across = span.from(); across < span.to(); across++) {
            int destination = surface.pixelAt(across, row);
            int painted = span.isConstant()
                    ? fill.compositeUnderConstantMask(destination, span.constantMask())
                    : fill.compositeUnderVariableMask(destination, span.masks()[across - masksStartAt]);
            surface.setPixel(across, row, painted);
        }
    }
}
