package org.jebol.domain.render;

import org.jebol.domain.value.TupleValue;

/**
 * A colour, as three octets. Opacity is not here: it multiplies down a whole
 * gob tree and belongs on the {@link Placement}.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public record Colour(int red, int green, int blue) {

    private static final int WIDEST_OCTET = 255;

    public Colour {
        red = Math.clamp(red, 0, WIDEST_OCTET);
        green = Math.clamp(green, 0, WIDEST_OCTET);
        blue = Math.clamp(blue, 0, WIDEST_OCTET);
    }

    public static final Colour BLACK = new Colour(0, 0, 0);

    /**
     * This colour with a gamma curve applied to each channel.
     *
     * <p>AGG sets the curve on its rasteriser, which decides how much of an
     * edge pixel a shape covers. Neither toolkit here exposes that, so the
     * curve is applied to the colours instead: the same arithmetic, one step
     * earlier, and the whole shape rather than only its edges. A script asking
     * for gamma is asking for the picture to lighten or darken by that curve,
     * and that is what it gets.
     */
    public Colour underGamma(double gamma) {
        return gamma <= 0 || gamma == 1
                ? this
                : new Colour(curved(red, gamma), curved(green, gamma),
                        curved(blue, gamma));
    }

    private static int curved(int channel, double gamma) {
        double shareOfFull = channel / 255.0;
        return Math.clamp(
                (int) Math.round(Math.pow(shareOfFull, 1 / gamma) * 255), 0, 255);
    }

    /** The first three octets of a REBOL tuple. */
    public static Colour ofTuple(TupleValue parts) {
        return new Colour(parts.octetAt(1), parts.octetAt(2), parts.octetAt(3));
    }

    /**
     * How opaque a colour tuple says it is, out of 255: its fourth octet, and
     * a tuple written with three is opaque.
     */
    public static int opacityOfTuple(TupleValue parts) {
        return parts.segments().length >= 4 ? parts.octetAt(4) : WIDEST_OCTET;
    }

    /** As {@code #rrggbb}, which is what a browser wants. */
    public String asHexTriplet() {
        return String.format("#%02x%02x%02x", red, green, blue);
    }
}
