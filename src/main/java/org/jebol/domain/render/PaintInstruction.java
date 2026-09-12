package org.jebol.domain.render;

import org.jebol.domain.value.ImageValue;

/**
 * One thing to paint, with everywhere it goes already worked out. A renderer
 * executes these and decides nothing.
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

    /** An image, pixel for pixel. */
    record Picture(Placement where, ImageValue pixels) implements PaintInstruction {

        @Override
        public PaintKind kind() {
            return PaintKind.PICTURE;
        }
    }

    /**
     * A path, painted with a stroke or a fill or both. Either may be absent; a
     * shape with neither is dropped before it reaches a renderer rather than
     * being drawn invisibly.
     */
    record Drawn(
            Placement where,
            java.util.List<PathStep> path,
            Transform transform,
            PaintState painted) implements PaintInstruction {

        public Drawn {
            path = java.util.List.copyOf(path);
        }

        @Override
        public PaintKind kind() {
            return PaintKind.DRAWING;
        }
    }
}
