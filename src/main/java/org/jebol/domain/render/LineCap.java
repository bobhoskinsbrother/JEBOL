package org.jebol.domain.render;

import java.util.Locale;
import java.util.Optional;

/**
 * How a line ends. The dialect's own three words.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public enum LineCap {

    BUTT,
    SQUARE,
    ROUNDED;

    /** The word a draw block writes for this. */
    public String spelling() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The one a word names, or empty when no word names one. */
    public static Optional<LineCap> named(String canonical) {
        for (LineCap each : values()) {
            if (each.spelling().equals(canonical)) {
                return Optional.of(each);
            }
        }
        return Optional.empty();
    }
}
