package org.jebol.domain.render;

import java.util.Locale;

/**
 * The kinds of thing a paint list holds.
 *
 * <p>Named separately from the record types so a renderer that sends a list
 * somewhere has a word to send. Specified in {@code spec/screen.allium}.
 */
public enum PaintKind {

    FILL,
    WRITING,
    PICTURE,
    DRAWING;

    /** The word this kind travels under. */
    public String spelling() {
        return name().toLowerCase(Locale.ROOT);
    }
}
