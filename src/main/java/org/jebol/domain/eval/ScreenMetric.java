package org.jebol.domain.eval;

import java.util.Optional;

/**
 * What GUI-METRIC can be asked for.
 *
 * <p>Twelve, and both of Rebol's hosts serve exactly these twelve:
 * {@code host-window.c} on posix dispatches them and the win32 one answers the
 * same list a different way. {@code virtual-screen-size} is in the word list
 * {@code boot/window.reb} hands the host and is in neither host's switch, so
 * asking for it is refused everywhere and it is not a member here.
 *
 * <p>Eleven measure something and answer a pair. {@link #SCREENS} counts and
 * answers an integer, which is why the C writes it into the frame and returns
 * before reaching the code that would have made a pair of it.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public enum ScreenMetric {

    SCREEN_SIZE("screen-size"),
    SCREEN_ORIGIN("screen-origin"),
    SCREEN_DPI("screen-dpi"),
    SCREENS("screens"),
    WORK_ORIGIN("work-origin"),
    WORK_SIZE("work-size"),
    TITLE_SIZE("title-size"),
    BORDER_SIZE("border-size"),
    BORDER_FIXED("border-fixed"),
    WINDOW_MIN_SIZE("window-min-size"),
    LOG_SIZE("log-size"),
    PHYS_SIZE("phys-size");

    private final String spelling;

    ScreenMetric(String spelling) {
        this.spelling = spelling;
    }

    /** The word a script writes for this metric. */
    public String spelling() {
        return spelling;
    }

    /** Whether this one counts rather than measures. */
    public boolean isACount() {
        return this == SCREENS;
    }

    /** The metric a word names, or empty for a word no host serves. */
    public static Optional<ScreenMetric> named(String canonical) {
        for (ScreenMetric metric : values()) {
            if (metric.spelling.equals(canonical)) {
                return Optional.of(metric);
            }
        }
        return Optional.empty();
    }
}
