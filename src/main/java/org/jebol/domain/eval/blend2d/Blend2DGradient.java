package org.jebol.domain.eval.blend2d;

import java.util.ArrayList;
import java.util.List;

public final class Blend2DGradient implements Blend2DPaint {

    public enum Extend {
        PAD,
        REPEAT,
        REFLECT
    }

    private enum Kind {
        LINEAR,
        RADIAL,
        CONIC
    }

    private record Stop(double offset, int argb) {
    }

    private static final double TURN = 2 * Math.PI;

    private static final int OPAQUE_ALPHA = 0xFF;

    private final Kind kind;

    private final double[] values;

    private final List<Stop> stops = new ArrayList<>();

    private Extend extend = Extend.PAD;

    private Blend2DGradient(Kind kind, double... values) {
        this.kind = kind;
        this.values = values;
    }

    public static Blend2DGradient linear(double x0, double y0, double x1, double y1) {
        return new Blend2DGradient(Kind.LINEAR, x0, y0, x1, y1);
    }

    public static Blend2DGradient radial(double centreX, double centreY, double focalX, double focalY, double radius) {
        return new Blend2DGradient(Kind.RADIAL, centreX, centreY, focalX, focalY, radius);
    }

    public static Blend2DGradient conic(double centreX, double centreY, double angle, double repeat) {
        return new Blend2DGradient(Kind.CONIC, centreX, centreY, angle, repeat);
    }

    public void setExtend(Extend chosen) {
        extend = chosen;
    }

    public void addStop(double offset, int argb) {
        if (!(offset >= 0.0 && offset <= 1.0)) {
            return;
        }
        Stop stop = new Stop(offset, argb);
        if (stops.isEmpty() || offset < stops.getFirst().offset()) {
            stops.addFirst(stop);
            return;
        }
        int last = stops.size() - 1;
        while (stops.get(last).offset() > offset) {
            last--;
        }
        if (last > 0 && stops.get(last - 1).offset() == offset) {
            stops.set(last, stop);
            return;
        }
        stops.add(last + 1, stop);
    }

    @Override
    public int premultipliedAt(double x, double y) {
        return SolidFill.premultiply(colourAt(x, y));
    }

    @Override
    public boolean isOpaque() {
        return !stops.isEmpty() && stops.stream().allMatch(stop -> stop.argb() >>> 24 == OPAQUE_ALPHA);
    }

    @Override
    public boolean isEmpty() {
        return stops.isEmpty();
    }

    int colourAt(double x, double y) {
        if (stops.isEmpty()) {
            return 0;
        }
        double t = switch (kind) {
            case LINEAR -> measureAlongLine(x, y);
            case RADIAL -> measureOutFromFocus(x, y);
            case CONIC -> measureAroundCentre(x, y);
        };
        if (Double.isNaN(t)) {
            return 0;
        }
        return interpolate(extendBeyondEnds(t));
    }

    private double measureAlongLine(double x, double y) {
        double dx = values[2] - values[0];
        double dy = values[3] - values[1];
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return 0;
        }
        return ((x - values[0]) * dx + (y - values[1]) * dy) / lengthSquared;
    }

    private double measureOutFromFocus(double x, double y) {
        double toCentreX = values[0] - values[2];
        double toCentreY = values[1] - values[3];
        double pointX = x - values[2];
        double pointY = y - values[3];
        double radius = values[4];
        double a = toCentreX * toCentreX + toCentreY * toCentreY - radius * radius;
        double b = pointX * toCentreX + pointY * toCentreY;
        double c = pointX * pointX + pointY * pointY;
        if (a == 0) {
            return b == 0 ? Double.NaN : c / (2 * b);
        }
        double discriminant = b * b - a * c;
        if (discriminant < 0) {
            return Double.NaN;
        }
        return (b - Math.sqrt(discriminant)) / a;
    }

    private double measureAroundCentre(double x, double y) {
        double angle = Math.atan2(y - values[1], x - values[0]) - values[2];
        return angle / TURN * values[3];
    }

    private double extendBeyondEnds(double t) {
        return switch (extend) {
            case PAD -> Math.clamp(t, 0.0, 1.0);
            case REPEAT -> t - Math.floor(t);
            case REFLECT -> {
                double folded = t - 2 * Math.floor(t / 2);
                yield folded > 1 ? 2 - folded : folded;
            }
        };
    }

    private int interpolate(double t) {
        Stop first = stops.getFirst();
        if (t <= first.offset()) {
            return first.argb();
        }
        for (int at = 1; at < stops.size(); at++) {
            Stop after = stops.get(at);
            if (t <= after.offset()) {
                Stop before = stops.get(at - 1);
                double span = after.offset() - before.offset();
                double weight = span == 0 ? 1 : (t - before.offset()) / span;
                return blend(before.argb(), after.argb(), weight);
            }
        }
        return stops.getLast().argb();
    }

    private static int blend(int from, int to, double weight) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int start = (from >>> shift) & 0xFF;
            int end = (to >>> shift) & 0xFF;
            int channel = (int) Math.round(start + (end - start) * weight);
            result |= Math.clamp(channel, 0, 255) << shift;
        }
        return result;
    }
}
