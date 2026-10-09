package org.jebol.domain.render;

import org.jebol.domain.value.TupleValue;

import java.math.BigDecimal;

/**
 * A colour, as three octets and how opaque it is, 255 being solid. A gob's own
 * opacity is not here: it multiplies down a whole gob tree and belongs on the
 * {@link Placement}.
 *
 * <p>Specified in {@code spec/screen.allium} and {@code spec/draw.allium}.
 */
public record Colour(int red, int green, int blue, int opacity) {

    private static final int WIDEST_OCTET = 255;

    private static final int CSS_OPACITY_PLACES = 1000;

    public Colour {
        red = Math.clamp(red, 0, WIDEST_OCTET);
        green = Math.clamp(green, 0, WIDEST_OCTET);
        blue = Math.clamp(blue, 0, WIDEST_OCTET);
        opacity = Math.clamp(opacity, 0, WIDEST_OCTET);
    }

    public Colour(int red, int green, int blue) {
        this(red, green, blue, WIDEST_OCTET);
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
                        curved(blue, gamma), opacity);
    }

    private static int curved(int channel, double gamma) {
        double shareOfFull = channel / 255.0;
        return Math.clamp(
                (int) Math.round(Math.pow(shareOfFull, 1 / gamma) * 255), 0, 255);
    }

    public static Colour ofTuple(TupleValue parts) {
        return new Colour(parts.octetAt(1), parts.octetAt(2), parts.octetAt(3), opacityOfTuple(parts));
    }

    public Colour solid() {
        return new Colour(red, green, blue);
    }

    public boolean isSolid() {
        return opacity == WIDEST_OCTET;
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

    public String asCss() {
        if (isSolid()) {
            return asHexTriplet();
        }
        double share = Math.round(opacity * CSS_OPACITY_PLACES / (double) WIDEST_OCTET)
                / (double) CSS_OPACITY_PLACES;
        return "rgba(%d,%d,%d,%s)".formatted(red, green, blue, BigDecimal.valueOf(share).stripTrailingZeros().toPlainString());
    }
}
