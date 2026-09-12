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
