package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventType;

import java.util.Optional;

final class WheelScrolling {

    private static final int LINE_SCROLL_PIXELS = 30;
    private static final int PAGE_SCROLL_PIXELS = 10;

    private double turned;

    Optional<Turning> take(double wheelRotation, boolean wholePage) {
        turned += wheelRotation;
        int whole = (int) turned;
        if (whole == 0) {
            return Optional.empty();
        }
        turned -= whole;
        return Optional.of(new Turning(
                wholePage ? ScreenEventType.SCROLL_PAGE : ScreenEventType.SCROLL_LINE,
                -whole * (wholePage ? PAGE_SCROLL_PIXELS : LINE_SCROLL_PIXELS)));
    }

    record Turning(ScreenEventType kind, int amount) {
    }
}
