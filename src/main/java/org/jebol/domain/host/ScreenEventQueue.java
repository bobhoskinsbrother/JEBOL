package org.jebol.domain.host;

import org.jebol.domain.value.GobValue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class ScreenEventQueue {

    private final Deque<ScreenEvent> queued = new ArrayDeque<>();

    public synchronized void add(ScreenEventKind kind, GobValue window) {
        queued.add(new ScreenEvent(kind, window));
    }

    public synchronized void add(ScreenEventKind kind, GobValue window, ScreenEventDetail detail) {
        queued.add(new ScreenEvent(kind, window, detail));
    }

    public synchronized Optional<ScreenEvent> takeTheOldest() {
        return Optional.ofNullable(queued.poll());
    }
}
