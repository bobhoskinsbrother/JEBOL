package org.jebol.domain.host;

import org.jebol.domain.value.GobValue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class ScreenEventQueue {

    private final Deque<ScreenEvent> queued = new ArrayDeque<>();

    public void add(ScreenEventType kind, GobValue window, ScreenEventDetail detail) {
        queued.add(new ScreenEvent(kind, window, detail));
    }

    public Optional<ScreenEvent> takeOldest() {
        return Optional.ofNullable(queued.poll());
    }
}
