package org.jebol.domain.eval.easing;

/**
 * The easing curves of Siskin-framework's Rebol-Easing extension 1.0.0, each
 * a function of one double computed as the extension's C computes it on a
 * 64-bit Linux or macOS build.
 */
public final class Easing {

    private static final double HALF_PI = Math.PI / 2;

    private Easing() {
    }

    public static double linear(double p) {
        return p;
    }

    public static double inQuad(double p) {
        return p * p;
    }

    public static double outQuad(double p) {
        return -(p * (p - 2));
    }

    public static double inOutQuad(double p) {
        if (p < 0.5) {
            return 2 * p * p;
        }
        return (-2 * p * p) + (4 * p) - 1;
    }

    public static double inCubic(double p) {
        return p * p * p;
    }

    public static double outCubic(double p) {
        double f = p - 1;
        return f * f * f + 1;
    }

    public static double inOutCubic(double p) {
        if (p < 0.5) {
            return 4 * p * p * p;
        }
        double f = (2 * p) - 2;
        return 0.5 * f * f * f + 1;
    }

    public static double inQuart(double p) {
        return p * p * p * p;
    }

    public static double outQuart(double p) {
        double f = p - 1;
        return f * f * f * (1 - p) + 1;
    }

    public static double inOutQuart(double p) {
        if (p < 0.5) {
            return 8 * p * p * p * p;
        }
        double f = p - 1;
        return -8 * f * f * f * f + 1;
    }

    public static double inQuint(double p) {
        return p * p * p * p * p;
    }

    public static double outQuint(double p) {
        double f = p - 1;
        return f * f * f * f * f + 1;
    }

    public static double inSine(double p) {
        return Math.sin((p - 1) * HALF_PI) + 1;
    }

    public static double outSine(double p) {
        return Math.sin(p * HALF_PI);
    }

    public static double inOutSine(double p) {
        return 0.5 * (1 - Math.cos(p * Math.PI));
    }

    public static double inCirc(double p) {
        return 1 - Math.sqrt(1 - (p * p));
    }

    public static double outCirc(double p) {
        return Math.sqrt((2 - p) * p);
    }

    public static double inOutCirc(double p) {
        if (p < 0.5) {
            return 0.5 * (1 - Math.sqrt(1 - 4 * (p * p)));
        }
        return 0.5 * (Math.sqrt(-((2 * p) - 3) * ((2 * p) - 1)) + 1);
    }

    public static double inExpo(double p) {
        return p == 0.0 ? p : Math.pow(2, 10 * (p - 1));
    }

    public static double outExpo(double p) {
        return p == 1.0 ? p : 1 - Math.pow(2, -10 * p);
    }

    public static double inOutExpo(double p) {
        if (p == 0.0 || p == 1.0) {
            return p;
        }
        if (p < 0.5) {
            return 0.5 * Math.pow(2, (20 * p) - 10);
        }
        return -0.5 * Math.pow(2, (-20 * p) + 10) + 1;
    }

    public static double inElastic(double p) {
        return Math.sin(13 * HALF_PI * p) * Math.pow(2, 10 * (p - 1));
    }

    public static double outElastic(double p) {
        return Math.sin(-13 * HALF_PI * (p + 1)) * Math.pow(2, -10 * p) + 1;
    }

    public static double inOutElastic(double p) {
        if (p < 0.5) {
            return 0.5 * Math.sin(13 * HALF_PI * (2 * p)) * Math.pow(2, 10 * ((2 * p) - 1));
        }
        return 0.5 * (Math.sin(-13 * HALF_PI * ((2 * p - 1) + 1))
                * Math.pow(2, -10 * (2 * p - 1)) + 2);
    }

    public static double inBack(double p) {
        return p * p * p - p * Math.sin(p * Math.PI);
    }

    public static double outBack(double p) {
        double f = 1 - p;
        return 1 - (f * f * f - f * Math.sin(f * Math.PI));
    }

    public static double inOutBack(double p) {
        if (p < 0.5) {
            double f = 2 * p;
            return 0.5 * (f * f * f - f * Math.sin(f * Math.PI));
        }
        double f = 1 - (2 * p - 1);
        return 0.5 * (1 - (f * f * f - f * Math.sin(f * Math.PI))) + 0.5;
    }

    public static double inBounce(double p) {
        return 1 - outBounce(1 - p);
    }

    public static double outBounce(double p) {
        if (p < 4 / 11.0) {
            return (121 * p * p) / 16.0;
        }
        if (p < 8 / 11.0) {
            return (363 / 40.0 * p * p) - (99 / 10.0 * p) + 17 / 5.0;
        }
        if (p < 9 / 10.0) {
            return (4356 / 361.0 * p * p) - (35442 / 1805.0 * p) + 16061 / 1805.0;
        }
        return (54 / 5.0 * p * p) - (513 / 25.0 * p) + 268 / 25.0;
    }

    public static double inOutBounce(double p) {
        if (p < 0.5) {
            return 0.5 * inBounce(p * 2);
        }
        return 0.5 * outBounce(p * 2 - 1) + 0.5;
    }
}
