package org.jebol.domain.eval.blend2d;

import java.util.Arrays;

final class CubicSpline {

    private static final double AFTER_ZERO = 1e-40;

    private static final double BEFORE_ONE = 0.999999999999999889;

    private static final double ONE_THIRD = 1.0 / 3.0;

    private static final int MOST_CUTS = 8;

    private CubicSpline() {
    }

    static double[] split(double startX, double startY, double[] controls) {
        double[] p = {startX, startY, controls[0], controls[1], controls[2], controls[3], controls[4], controls[5]};
        double v1x = p[2] - p[0];
        double v1y = p[3] - p[1];
        double v2x = p[4] - p[2];
        double v2y = p[5] - p[3];
        double v3x = p[6] - p[4];
        double v3y = p[7] - p[5];
        double ax = v3x - v2x - v2x + v1x;
        double ay = v3y - v2y - v2y + v1y;
        double bx = 3.0 * (v2x - v1x);
        double by = 3.0 * (v2y - v1y);
        double cx = 3.0 * v1x;
        double cy = 3.0 * v1y;
        double dx = p[0];
        double dy = p[1];

        double[] cuts = new double[MOST_CUTS];
        int count = 0;
        double q0 = bx * ay - by * ax;
        double q1 = cx * ay - cy * ax;
        double q2 = cx * by - cy * bx;
        double cusp = (q1 / q0) * -0.5;
        cuts[count] = cusp;
        count += cusp > 0.0 & cusp < 1.0 ? 1 : 0;
        count = appendRoots(cuts, count, q0 * 6.0, q1 * 6.0, q2 * 2.0);
        double derivativeAx = 3.0 * (v3x - v2x - v2x + v1x);
        double derivativeAy = 3.0 * (v3y - v2y - v2y + v1y);
        double derivativeBx = 6.0 * (v2x - v1x);
        double derivativeBy = 6.0 * (v2y - v1y);
        double derivativeCx = 3.0 * v1x;
        double derivativeCy = 3.0 * v1y;
        count = appendRoots(cuts, count, derivativeAx, derivativeBx, derivativeCx);
        count = appendRoots(cuts, count, derivativeAy, derivativeBy, derivativeCy);
        if (count == 0) {
            return p;
        }
        sortAscending(cuts, count);
        cuts[count++] = 1.0;

        double[] out = new double[2 + 6 * count];
        out[0] = p[0];
        out[1] = p[1];
        int written = 2;
        double tCut = 0.0;
        int at = 0;
        do {
            double t = cuts[at++];
            if (t == tCut) {
                continue;
            }
            double step = (t - tCut) * ONE_THIRD;
            double tpx = ((ax * t + bx) * t + cx) * t + dx;
            double tpy = ((ay * t + by) * t + cy) * t + dy;
            if (at == count) {
                tpx = p[6];
                tpy = p[7];
            }
            double cp1x = ((ax * (tCut * 3.0) + bx * 2.0) * tCut + cx) * step;
            double cp1y = ((ay * (tCut * 3.0) + by * 2.0) * tCut + cy) * step;
            double cp2x = ((ax * (t * 3.0) + bx * 2.0) * t + cx) * step;
            double cp2y = ((ay * (t * 3.0) + by * 2.0) * t + cy) * step;
            out[written] = out[written - 2] + cp1x;
            out[written + 1] = out[written - 1] + cp1y;
            out[written + 2] = tpx - cp2x;
            out[written + 3] = tpy - cp2y;
            out[written + 4] = tpx;
            out[written + 5] = tpy;
            written += 6;
            tCut = t;
        } while (at != count);
        return Arrays.copyOf(out, written);
    }

    private static int appendRoots(double[] cuts, int count, double a, double b, double c) {
        double discriminant = b * b - 4.0 * a * c;
        double d = discriminant < 0.0 ? 0.0 : discriminant;
        double s = Math.sqrt(d);
        double q = -0.5 * (b + Math.copySign(s, b));
        double t0 = q / a;
        double t1 = c / q;
        double x0 = t1 < t0 ? t1 : t0;
        double x1 = t1 < t0 ? t0 : t1;
        cuts[count] = x0;
        int found = x0 >= AFTER_ZERO & x0 <= BEFORE_ONE ? 1 : 0;
        cuts[count + found] = x1;
        found += x1 > x0 & x1 >= AFTER_ZERO & x1 <= BEFORE_ONE ? 1 : 0;
        return count + found;
    }

    private static void sortAscending(double[] values, int count) {
        for (int moving = 1; moving < count; moving++) {
            for (int at = moving; at > 0 && values[at - 1] > values[at]; at--) {
                double held = values[at - 1];
                values[at - 1] = values[at];
                values[at] = held;
            }
        }
    }
}
