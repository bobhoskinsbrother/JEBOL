package org.jebol.domain.render;

import java.util.ArrayList;
import java.util.List;

public final class TextLines {

    private static final int NEWLINE = '\n';

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
    private final TextMeasurement measure;
    private final double roomForEachLine;
    private final List<Line> lines;

    public TextLines(List<TextRun> runs, TextMeasurement measure) {
        this(runs, measure, Double.POSITIVE_INFINITY);
    }

    public TextLines(List<TextRun> runs, TextMeasurement measure, double roomForEachLine) {
        this.runs = List.copyOf(runs);
        this.measure = measure;
        this.roomForEachLine = roomForEachLine;
        this.lines = List.copyOf(breakIntoLines(this.runs));
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

    public double lineTop(int index, TextLayout layout, Placement box) {
        double top = layout.stackStartDown(box, tallness());
        for (int above = 0; above < index; above++) {
            top += lines.get(above).high();
        }
        return top;
    }

    public double startOfLine(int index, TextLayout layout, Placement box) {
        return layout.lineStartAcross(box, lines.get(index).wide());
    }

    public CaretPlace caretPlace(int run, int character, TextLayout layout, Placement box) {
        if (lines.isEmpty()) {
            return new CaretPlace(layout.lineStartAcross(box, 0), layout.stackStartDown(box, 0), 0);
        }
        RunAndCharacter caret = holdWithinText(run, character);
        for (int index = lines.size() - 1; index >= 0; index--) {
            double across = startOfLine(index, layout, box);
            for (Piece piece : lines.get(index).pieces()) {
                if (piece.holds(caret)) {
                    return new CaretPlace(
                            across + prefixWidth(piece, caret.character() - piece.from()),
                            lineTop(index, layout, box),
                            lines.get(index).high());
                }
                across += piece.wide();
            }
        }
        int last = lines.size() - 1;
        return new CaretPlace(startOfLine(last, layout, box) + lines.get(last).wide(),
                lineTop(last, layout, box), lines.get(last).high());
    }

    public RunAndCharacter findCaretNearest(double across, double down, TextLayout layout, Placement box) {
        if (lines.isEmpty()) {
            return RunAndCharacter.NOWHERE;
        }
        int index = findLineLevelWith(down, layout, box);
        RunAndCharacter nearest = RunAndCharacter.NOWHERE;
        double nearestDistance = Double.MAX_VALUE;
        double along = startOfLine(index, layout, box);
        for (Piece piece : lines.get(index).pieces()) {
            for (int within = 0; within <= piece.length(); within++) {
                double distance = Math.abs(across - (along + prefixWidth(piece, within)));
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearest = new RunAndCharacter(piece.run(), piece.from() + within);
                }
            }
            along += piece.wide();
        }
        return nearest;
    }

    private int findLineLevelWith(double down, TextLayout layout, Placement box) {
        for (int index = 0; index < lines.size(); index++) {
            if (down < lineTop(index, layout, box) + lines.get(index).high()) {
                return index;
            }
        }
        return lines.size() - 1;
    }

    private RunAndCharacter holdWithinText(int run, int character) {
        if (run < 0 || run >= runs.size()) {
            return new RunAndCharacter(runs.size(), 0);
        }
        int length = runs.get(run).text().codePointCount(0, runs.get(run).text().length());
        return new RunAndCharacter(run, Math.clamp(character, 0, length));
    }

    private double prefixWidth(Piece piece, int characters) {
        if (characters <= 0) {
            return 0;
        }
        if (characters >= piece.length()) {
            return piece.wide();
        }
        int end = piece.text().offsetByCodePoints(0, characters);
        return measure.textExtent(withText(piece.font(), piece.text().substring(0, end))).wide();
    }

    private TextRun withText(TextRun font, String text) {
        return new TextRun(text, font.colour(), font.size(), font.bold(), font.italic());
    }

    private record Letter(int run, int character, int codePoint) {

        private static final int STANDS_FOR_EMPTY_PIECE = -1;

        boolean standsForEmptyPiece() {
            return codePoint == STANDS_FOR_EMPTY_PIECE;
        }

        boolean isASpace() {
            return codePoint == ' ' || codePoint == '\t';
        }
    }

    private List<Line> breakIntoLines(List<TextRun> written) {
        List<Line> laid = new ArrayList<>();
        for (List<Piece> betweenNewlines : splitAtNewlines(written)) {
            laid.addAll(breakToFitRoom(betweenNewlines));
        }
        return laid;
    }

    private List<Line> breakToFitRoom(List<Piece> betweenNewlines) {
        List<Letter> letters = splitIntoLetters(betweenNewlines);
        if (roomForEachLine == Double.POSITIVE_INFINITY || letters.isEmpty()) {
            return List.of(makeLine(betweenNewlines));
        }
        List<Line> broken = new ArrayList<>();
        int lineStart = 0;
        int at = 0;
        while (at < letters.size()) {
            int wordEnd = wordEndFrom(letters, at);
            if (fits(letters.subList(lineStart, wordEnd))) {
                at = spaceRunEndFrom(letters, wordEnd);
            } else if (at > lineStart) {
                broken.add(makeLineFromLetters(letters.subList(lineStart, at)));
                lineStart = at;
            } else {
                int fitting = fittedWordEnd(letters, at, wordEnd);
                broken.add(makeLineFromLetters(letters.subList(at, fitting)));
                lineStart = fitting;
                at = fitting;
            }
        }
        if (lineStart < letters.size() || broken.isEmpty()) {
            broken.add(makeLineFromLetters(letters.subList(lineStart, letters.size())));
        }
        return broken;
    }

    private List<Letter> splitIntoLetters(List<Piece> pieces) {
        List<Letter> letters = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.text().isEmpty()) {
                letters.add(new Letter(piece.run(), piece.from(), Letter.STANDS_FOR_EMPTY_PIECE));
            }
            int[] characters = piece.text().codePoints().toArray();
            for (int index = 0; index < characters.length; index++) {
                letters.add(new Letter(piece.run(), piece.from() + index, characters[index]));
            }
        }
        return letters;
    }

    private int wordEndFrom(List<Letter> letters, int from) {
        int end = from;
        while (end < letters.size() && !letters.get(end).isASpace()) {
            end++;
        }
        return end;
    }

    private int spaceRunEndFrom(List<Letter> letters, int from) {
        int end = from;
        while (end < letters.size() && letters.get(end).isASpace()) {
            end++;
        }
        return end;
    }

    private int fittedWordEnd(List<Letter> letters, int from, int wordEnd) {
        int end = from + 1;
        while (end < wordEnd && fits(letters.subList(from, end + 1))) {
            end++;
        }
        return end;
    }

    private boolean fits(List<Letter> letters) {
        return piecesWidth(gatherIntoPieces(letters)) <= roomForEachLine;
    }

    private Line makeLineFromLetters(List<Letter> letters) {
        int printed = letters.size();
        while (printed > 0 && letters.get(printed - 1).isASpace()) {
            printed--;
        }
        return makeLine(gatherIntoPieces(letters), piecesWidth(gatherIntoPieces(letters.subList(0, printed))));
    }

    private double piecesWidth(List<Piece> pieces) {
        return pieces.stream().mapToDouble(Piece::wide).sum();
    }

    private List<Piece> gatherIntoPieces(List<Letter> letters) {
        List<Piece> pieces = new ArrayList<>();
        int start = 0;
        while (start < letters.size()) {
            int end = start + 1;
            while (end < letters.size() && letters.get(end).run() == letters.get(start).run()
                    && !letters.get(start).standsForEmptyPiece() && !letters.get(end).standsForEmptyPiece()) {
                end++;
            }
            pieces.add(pieceFromLetters(letters.subList(start, end)));
            start = end;
        }
        return pieces;
    }

    private Piece pieceFromLetters(List<Letter> letters) {
        Letter first = letters.getFirst();
        int[] characters = letters.stream()
                .mapToInt(Letter::codePoint)
                .filter(codePoint -> codePoint != Letter.STANDS_FOR_EMPTY_PIECE)
                .toArray();
        return makePiece(first.run(), first.character(), new String(characters, 0, characters.length),
                runs.get(first.run()));
    }

    private List<List<Piece>> splitAtNewlines(List<TextRun> written) {
        List<List<Piece>> laid = new ArrayList<>();
        List<Piece> current = new ArrayList<>();
        for (int run = 0; run < written.size(); run++) {
            TextRun font = written.get(run);
            int[] characters = font.text().codePoints().toArray();
            int from = 0;
            for (int at = 0; at <= characters.length; at++) {
                boolean endsRun = at == characters.length;
                if (endsRun || characters[at] == NEWLINE) {
                    current.add(makePiece(run, from, new String(characters, from, at - from), font));
                    if (!endsRun) {
                        laid.add(current);
                        current = new ArrayList<>();
                        from = at + 1;
                    }
                }
            }
        }
        if (!current.isEmpty()) {
            laid.add(current);
        }
        return laid;
    }

    private Piece makePiece(int run, int from, String text, TextRun font) {
        return new Piece(run, from, text, font, measure.textExtent(withText(font, text)).wide());
    }

    private Line makeLine(List<Piece> pieces) {
        return makeLine(pieces, piecesWidth(pieces));
    }

    private Line makeLine(List<Piece> pieces, double wide) {
        double ascent = 0;
        double descent = 0;
        for (Piece piece : pieces) {
            TextExtent extent = measure.textExtent(withText(piece.font(), piece.text()));
            ascent = Math.max(ascent, extent.ascent());
            descent = Math.max(descent, extent.descent());
        }
        return new Line(List.copyOf(pieces), wide, ascent, descent);
    }
}
