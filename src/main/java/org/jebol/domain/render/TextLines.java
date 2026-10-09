package org.jebol.domain.render;

import java.util.ArrayList;
import java.util.List;

public final class TextLines {

    private static final int A_NEWLINE = '\n';

    public record RunAndCharacter(int run, int character) {

        public static final RunAndCharacter NOWHERE = new RunAndCharacter(-1, -1);

        public boolean isNowhere() {
            return run < 0;
        }
    }

    public record CaretPlace(double across, double top, double high) {
    }

    public record Piece(int run, int from, String text, TextRun font, double wide) {

        int length() {
            return text.codePointCount(0, text.length());
        }

        boolean holds(RunAndCharacter caret) {
            return caret.run() == run && caret.character() >= from && caret.character() <= from + length();
        }
    }

    public record Line(List<Piece> pieces, double wide, double ascent, double descent) {

        public double high() {
            return ascent + descent;
        }
    }

    private final List<TextRun> runs;
    private final TextMeasure measure;
    private final List<Line> lines;

    public TextLines(List<TextRun> runs, TextMeasure measure) {
        this.runs = List.copyOf(runs);
        this.measure = measure;
        this.lines = List.copyOf(theLinesOf(this.runs));
    }

    public List<Line> lines() {
        return lines;
    }

    public double widest() {
        return lines.stream().mapToDouble(Line::wide).max().orElse(0);
    }

    public double tallness() {
        return lines.stream().mapToDouble(Line::high).sum();
    }

    public double topOfLine(int index, TextLayout layout, Placement box) {
        double top = layout.whereTheStackStarts(box, tallness());
        for (int above = 0; above < index; above++) {
            top += lines.get(above).high();
        }
        return top;
    }

    public double startOfLine(int index, TextLayout layout, Placement box) {
        return layout.whereALineStarts(box, lines.get(index).wide());
    }

    public CaretPlace whereTheCaretIs(int run, int character, TextLayout layout, Placement box) {
        if (lines.isEmpty()) {
            return new CaretPlace(layout.whereALineStarts(box, 0), layout.whereTheStackStarts(box, 0), 0);
        }
        RunAndCharacter caret = heldWithinTheText(run, character);
        for (int index = 0; index < lines.size(); index++) {
            double across = startOfLine(index, layout, box);
            for (Piece piece : lines.get(index).pieces()) {
                if (piece.holds(caret)) {
                    return new CaretPlace(
                            across + widthOf(piece, caret.character() - piece.from()),
                            topOfLine(index, layout, box),
                            lines.get(index).high());
                }
                across += piece.wide();
            }
        }
        int last = lines.size() - 1;
        return new CaretPlace(startOfLine(last, layout, box) + lines.get(last).wide(),
                topOfLine(last, layout, box), lines.get(last).high());
    }

    public RunAndCharacter caretNearest(double across, double down, TextLayout layout, Placement box) {
        if (lines.isEmpty()) {
            return RunAndCharacter.NOWHERE;
        }
        int index = theLineLevelWith(down, layout, box);
        RunAndCharacter nearest = RunAndCharacter.NOWHERE;
        double nearestDistance = Double.MAX_VALUE;
        double along = startOfLine(index, layout, box);
        for (Piece piece : lines.get(index).pieces()) {
            for (int within = 0; within <= piece.length(); within++) {
                double distance = Math.abs(across - (along + widthOf(piece, within)));
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearest = new RunAndCharacter(piece.run(), piece.from() + within);
                }
            }
            along += piece.wide();
        }
        return nearest;
    }

    private int theLineLevelWith(double down, TextLayout layout, Placement box) {
        for (int index = 0; index < lines.size(); index++) {
            if (down < topOfLine(index, layout, box) + lines.get(index).high()) {
                return index;
            }
        }
        return lines.size() - 1;
    }

    private RunAndCharacter heldWithinTheText(int run, int character) {
        if (run < 0 || run >= runs.size()) {
            return new RunAndCharacter(runs.size(), 0);
        }
        int length = runs.get(run).text().codePointCount(0, runs.get(run).text().length());
        return new RunAndCharacter(run, Math.clamp(character, 0, length));
    }

    private double widthOf(Piece piece, int characters) {
        if (characters <= 0) {
            return 0;
        }
        if (characters >= piece.length()) {
            return piece.wide();
        }
        int end = piece.text().offsetByCodePoints(0, characters);
        return measure.extentOf(withText(piece.font(), piece.text().substring(0, end))).wide();
    }

    private TextRun withText(TextRun font, String text) {
        return new TextRun(text, font.colour(), font.size(), font.bold(), font.italic());
    }

    private List<Line> theLinesOf(List<TextRun> written) {
        List<Line> laid = new ArrayList<>();
        List<Piece> current = new ArrayList<>();
        for (int run = 0; run < written.size(); run++) {
            TextRun font = written.get(run);
            int[] characters = font.text().codePoints().toArray();
            int from = 0;
            for (int at = 0; at <= characters.length; at++) {
                boolean endsTheRun = at == characters.length;
                if (endsTheRun || characters[at] == A_NEWLINE) {
                    current.add(aPiece(run, from, new String(characters, from, at - from), font));
                    if (!endsTheRun) {
                        laid.add(aLineOf(current));
                        current = new ArrayList<>();
                        from = at + 1;
                    }
                }
            }
        }
        if (!current.isEmpty()) {
            laid.add(aLineOf(current));
        }
        return laid;
    }

    private Piece aPiece(int run, int from, String text, TextRun font) {
        return new Piece(run, from, text, font, measure.extentOf(withText(font, text)).wide());
    }

    private Line aLineOf(List<Piece> pieces) {
        double wide = 0;
        double ascent = 0;
        double descent = 0;
        for (Piece piece : pieces) {
            TextExtent extent = measure.extentOf(withText(piece.font(), piece.text()));
            wide += piece.wide();
            ascent = Math.max(ascent, extent.ascent());
            descent = Math.max(descent, extent.descent());
        }
        return new Line(List.copyOf(pieces), wide, ascent, descent);
    }
}
