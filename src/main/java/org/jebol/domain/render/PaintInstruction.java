package org.jebol.domain.render;

import org.jebol.domain.value.ImageValue;

/**
 * One thing to paint, with everywhere it goes already worked out.
 *
 * <p>A renderer executes these and decides nothing. That is what keeps a
 * desktop window, a phone and a browser showing the same picture: they are
 * not three walks over a gob tree that happen to agree, they are three
 * executions of one list.
 *
 * <p>Three kinds today, for the four gob contents that can be painted -- a
 * colour, a string, a rich-text block and an image. The DRAW dialect adds
 * kinds here rather than adding a walk to each renderer, which is thirty
 * commands written once instead of three times.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public sealed interface PaintInstruction {

    /** Where it goes, what it may cover, and how much shows through. */
    Placement where();

    PaintKind kind();

    /** A rectangle of one colour. */
    record Fill(Placement where, Colour colour) implements PaintInstruction {

        @Override
        public PaintKind kind() {
            return PaintKind.FILL;
        }
    }

    /** A line of characters, drawn from the top left of the placement. */
    record Writing(Placement where, String text, Colour colour)
            implements PaintInstruction {

        @Override
        public PaintKind kind() {
            return PaintKind.WRITING;
        }
    }

    /**
     * An image, pixel for pixel.
     *
     * <p>The one instruction that carries a REBOL value rather than numbers,
     * because a picture is what it holds. A renderer that has to send this
     * somewhere turns the pixels into whatever its transport carries.
     */
    record Picture(Placement where, ImageValue pixels) implements PaintInstruction {

        @Override
        public PaintKind kind() {
            return PaintKind.PICTURE;
        }
    }
}
