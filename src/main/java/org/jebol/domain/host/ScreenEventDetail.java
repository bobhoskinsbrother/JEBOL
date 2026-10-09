package org.jebol.domain.host;

/**
 * What a window reported beside the kind of event: where it happened, or which
 * key it was.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public sealed interface ScreenEventDetail {

    /** A pointer's place in the drawable area, a new size, or a window's place. */
    record At(int across, int down) implements ScreenEventDetail {
    }

    /** A key that typed this character. */
    record Typed(int codepoint) implements ScreenEventDetail {
    }

    /** A key that types nothing, by its name in {@code system/catalog}'s key list. */
    record NamedKey(String name) implements ScreenEventDetail {
    }

    /** Nothing beside the kind: a close, or a key nothing names. */
    record NothingMore() implements ScreenEventDetail {
    }
}
