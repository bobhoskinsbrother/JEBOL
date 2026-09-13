package org.jebol.domain.render;

import java.util.List;

/**
 * A run of colours to fill with instead of one colour.
 *
 * <p>Two kinds, because two is what both toolkits have: a linear gradient runs
 * along an angle, and a radial one runs out from a point. The stops are
 * fractions of the way along, one per colour and in order.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public record Gradient(
        GradientShape shape,
        double acrossOffset, double downOffset,
        double from, double to,
        double angle,
        List<Colour> colours,
        List<Double> stops) {

    public Gradient {
        colours = List.copyOf(colours);
        stops = List.copyOf(stops);
    }

    /** Where the run ends up, measured from its offset along the angle. */
    public double acrossEnd() {
        return acrossOffset + to * Math.cos(Math.toRadians(angle));
    }

    public double downEnd() {
        return downOffset + to * Math.sin(Math.toRadians(angle));
    }

    /** Whether a toolkit's own gradient carries this one. */
    public boolean radial() {
        return shape.isRadial();
    }

    /** Where the run starts, which is the offset itself for a radial one. */
    public double acrossStart() {
        return radial() ? acrossOffset
                : acrossOffset + from * Math.cos(Math.toRadians(angle));
    }

    public double downStart() {
        return radial() ? downOffset
                : downOffset + from * Math.sin(Math.toRadians(angle));
    }

    /** The radius a radial gradient reaches, which is never zero or less. */
    public double radius() {
        double reach = Math.abs(to - from);
        return reach > 0 ? reach : 1;
    }
}
