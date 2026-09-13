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
        int wide = whole(root.storage().size().x());
        int high = whole(root.storage().size().y());
        return within(root, ClipRectangle.wholeSurface(wide, high), 0, drawDialect);
    }

    /** The same, clipped to a surface of a stated size. */
    public static PaintList onASurface(GobValue root, int wide, int high) {
        return within(root, ClipRectangle.wholeSurface(wide, high), 0, null);
    }

    /**
     * The whole screen: the root gob and every window under it, with the titles
     * of both left out.
     *
     * <p>The same {@code text} field means two things depending on where a gob
     * sits: content on an ordinary gob, and the title bar's words on a window
     * or on the screen gob itself.
     */
    public static PaintList ofTheScreen(
            GobValue root, int wide, int high, ObjectValue drawDialect) {

        return within(root, ClipRectangle.wholeSurface(wide, high),
                DEPTHS_WHOSE_TEXT_IS_A_TITLE, drawDialect);
    }

    /** One window and its contents, with its own title left out. */
    public static PaintList ofAWindow(GobValue window, ObjectValue drawDialect) {
        int wide = whole(window.storage().size().x());
        int high = whole(window.storage().size().y());
        return within(window, ClipRectangle.wholeSurface(wide, high), 1, drawDialect);
    }

    private static final int DEPTHS_WHOSE_TEXT_IS_A_TITLE = 2;

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

        int wide = whole(gob.size().x());
        int high = whole(gob.size().y());
        if (wide <= 0 || high <= 0) {
            return;
        }
        ClipRectangle own = within.overlapWith(
                new ClipRectangle(across, down, wide, high));
        if (own.isEmpty()) {
            return;
        }
        int opacity = multipliedOpacity(inheritedOpacity, gob.alpha());
        Placement where = new Placement(across, down, wide, high, own, opacity);

        if (gob.contentKind() == GobStorage.Content.DRAW) {
            gathered.addAll(
                    whatItsDrawBlockPaints(gob, where, wide, high, drawDialect));
        } else if (titledDepths <= 0 || !itsTextIsATitle(gob)) {
            instructionFor(gob, where).ifPresent(gathered::add);
        }

        for (Value child : gob.pane()) {
            if (child instanceof GobValue held) {
                gatherParentBeforeChildrenWhichIsWhatInFrontMeans(gathered,held.storage(),
                        across + whole(held.storage().offset().x()),
                        down + whole(held.storage().offset().y()),
                        own, opacity, titledDepths - 1, drawDialect);
            }
        }
    }

    private static boolean itsTextIsATitle(GobStorage gob) {
        return gob.contentKind() == GobStorage.Content.STRING
                || gob.contentKind() == GobStorage.Content.TEXT;
    }

    private static List<PaintInstruction> whatItsDrawBlockPaints(
            GobStorage gob, Placement where, int wide, int high,
            ObjectValue drawDialect) {

        if (drawDialect == null
                || !(gob.contentIfKind(GobStorage.Content.DRAW)
                        instanceof BlockValue block)) {
            return List.of();
        }
        return DrawDialect.instructionsFor(block, drawDialect, where, wide, high);
    }

    private static int multipliedOpacity(int inherited, int own) {
        return Math.round(inherited * Math.clamp(own, 0, Placement.OPAQUE)
                / (float) Placement.OPAQUE);
    }

    private static java.util.Optional<PaintInstruction> instructionFor(
            GobStorage gob, Placement where) {

        return switch (gob.contentKind()) {
            case COLOUR -> filledWith(gob.contentIfKind(GobStorage.Content.COLOUR), where);
            case STRING -> written(gob.contentIfKind(GobStorage.Content.STRING), where);
            case TEXT -> written(gob.contentIfKind(GobStorage.Content.TEXT), where);
            case IMAGE -> pictured(gob.contentIfKind(GobStorage.Content.IMAGE), where);
            case NONE, DRAW, EFFECT, WIDGET -> Optional.empty();
        };
    }

    private static java.util.Optional<PaintInstruction> filledWith(
            Value colour, Placement where) {

        if (!(colour instanceof TupleValue parts)) {
            return Optional.empty();
        }
        Placement showing = new Placement(
                where.across(), where.down(), where.wide(), where.high(), where.clip(),
                multipliedOpacity(where.opacity(), Colour.opacityOfTuple(parts)));
        return Optional.of(
                new PaintInstruction.Fill(showing, Colour.ofTuple(parts)));
    }

    private static java.util.Optional<PaintInstruction> written(
            Value held, Placement where) {

        String text = held instanceof StringValue said
                ? said.text()
                : Molder.form(held);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                PaintInstruction.Writing.plain(where, text, Colour.BLACK));
    }

    private static java.util.Optional<PaintInstruction> pictured(
            Value held, Placement where) {

        return held instanceof ImageValue pixels
                ? Optional.of(PaintInstruction.Picture.atItsOwnSize(where, pixels))
                : Optional.empty();
    }

    private static int whole(double measurement) {
        return (int) Math.round(measurement);
    }
}
