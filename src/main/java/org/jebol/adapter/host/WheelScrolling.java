package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventType;

import java.util.Optional;

final class WheelScrolling {

    private static final int A_LINE_OF_SCROLLING = 30;
    private static final int A_PAGE_OF_SCROLLING = 10;

    private double turned;

    Optional<Turning> take(double wheelRotation, boolean aWholePage) {
        turned += wheelRotation;
        int whole = (int) turned;
        if (whole == 0) {
            return Optional.empty();
        }
        turned -= whole;
        return Optional.of(new Turning(
                aWholePage ? ScreenEventType.SCROLL_PAGE : ScreenEventType.SCROLL_LINE,
                -whole * (aWholePage ? A_PAGE_OF_SCROLLING : A_LINE_OF_SCROLLING)));
    }

    record Turning(ScreenEventType kind, int amount) {
    }
}
