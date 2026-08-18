package org.jebol.domain.eval;

import org.jebol.domain.value.GobValue;

/**
 * Something that happened on the screen, waiting for the script to be told.
 *
 * <p>It exists because the screen's own thread and the interpreter's are not
 * the same thread. A toolkit calls a listener on its own, and an interpreter
 * is owned by one thread precisely so that series can share mutable storage
 * with nothing synchronising them. So the screen makes one of these and the
 * interpreter takes it later, inside WAIT.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public record ScreenEvent(ScreenEventKind kind, GobValue window) {

    public ScreenEvent {
        if (kind == null) {
            throw new IllegalArgumentException("an event needs a kind");
        }
    }
}
