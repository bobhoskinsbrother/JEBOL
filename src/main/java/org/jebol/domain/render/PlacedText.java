package org.jebol.domain.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record PlacedText(
        List<PlacedLine> lines,
        List<PlacedPatch> selection,
        Optional<PlacedCaret> caret,
        Optional<PlacedShadow> shadow) {

    public PlacedText {
        lines = List.copyOf(lines);
        selection = List.copyOf(selection);
    }

    public record PlacedLine(List<PlacedPiece> pieces) {

        public PlacedLine {
            pieces = List.copyOf(pieces);
        }
    }

    public record PlacedPiece(double across, double baseline, TextRun font, String text) {
    }

    public record PlacedPatch(double across, double top, double wide, double high) {
    }

    public record PlacedCaret(double across, double top, double wide, double high, Colour colour) {
    }

    public record PlacedShadow(int across, int down, Colour colour) {
    }

    public static final Colour THE_COLOUR_A_SELECTION_IS_MARKED_IN = new Colour(170, 200, 245);

    private static final double THE_WIDTH_OF_A_CARET = 1;

    public static PlacedText of(PaintInstruction.Writing written, TextMeasurement measure) {
        Placement where = written.where();
        TextLayout layout = written.layout();
        TextLines lines = new TextLines(written.runs(), measure, layout.measureRoomForEachLine(where));
        return new PlacedText(
                placeTheLinesOf(lines, layout, where),
                findTheSelectionIn(written.caret(), lines, layout, where),
                findTheCaretIn(written, lines, layout, where),
                placeTheShadowOf(layout));
    }

    private static List<PlacedLine> placeTheLinesOf(
            TextLines lines, TextLayout layout, Placement where) {

        double top = layout.findWhereTheStackStarts(where, lines.tallness());
        List<PlacedLine> placed = new ArrayList<>();
        for (TextLines.Line line : lines.lines()) {
            double along = layout.findWhereALineStarts(where, line.wide());
            double baseline = top + line.ascent();
            List<PlacedPiece> pieces = new ArrayList<>();
            for (TextLines.Piece piece : line.pieces()) {
                pieces.add(new PlacedPiece(along, baseline, piece.font(), piece.text()));
                along += piece.wide();
            }
            placed.add(new PlacedLine(pieces));
            top += line.high();
        }
        return placed;
    }

    private static List<PlacedPatch> findTheSelectionIn(
            TextCaret caret, TextLines lines, TextLayout layout, Placement where) {

        if (!caret.marksSelection()) {
            return List.of();
        }
        TextLines.CaretPlace from = lines.findWhereTheCaretIs(
                caret.selectionFrom().run(), caret.selectionFrom().character(), layout, where);
        TextLines.CaretPlace to = lines.findWhereTheCaretIs(
                caret.selectionTo().run(), caret.selectionTo().character(), layout, where);
        TextLines.CaretPlace first = from.top() < to.top()
                || (from.top() == to.top() && from.across() <= to.across()) ? from : to;
        TextLines.CaretPlace last = first == from ? to : from;
        if (first.top() == last.top()) {
            return List.of(makePatch(first.across(), first.top(), last.across(), first.top() + first.high()));
        }
        return List.of(
                makePatch(first.across(), first.top(), where.across() + where.wide(), first.top() + first.high()),
                makePatch(where.across(), first.top() + first.high(), where.across() + where.wide(), last.top()),
                makePatch(where.across(), last.top(), last.across(), last.top() + last.high()));
    }

    private static PlacedPatch makePatch(double left, double top, double right, double bottom) {
        return new PlacedPatch(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private static Optional<PlacedCaret> findTheCaretIn(
            PaintInstruction.Writing written, TextLines lines, TextLayout layout, Placement where) {

        TextCaret caret = written.caret();
        if (!caret.isShown()) {
            return Optional.empty();
        }
        TextLines.CaretPlace placed = lines.findWhereTheCaretIs(caret.run(), caret.character(), layout, where);
        Colour inked = caret.run() < written.runs().size()
                ? written.runs().get(caret.run()).colour()
                : Colour.BLACK;
        return Optional.of(new PlacedCaret(placed.across(), placed.top(),
                THE_WIDTH_OF_A_CARET, placed.high(), inked));
    }

    private static Optional<PlacedShadow> placeTheShadowOf(TextLayout layout) {
        return layout.castsShadow()
                ? Optional.of(new PlacedShadow(layout.shadowAcross(), layout.shadowDown(), Colour.BLACK))
                : Optional.empty();
    }
}
