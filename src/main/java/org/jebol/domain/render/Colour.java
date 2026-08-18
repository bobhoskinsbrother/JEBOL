package org.jebol.domain.render;

import org.jebol.domain.value.TupleValue;

/**
 * A colour, as three octets.
 *
 * <p>Opacity is not here. A gob's own alpha, its ancestors' alphas and the
 * fourth octet of its colour all multiply into one number, and that number
 * belongs on the {@link Placement} because it applies to a picture as much as
 * to a fill. Keeping it in two places is how the two get out of step.
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
     * How opaque a colour tuple says it is, out of 255.
     *
     * <p>The fourth octet, and it runs the same way an opacity does: 255 is
     * opaque. The C settles it where it decides whether a gob can be painted
     * over -- {@code if (VAL_TUPLE_LEN(val) < 4 || VAL_TUPLE(val)[3] == 255)
     * SET_GOB_OPAQUE(gob);} -- and a tuple written with three octets is
     * stored with a fourth of 255, so a colour with no opacity named is
     * opaque.
     */
    public static int opacityOfTuple(TupleValue parts) {
        return parts.segments().length >= 4 ? parts.octetAt(4) : WIDEST_OCTET;
    }

    /** As {@code #rrggbb}, which is what a browser wants. */
    public String asHexTriplet() {
        return String.format("#%02x%02x%02x", red, green, blue);
    }
}
