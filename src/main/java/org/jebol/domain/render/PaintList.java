package org.jebol.domain.render;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One gob tree, walked once, as the thing every renderer is handed.
 *
 * <p>Three renderers are wanted -- a desktop window, a phone and a browser --
 * and where the tree gets walked decides whether they stay alike. If each
 * renderer walks it, each has its own opinion about offsets, clipping and
 * opacity, and they drift apart in ways only somebody comparing screenshots
 * would notice.
 *
 * <p>So the walk happens here and nowhere else. A renderer executes the list
 * and decides nothing, which means two renderers cannot disagree about where
 * a thing goes: neither of them works it out.
 *
 * <p>What comparing pictures is still for, after this: glyph shapes and
 * anti-aliased edges, which no two rasterisers agree on and which no amount
 * of shared input fixes. Geometry and colour need no tolerance; text does.
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
     * A gob tree flattened, clipped to the gob's own area.
     *
     * <p>With no dialect, so a gob carrying a draw block paints nothing. Every
     * flattening that might meet one takes the dialect, and the ones that
     * cannot -- a lone coloured gob in a test -- do not have to invent it.
     */
    public static PaintList of(GobValue root) {
        return of(root, null);
    }

    /**
     * The same, reading any draw block it meets against a dialect.
     *
     * <p>The dialect is {@code system/dialects/draw} and it is passed rather
     * than reached, because flattening a gob tree has no way to a system
     * object and should not learn one. Threaded rather than held somewhere
     * shared, because a host runs many interpreters at once and each has its
     * own.
     */
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
     * The whole screen: the root gob and every window under it.
     *
     * <p>Different from {@link #of} in one thing, and it is the thing that
     * makes a page look right. The same {@code text} field means two things
     * depending on where a gob sits: on an ordinary gob it is content and gets
     * painted, and on a window it is the title bar's words. VIEW writes
     * {@code window/text: any [opts/title window/text "REBOL: untitled"]}, and
     * the screen gob itself carries {@code text: "Top Gob"}, which is a name
     * for the thing rather than anything anybody should see.
     *
     * <p>Painting them is a quiet failure: a window shows its own title across
     * its top left corner, in black, over whatever was meant to be there. It
     * was in the desktop renderer and nobody noticed, because no test gob had
     * any text.
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

    /**
     * How many levels down from where the walk starts hold titles rather than
     * content: the screen gob itself, and every window in its pane.
     */
    private static final int DEPTHS_WHOSE_TEXT_IS_A_TITLE = 2;

    private static PaintList within(
            GobValue root, ClipRectangle surface, int titledDepths,
            ObjectValue drawDialect) {

        List<PaintInstruction> gathered = new ArrayList<>();
        gather(gathered, root.storage(), 0, 0, surface, Placement.OPAQUE,
                titledDepths, drawDialect);
        return new PaintList(gathered);
    }

    /**
     * One gob and everything under it, parent first.
     *
     * <p>Parent before children because that is the whole of what "in front"
     * means, and the C's compositor relies on the same order. A list in the
     * wrong order is a picture with the wrong thing on top, which reads as a
     * bug in whichever renderer somebody happened to be looking at.
     */
    private static void gather(
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
                gather(gathered, held.storage(),
                        across + whole(held.storage().offset().x()),
                        down + whole(held.storage().offset().y()),
                        own, opacity, titledDepths - 1, drawDialect);
            }
        }
    }

    /**
     * Whether this gob's content is words for a title bar rather than
     * something to paint.
     *
     * <p>Only its text is. A window may carry a colour or an image and those
     * are painted as any other gob's would be, which matters because VIEW puts
     * a background in by inserting a gob rather than by colouring the window
     * -- so a window that does carry a colour was given one on purpose.
     */
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

    /**
     * Two opacities as one, which is what nesting them means.
     *
     * <p>A half-transparent gob inside another half-transparent one is a
     * quarter. Multiplied here rather than by each renderer, because
     * compositing is where toolkits differ most and the arithmetic is the part
     * that must not.
     */
    private static int multipliedOpacity(int inherited, int own) {
        return Math.round(inherited * Math.clamp(own, 0, Placement.OPAQUE)
                / (float) Placement.OPAQUE);
    }

    /**
     * What one gob paints, if anything.
     *
     * <p>Four of the eight content kinds paint. A draw block, an effect, a
     * native widget and nothing itself produce no instruction, and that gap is
     * the same gap in all three renderers because it is decided here rather
     * than three times.
     */
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
                new PaintInstruction.Writing(where, text, Colour.BLACK));
    }

    private static java.util.Optional<PaintInstruction> pictured(
            Value held, Placement where) {

        return held instanceof ImageValue pixels
                ? Optional.of(new PaintInstruction.Picture(where, pixels))
                : Optional.empty();
    }

    /** A gob's sizes and offsets are float pixels; a surface wants whole ones. */
    private static int whole(double measurement) {
        return (int) Math.round(measurement);
    }
}
