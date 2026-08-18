package org.jebol.domain.render;

import java.util.Locale;
import java.util.Optional;

/**
 * How the inside of a path is decided where it crosses itself.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public enum FillRule {

    NON_ZERO,
    EVEN_ODD;

    /** The word a draw block writes for this. */
    public String spelling() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The one a word names, or empty when no word names one. */
    public static Optional<FillRule> named(String canonical) {
        for (FillRule each : values()) {
            if (each.spelling().equals(canonical)) {
                return Optional.of(each);
            }
        }
        return Optional.empty();
    }
}
