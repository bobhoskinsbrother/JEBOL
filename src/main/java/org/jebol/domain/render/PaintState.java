package org.jebol.domain.render;

import java.util.List;
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
        boolean antiAliased,
        List<Double> dashes,
        Optional<Gradient> fillGradient,
        ArrowEnds arrowEnds) {

    public PaintState {
        dashes = List.copyOf(dashes);
    }

    /** What a draw block starts with: a black line one pixel wide, no fill. */
    public static final PaintState AT_THE_START = new PaintState(
            Optional.of(Colour.BLACK), Optional.empty(), 1,
            LineCap.BUTT, LineJoin.MITER, FillRule.NON_ZERO, true,
            List.of(), Optional.empty(), ArrowEnds.NEITHER);

    public boolean paintsNothing() {
        return strokeColour.isEmpty() && fillColour.isEmpty()
                && fillGradient.isEmpty();
    }

    public PaintState withDashes(List<Double> lengths) {
        return new PaintState(strokeColour, fillColour, lineWidth, lineCap,
                lineJoin, fillRule, antiAliased, lengths, fillGradient, arrowEnds);
    }

    public PaintState withFillGradient(Optional<Gradient> gradient) {
        return new PaintState(strokeColour, fillColour, lineWidth, lineCap,
                lineJoin, fillRule, antiAliased, dashes, gradient, arrowEnds);
    }

    public PaintState withArrowEnds(ArrowEnds ends) {
        return new PaintState(strokeColour, fillColour, lineWidth, lineCap,
                lineJoin, fillRule, antiAliased, dashes, fillGradient, ends);
    }

    public PaintState withStroke(Optional<Colour> colour) {
        return new PaintState(colour, fillColour, lineWidth,
                lineCap, lineJoin, fillRule, antiAliased, dashes, fillGradient,
                arrowEnds);
    }

    public PaintState withFill(Optional<Colour> colour) {
        return new PaintState(strokeColour, colour, lineWidth,
                lineCap, lineJoin, fillRule, antiAliased, dashes, fillGradient,
                arrowEnds);
    }

    /** A width of zero or less is a width of one. */
    public PaintState withLineWidth(double width) {
        return new PaintState(strokeColour, fillColour, width > 0 ? width : 1,
                lineCap, lineJoin, fillRule, antiAliased, dashes, fillGradient,
                arrowEnds);
    }

    public PaintState withLineCap(LineCap cap) {
        return new PaintState(strokeColour, fillColour, lineWidth,
                cap, lineJoin, fillRule, antiAliased, dashes, fillGradient,
                arrowEnds);
    }

    public PaintState withLineJoin(LineJoin join) {
        return new PaintState(strokeColour, fillColour, lineWidth,
                lineCap, join, fillRule, antiAliased, dashes, fillGradient,
                arrowEnds);
    }

    public PaintState withFillRule(FillRule rule) {
        return new PaintState(strokeColour, fillColour, lineWidth,
                lineCap, lineJoin, rule, antiAliased, dashes, fillGradient,
                arrowEnds);
    }

    public PaintState withAntiAliasing(boolean smoothed) {
        return new PaintState(strokeColour, fillColour, lineWidth,
                lineCap, lineJoin, fillRule, smoothed, dashes, fillGradient,
                arrowEnds);
    }
}
