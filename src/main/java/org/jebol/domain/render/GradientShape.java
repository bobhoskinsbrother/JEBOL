package org.jebol.domain.render;

import java.util.Optional;
import java.util.Locale;

/**
 * The six ways the dialect can run a set of colours across a shape.
 *
 * <p>Three of them are the same run of colours along a line and differ only in
 * where the colours sit along it or which way the line points, so they are
 * worked out here and reach a renderer as an ordinary linear run. CONIC and
 * DIAMOND are not lines at all: they are drawn as a fan of wedges and a set of
 * rings, computed here as ordinary filled shapes, because a renderer that had
 * to invent them would invent a different one from the other renderer.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public enum GradientShape {

    LINEAR,

    RADIAL,

    DIAGONAL,

    CUBIC,

    CONIC,

    DIAMOND;

    public static Optional<GradientShape> named(String spelling) {
        for (GradientShape shape : values()) {
            if (shape.name().toLowerCase(Locale.ROOT).equals(spelling)) {
                return Optional.of(shape);
            }
        }
        return Optional.empty();
    }

    /** Whether a toolkit's own gradient can carry this one. */
    public boolean aToolkitCanDrawIt() {
        return this != CONIC && this != DIAMOND;
    }

    /** Which of the two gradients a toolkit has this becomes. */
    public boolean isRadial() {
        return this == RADIAL;
    }
}
