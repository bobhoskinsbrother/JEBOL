package org.jebol.domain.eval.blend2d;

final class AnalyticCells {

    static final int PIXELS_PER_GROUP = 4;

    private final int high;

    private final int cellStride;

    private final int groupStride;

    private final int[] cells;

    private final boolean[] groups;

    AnalyticCells(int wide, int high) {
        this.high = high;
        int requiredWidth = (wide + 1 + PIXELS_PER_GROUP + PIXELS_PER_GROUP - 1) / PIXELS_PER_GROUP * PIXELS_PER_GROUP;
        cellStride = requiredWidth;
        groupStride = requiredWidth / PIXELS_PER_GROUP;
        cells = new int[cellStride * high];
        groups = new boolean[groupStride * high];
    }

    int high() {
        return high;
    }

    int groupCount() {
        return groupStride;
    }

    int cellAt(int row, int x) {
        return cells[row * cellStride + x];
    }

    boolean isGroupMarked(int row, int group) {
        return groups[row * groupStride + group];
    }

    void addToCell(int row, int x, int value) {
        cells[row * cellStride + x] += value;
    }

    void mergeIntoCell(int row, int x, int cover, int area) {
        int at = row * cellStride + x;
        cells[at] += (cover << 9) - area;
        cells[at + 1] += area;
    }

    void markGroupAt(int row, int x) {
        groups[row * groupStride + Integer.divideUnsigned(x, PIXELS_PER_GROUP)] = true;
    }

    void markGroups(int row, int firstGroup, int lastGroup) {
        for (int group = firstGroup; group <= lastGroup; group++) {
            groups[row * groupStride + group] = true;
        }
    }
}
