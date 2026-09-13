package org.jebol.domain.render;

import org.jebol.domain.value.ImageValue;

/**
 * One thing to paint, with everywhere it goes already worked out. A renderer
 * executes these and decides nothing.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public sealed interface PaintInstruction {

    Placement where();

    PaintKind kind();

    record Fill(Placement where, Colour colour) implements PaintInstruction {

        @Override
        public PaintKind kind() {
            return PaintKind.FILL;
        }
    }

    record Writing(
            Placement where, String text, Colour colour,
            double size, boolean bold, boolean italic)
            implements PaintInstruction {

        /** The size a gob's own text is written at when nothing asks for one. */
        public static final double THE_ORDINARY_SIZE = 12;

        /** Plain, at the ordinary size: what a gob's own string is. */
        public static Writing plain(Placement where, String text, Colour colour) {
            return new Writing(where, text, colour, THE_ORDINARY_SIZE, false, false);
        }

        @Override
        public PaintKind kind() {
            return PaintKind.WRITING;
        }
    }

    record Picture(
            Placement where, ImageValue pixels,
            double wide, double high, Transform transform)
            implements PaintInstruction {

        /** At its own size and square to the surface, which is what a gob shows. */
        public static Picture atItsOwnSize(Placement where, ImageValue pixels) {
            return new Picture(where, pixels,
                    pixels.size().x(), pixels.size().y(), Transform.NONE);
        }

        @Override
        public PaintKind kind() {
            return PaintKind.PICTURE;
        }
    }

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
