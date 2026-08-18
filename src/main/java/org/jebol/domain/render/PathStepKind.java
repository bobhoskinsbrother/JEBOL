package org.jebol.domain.render;

import java.util.Locale;

/**
 * The kinds of step a path is made of.
 *
 * <p>Named apart from the record types so a renderer that sends a path
 * somewhere has a word to send. Specified in {@code spec/draw.allium}.
 */
public enum PathStepKind {

    MOVE_TO,
    LINE_TO,
    QUADRATIC_TO,
    CUBIC_TO,
    ELLIPSE_AT,
    ARC_TO,
    CLOSE;

    /** The word this step travels under. */
    public String spelling() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
