package org.jebol.domain.eval.blend2d;

import java.util.Arrays;

final class QuadSpline {

    private QuadSpline() {
    }

    static double[] split(double startX, double startY, double[] controls) {
        double[] p = {startX, startY, controls[0], controls[1], controls[2], controls[3]};
        double v1x = p[2] - p[0];
        double v1y = p[3] - p[1];
        double v2x = p[4] - p[2];
        double v2y = p[5] - p[3];
        double ax = v2x - v1x;
        double ay = v2y - v1y;
        double bx = v1x + v1x;
        double by = v1y + v1y;
        double cx = p[0];
        double cy = p[1];
        double extremaX = (p[0] - p[2]) / (p[0] - p[2] * 2.0 + p[4]);
        double extremaY = (p[1] - p[3]) / (p[1] - p[3] * 2.0 + p[5]);
        double t0 = extremaY < extremaX ? extremaY : extremaX;
        double t1 = extremaX < extremaY ? extremaY : extremaX;
        double[] cuts = new double[3];
        int count = 0;
        cuts[count] = t0;
        count += t0 > 0.0 & t0 < 1.0 ? 1 : 0;
        double floor = t0 < 0.0 ? 0.0 : t0;
        cuts[count] = t1;
        count += t1 > floor & t1 < 1.0 ? 1 : 0;
        if (count == 0) {
            return p;
        }
        cuts[count++] = 1.0;
        double[] out = new double[2 + 4 * count];
        out[0] = p[0];
        out[1] = p[1];
        int written = 2;
        double tCut = 0.0;
        int at = 0;
        do {
            double t = cuts[at];
            double step = (t - tCut) * 0.5;
            double cpx = (ax * (t * 2.0) + bx) * step;
            double cpy = (ay * (t * 2.0) + by) * step;
            double tpx = (ax * t + bx) * t + cx;
            double tpy = (ay * t + by) * t + cy;
            if (++at == count) {
                tpx = p[4];
                tpy = p[5];
            }
            out[written] = tpx - cpx;
            out[written + 1] = tpy - cpy;
            out[written + 2] = tpx;
            out[written + 3] = tpy;
            written += 4;
            tCut = t;
        } while (at != count);
        return Arrays.copyOf(out, written);
    }
}
