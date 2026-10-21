package org.jebol.domain.render;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One gob tree, walked once, as the thing every renderer is handed.
 *
 * <p>The walk happens here and nowhere else: a renderer executes the list and
 * decides nothing, so two renderers cannot disagree about where a thing goes.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public record PaintList(List<PaintInstruction> instructions) {

    public PaintList {
        instructions = List.copyOf(instructions);
    }

    public int count() {
        return instructions.size();
    }

    public boolean isEmpty() {
        return instructions.isEmpty();
    }

    /**
     * A gob tree flattened, clipped to the gob's own area, with no dialect --
     * so a gob carrying a draw block paints nothing.
     */
    public static PaintList of(GobValue root) {
        return of(root, null);
    }

    /** The same, reading any draw block it meets against a dialect. */
    public static PaintList of(GobValue root, ObjectValue drawDialect) {
        int wide = roundToWhole(root.storage().size().x());
        int high = roundToWhole(root.storage().size().y());
        return within(root, ClipRectangle.coverWholeSurface(wide, high), 0, drawDialect);
    }

    /** The same, clipped to a surface of a stated size. */
    public static PaintList onSurface(GobValue root, int wide, int high) {
        return within(root, ClipRectangle.coverWholeSurface(wide, high), 0, null);
    }

    /**
     * The whole screen: the root gob and every window under it, with the titles
     * of both left out.
     *
     * <p>The same {@code text} field means two things depending on where a gob
     * sits: content on an ordinary gob, and the title bar's words on a window
     * or on the screen gob itself.
     */
    public static PaintList ofScreen(
            GobValue root, int wide, int high, ObjectValue drawDialect) {

        return within(root, ClipRectangle.coverWholeSurface(wide, high),
                DEPTHS_WHOSE_TEXT_IS_A_TITLE, drawDialect);
    }

    /** One window and its contents, with its own title left out. */
    public static PaintList ofWindow(GobValue window, ObjectValue drawDialect) {
        int wide = roundToWhole(window.storage().size().x());
        int high = roundToWhole(window.storage().size().y());
        return within(window, ClipRectangle.coverWholeSurface(wide, high), 1, drawDialect);
    }

    private static final int DEPTHS_WHOSE_TEXT_IS_A_TITLE = 2;

    public List<DrawingCommand> calls(TextMeasurement measure) {
        List<DrawingCommand> calls = new ArrayList<>();
        for (PaintInstruction instruction : instructions) {
            switch (instruction) {
                case PaintInstruction.Fill filled -> calls.add(asRectangleCall(filled));
                case PaintInstruction.Writing written -> calls.add(
                        new DrawingCommand.Text(written.where(), PlacedText.of(written, measure)));
                case PaintInstruction.Picture shown -> calls.add(new DrawingCommand.Picture(
                        shown.where(), shown.pixels(), shown.wide(), shown.high(), shown.transform()));
                case PaintInstruction.Drawn drawing -> addWhatIsDrawn(calls, drawing);
            }
        }
        return calls;
    }

    public void executeOn(Surface surface, TextMeasurement measure) {
        calls(measure).forEach(surface::execute);
    }

    private static DrawingCommand asRectangleCall(PaintInstruction.Fill filled) {
        Placement where = filled.where();
        double left = where.across();
        double top = where.down();
        double right = left + where.wide();
        double bottom = top + where.high();
        return new DrawingCommand.Fill(where,
                List.of(
                        new PathStep.MoveTo(left, top),
                        new PathStep.LineTo(right, top),
                        new PathStep.LineTo(right, bottom),
                        new PathStep.LineTo(left, bottom),
                        new PathStep.Close()),
                FillRule.NON_ZERO, Optional.empty(), filled.colour(), Transform.NONE,
                true);
    }

    private static void addWhatIsDrawn(
            List<DrawingCommand> calls, PaintInstruction.Drawn drawing) {

        PaintState painted = drawing.painted();
        if (painted.fillGradient().isPresent()) {
            calls.add(new DrawingCommand.Fill(drawing.where(), drawing.path(),
                    painted.fillRule(), painted.fillGradient(), Colour.BLACK,
                    drawing.transform(), painted.isAntiAlias()));
        } else if (painted.fillColour().isPresent()) {
            calls.add(new DrawingCommand.Fill(drawing.where(), drawing.path(),
                    painted.fillRule(), Optional.empty(),
                    painted.fillColour().orElseThrow(), drawing.transform(),
                    painted.isAntiAlias()));
        }
        if (painted.strokeColour().isPresent()) {
            calls.add(new DrawingCommand.Stroke(
                    drawing.where(), drawing.path(), painted, drawing.transform()));
        }
    }

    private static PaintList within(
            GobValue root, ClipRectangle surface, int titledDepths,
            ObjectValue drawDialect) {

        List<PaintInstruction> gathered = new ArrayList<>();
        gatherParentBeforeChildrenWhichIsWhatInFrontMeans(gathered,root.storage(), 0, 0, surface, Placement.OPAQUE,
                titledDepths, drawDialect);
        return new PaintList(gathered);
    }

    private static void gatherParentBeforeChildrenWhichIsWhatInFrontMeans(
            List<PaintInstruction> gathered, GobStorage gob,
            int across, int down, ClipRectangle within, int inheritedOpacity,
            int titledDepths, ObjectValue drawDialect) {

        int wide = roundToWhole(gob.size().x());
        int high = roundToWhole(gob.size().y());
        if (wide <= 0 || high <= 0) {
            return;
        }
        ClipRectangle own = within.overlapWith(
                new ClipRectangle(across, down, wide, high));
        if (own.isEmpty()) {
            return;
        }
        int opacity = multiplyOpacity(inheritedOpacity, gob.alpha());
        Placement where = new Placement(across, down, wide, high, own, opacity);

        if (gob.contentKind() == GobStorage.Content.DRAW) {
            gathered.addAll(
                    paintItsDrawBlock(gob, where, wide, high, drawDialect));
        } else if (gob.contentKind() == GobStorage.Content.TEXT
                && gob.readContentIfKind(GobStorage.Content.TEXT) instanceof AnyBlockValue richText) {
            if (titledDepths <= 0) {
                gathered.addAll(writeAsRichText(richText, where));
            }
        } else if (titledDepths <= 0 || !hasTitleForItsText(gob)) {
            makeInstructionFor(gob, where).ifPresent(gathered::add);
        }

        for (Value child : gob.pane()) {
            if (child instanceof GobValue held) {
                gatherParentBeforeChildrenWhichIsWhatInFrontMeans(gathered,held.storage(),
                        across + roundToWhole(held.storage().offset().x()),
                        down + roundToWhole(held.storage().offset().y()),
                        own, opacity, titledDepths - 1, drawDialect);
            }
        }
    }

    private static boolean hasTitleForItsText(GobStorage gob) {
        return gob.contentKind() == GobStorage.Content.STRING
                || gob.contentKind() == GobStorage.Content.TEXT;
    }

    private static List<PaintInstruction> paintItsDrawBlock(
            GobStorage gob, Placement where, int wide, int high,
            ObjectValue drawDialect) {

        if (drawDialect == null
                || !(gob.readContentIfKind(GobStorage.Content.DRAW)
                        instanceof AnyBlockValue block)) {
            return List.of();
        }
        return new DrawDialect().readTheInstructionsFor(block, drawDialect, where, wide, high);
    }

    private static int multiplyOpacity(int inherited, int own) {
        return Math.round(inherited * Math.clamp(own, 0, Placement.OPAQUE)
                / (float) Placement.OPAQUE);
    }

    private static Optional<PaintInstruction> makeInstructionFor(
            GobStorage gob, Placement where) {

        return switch (gob.contentKind()) {
            case COLOUR -> fillWith(gob.readContentIfKind(GobStorage.Content.COLOUR), where);
            case STRING -> write(gob.readContentIfKind(GobStorage.Content.STRING), where);
            case TEXT -> write(gob.readContentIfKind(GobStorage.Content.TEXT), where);
            case IMAGE -> picture(gob.readContentIfKind(GobStorage.Content.IMAGE), where);
            case NONE, DRAW, EFFECT, WIDGET -> Optional.empty();
        };
    }

    private static Optional<PaintInstruction> fillWith(
            Value colour, Placement where) {

        if (!(colour instanceof TupleValue parts)) {
            return Optional.empty();
        }
        Placement showing = new Placement(
                where.across(), where.down(), where.wide(), where.high(), where.clip(),
                multiplyOpacity(where.opacity(), Colour.readTheOpacityOfTuple(parts)));
        return Optional.of(
                new PaintInstruction.Fill(showing, Colour.ofTuple(parts).solid()));
    }

    private static Optional<PaintInstruction> write(
            Value held, Placement where) {

        String text = held instanceof AnyStringValue said
                ? said.text()
                : Molder.form(held);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                PaintInstruction.Writing.writePlainly(where, text, Colour.BLACK));
    }

    private static List<PaintInstruction> writeAsRichText(AnyBlockValue richText, Placement where) {
        RichText.Line line = new RichText(Colour.BLACK).lineIn(richText);
        return line.runs().isEmpty()
                ? List.of()
                : List.of(new PaintInstruction.Writing(where, line.runs(), line.layout(), line.caret()));
    }

    private static Optional<PaintInstruction> picture(
            Value held, Placement where) {

        return held instanceof ImageValue pixels
                ? Optional.of(PaintInstruction.Picture.atItsOwnSize(where, pixels))
                : Optional.empty();
    }

    private static int roundToWhole(double measurement) {
        return (int) Math.round(measurement);
    }
}
