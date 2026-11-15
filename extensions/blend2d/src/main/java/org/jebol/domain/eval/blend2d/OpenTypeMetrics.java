package org.jebol.domain.eval.blend2d;

import java.util.Optional;

final class OpenTypeMetrics {

    private static final int HEADER_SIZE = 36;

    private static final int LONG_METRIC_SIZE = 4;

    private final boolean hasHorizontalHeader;

    private final OpenTypeTable horizontalMetrics;

    private final int longMetricCount;

    private OpenTypeMetrics(boolean hasHorizontalHeader, OpenTypeTable horizontalMetrics, int longMetricCount) {
        this.hasHorizontalHeader = hasHorizontalHeader;
        this.horizontalMetrics = horizontalMetrics;
        this.longMetricCount = longMetricCount;
    }

    static Optional<OpenTypeMetrics> read(OpenTypeFace face, OpenTypeTable hhea, OpenTypeTable hmtx,
                                          OpenTypeTable vhea, OpenTypeTable vmtx) {
        boolean hasHeader = !hhea.isEmpty();
        OpenTypeTable metrics = OpenTypeTable.EMPTY;
        int longMetrics = 0;
        if (hasHeader) {
            if (!hhea.fits(HEADER_SIZE)) {
                return Optional.empty();
            }
            if (!hmtx.isEmpty()) {
                longMetrics = Math.min(hhea.readU16(34), face.glyphCount);
                if (!hmtx.fits((long) longMetrics * LONG_METRIC_SIZE)) {
                    return Optional.empty();
                }
                metrics = hmtx;
            }
        }
        if (!vhea.isEmpty()) {
            if (!vhea.fits(HEADER_SIZE)) {
                return Optional.empty();
            }
            if (!vmtx.isEmpty() && !vmtx.fits((long) Math.min(vhea.readU16(34), face.glyphCount) * LONG_METRIC_SIZE)) {
                return Optional.empty();
            }
        }
        return Optional.of(new OpenTypeMetrics(hasHeader, metrics, longMetrics));
    }

    boolean fillAdvances(int[] glyphs, double[] advances) {
        if (!hasHorizontalHeader || longMetricCount == 0) {
            return false;
        }
        for (int at = 0; at < glyphs.length; at++) {
            int index = Math.min(glyphs[at], longMetricCount - 1);
            advances[at] = horizontalMetrics.readU16(index * LONG_METRIC_SIZE);
        }
        return true;
    }
}
