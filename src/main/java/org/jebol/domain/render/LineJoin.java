package org.jebol.domain.render;

import java.util.Locale;
import java.util.Optional;

/**
 * How two segments meet. The dialect's own four words.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public enum LineJoin {

    MITER,
    MITER_BEVEL,
    ROUND,
    BEVEL;

    /** The word a draw block writes for this. */
    public String spelling() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The one a word names, or empty when no word names one. */
    public static Optional<LineJoin> named(String canonical) {
        for (LineJoin each : values()) {
            if (each.spelling().equals(canonical)) {
                return Optional.of(each);
            }
        }
        return Optional.empty();
    }
}
