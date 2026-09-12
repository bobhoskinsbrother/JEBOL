package org.jebol.domain.render;

import java.util.Optional;

/**
 * What a shape is painted with, at the moment the dialect reaches it. A shape
 * takes the state as it stands without consuming it, and a stroke and a fill
 * are each optional -- a shape with neither paints nothing.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public record PaintState(
        Optional<Colour> strokeColour,
        Optional<Colour> fillColour,
        double lineWidth,
        LineCap lineCap,
        LineJoin lineJoin,
        FillRule fillRule,
        boolean antiAliased) {

    /** What a draw block starts with: a black line one pixel wide, no fill. */
    public static final PaintState AT_THE_START = new PaintState(
            Optional.of(Colour.BLACK), Optional.empty(), 1,
            LineCap.BUTT, LineJoin.MITER, FillRule.NON_ZERO, true);

    public boolean paintsNothing() {
        return strokeColour.isEmpty() && fillColour.isEmpty();
    }

    public PaintState withStroke(Optional<Colour> colour) {
        return new PaintState(colour, fillColour, lineWidth,
                lineCap, lineJoin, fillRule, antiAliased);
    }

    public PaintState withFill(Optional<Colour> colour) {
        return new PaintState(strokeColour, colour, lineWidth,
                lineCap, lineJoin, fillRule, antiAliased);
    }

    /** A width of zero or less is a width of one. */
    public PaintState withLineWidth(double width) {
        return new PaintState(strokeColour, fillColour, width > 0 ? width : 1,
                lineCap, lineJoin, fillRule, antiAliased);
    }

    public PaintState withLineCap(LineCap cap) {
        return new PaintState(strokeColour, fillColour, lineWidth,
                cap, lineJoin, fillRule, antiAliased);
    }

    public PaintState withLineJoin(LineJoin join) {
        return new PaintState(strokeColour, fillColour, lineWidth,
                lineCap, join, fillRule, antiAliased);
    }

    public PaintState withFillRule(FillRule rule) {
        return new PaintState(strokeColour, fillColour, lineWidth,
                lineCap, lineJoin, rule, antiAliased);
    }

    public PaintState withAntiAliasing(boolean smoothed) {
        return new PaintState(strokeColour, fillColour, lineWidth,
                lineCap, lineJoin, fillRule, smoothed);
    }
}
