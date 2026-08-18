package org.jebol.domain.render;

import java.util.Optional;

/**
 * What a shape is painted with, at the moment the dialect reaches it.
 *
 * <p>A draw block is a sequence of state changes and shapes, and a shape takes
 * the state as it stands without consuming it. So {@code pen red box 0x0 10x10
 * box 20x20 30x30} draws two red boxes.
 *
 * <p>A stroke and a fill are each optional and independently so. A shape with
 * neither paints nothing, which is not a mistake to guard against: {@code pen
 * off fill-pen off} is a legal way to say it, and the C says the same thing by
 * passing NULL for both.
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

    /**
     * What a draw block starts with.
     *
     * <p>A black line one pixel wide and no fill, so a block that draws a box
     * without setting a pen still draws a visible box. No fill because an
     * unfilled outline is what somebody drawing a diagram expects, and a
     * filled one would hide whatever it was drawn over.
     */
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

    /**
     * A width of zero or less is a width of one.
     *
     * <p>{@code boot/draw.reb} says so in the declaration itself: "Zero, or
     * negative values, produce a line-width of 1."
     */
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
