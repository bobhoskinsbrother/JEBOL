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

    public static PlacedText of(PaintInstruction.Writing written, TextMeasure measure) {
        Placement where = written.where();
        TextLayout layout = written.layout();
        TextLines lines = new TextLines(written.runs(), measure, layout.roomForEachLine(where));
        return new PlacedText(
                placedLinesOf(lines, layout, where),
                theSelectionIn(written.caret(), lines, layout, where),
                theCaretIn(written, lines, layout, where),
                theShadowOf(layout));
    }

    private static List<PlacedLine> placedLinesOf(
            TextLines lines, TextLayout layout, Placement where) {

        double top = layout.whereTheStackStarts(where, lines.tallness());
        List<PlacedLine> placed = new ArrayList<>();
        for (TextLines.Line line : lines.lines()) {
            double along = layout.whereALineStarts(where, line.wide());
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

    private static List<PlacedPatch> theSelectionIn(
            TextCaret caret, TextLines lines, TextLayout layout, Placement where) {

        if (!caret.marksASelection()) {
            return List.of();
        }
        TextLines.CaretPlace from = lines.whereTheCaretIs(
                caret.selectionFrom().run(), caret.selectionFrom().character(), layout, where);
        TextLines.CaretPlace to = lines.whereTheCaretIs(
                caret.selectionTo().run(), caret.selectionTo().character(), layout, where);
        TextLines.CaretPlace first = from.top() < to.top()
                || (from.top() == to.top() && from.across() <= to.across()) ? from : to;
        TextLines.CaretPlace last = first == from ? to : from;
        if (first.top() == last.top()) {
            return List.of(aPatch(first.across(), first.top(), last.across(), first.top() + first.high()));
        }
        return List.of(
                aPatch(first.across(), first.top(), where.across() + where.wide(), first.top() + first.high()),
                aPatch(where.across(), first.top() + first.high(), where.across() + where.wide(), last.top()),
                aPatch(where.across(), last.top(), last.across(), last.top() + last.high()));
    }

    private static PlacedPatch aPatch(double left, double top, double right, double bottom) {
        return new PlacedPatch(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private static Optional<PlacedCaret> theCaretIn(
            PaintInstruction.Writing written, TextLines lines, TextLayout layout, Placement where) {

        TextCaret caret = written.caret();
        if (!caret.isShown()) {
            return Optional.empty();
        }
        TextLines.CaretPlace placed = lines.whereTheCaretIs(caret.run(), caret.character(), layout, where);
        Colour inked = caret.run() < written.runs().size()
                ? written.runs().get(caret.run()).colour()
                : Colour.BLACK;
        return Optional.of(new PlacedCaret(placed.across(), placed.top(),
                THE_WIDTH_OF_A_CARET, placed.high(), inked));
    }

    private static Optional<PlacedShadow> theShadowOf(TextLayout layout) {
        return layout.castsAShadow()
                ? Optional.of(new PlacedShadow(layout.shadowAcross(), layout.shadowDown(), Colour.BLACK))
                : Optional.empty();
    }
}
